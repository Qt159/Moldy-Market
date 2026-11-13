// category/service/CategoryService.java
package com.moldy.moldymarket.category.service;


import com.moldy.moldymarket.category.dto.CategoryResponse;
import com.moldy.moldymarket.category.dto.CreateCategoryRequest;
import com.moldy.moldymarket.category.dto.UpdateCategoryRequest;
import com.moldy.moldymarket.category.entity.Category;
import com.moldy.moldymarket.category.entity.CategoryStatus;
import com.moldy.moldymarket.category.repository.CategoryRepository;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository repository;
    private final ProductRepository productRepository; // check trước khi xóa category

    // Public: cây danh mục đang ACTIVE — 1 query, build tree trong memory
    public List<CategoryResponse> getActiveTree() {
        List<Category> all = repository.findAllByStatusOrderByLevelAscNameAsc(CategoryStatus.ACTIVE);

        Map<UUID, List<Category>> childrenByParent = all.stream()
                .filter(Category::isSubCategory)
                .collect(Collectors.groupingBy(Category::getParentId));

        return all.stream()
                .filter(Category::isRoot)
                .map(root -> toTreeNode(root, childrenByParent.getOrDefault(root.getId(), List.of())))
                .toList();
    }

    public CategoryResponse getById(UUID id) {
        Category category = findActiveOrThrow(id);
        boolean hasChildren = category.isRoot() && repository.existsByParentId(id);
        return toResponse(category, hasChildren);
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        Category category;
        if (request.parentId() == null) {
            validateNameUnique(request.name(), null, null);
            category = new Category(request.name(), request.description());
        } else {
            Category parent = findByIdOrThrow(request.parentId());
            if (!parent.isRoot()) {
                throw new AppException(ErrorCode.CATEGORY_PARENT_NOT_ROOT);
            }
            validateNameUnique(request.name(), request.parentId(), null);
            category = new Category(request.name(), request.description(), parent);
        }

        return toResponse(repository.save(category), false);
    }

    @Transactional
    public CategoryResponse update(UUID id, UpdateCategoryRequest request) {
        Category category = findByIdOrThrow(id);
        validateNameUnique(request.name(), category.getParentId(), id);
        category.update(request.name(), request.description());

        boolean hasChildren = category.isRoot() && repository.existsByParentId(id);
        return toResponse(category, hasChildren);
    }

    @Transactional
    public void delete(UUID id) {
        Category category = findByIdOrThrow(id);

        if (category.isRoot() && repository.existsByParentId(id)) {
            throw new AppException(ErrorCode.CATEGORY_HAS_CHILDREN);
        }
        if (productRepository.existsByCategoryId(id)) {
            throw new AppException(ErrorCode.CATEGORY_HAS_LISTINGS);
        }

        repository.delete(category);
    }

    // Product module gọi hàm này khi tạo/sửa sản phẩm
    public void validateProductCategory(UUID categoryId) {
        Category category = findByIdOrThrow(categoryId);

        if (!category.isActive()) {
            throw new AppException(ErrorCode.CATEGORY_INACTIVE);
        }
        if (!category.isSubCategory()) {
            throw new AppException(ErrorCode.CATEGORY_NOT_LEAF);
        }
        if (!category.getParent().isActive()) {
            throw new AppException(ErrorCode.CATEGORY_INACTIVE);
        }
    }

    // --- Helpers -------------------------------------------------
    private Category findByIdOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private Category findActiveOrThrow(UUID id) {
        Category category = findByIdOrThrow(id);
        if (!category.isActive()) {
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return category;
    }

    private void validateNameUnique(String name, UUID parentId, UUID excludeId) {
        boolean duplicate = (parentId == null)
                ? (excludeId == null
                ? repository.existsByParentIsNullAndName(name)
                : repository.existsByParentIsNullAndNameAndIdNot(name, excludeId))
                : (excludeId == null
                ? repository.existsByParentIdAndName(parentId, name)
                : repository.existsByParentIdAndNameAndIdNot(parentId, name, excludeId));

        if (duplicate) {
            throw new AppException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
        }
    }

    private CategoryResponse toResponse(Category c, boolean hasChildren) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.getParentId(),
                c.getLevel(), c.getStatus(), hasChildren, null, c.getCreatedAt(), c.getUpdatedAt());
    }

    private CategoryResponse toTreeNode(Category root, List<Category> children) {
        List<CategoryResponse> childResponses = children.stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.getParentId(),
                        c.getLevel(), c.getStatus(), false, List.of(), null, null))
                .toList();

        return new CategoryResponse(root.getId(), root.getName(), root.getDescription(), root.getParentId(),
                root.getLevel(), root.getStatus(), !childResponses.isEmpty(), childResponses, null, null);
    }
}