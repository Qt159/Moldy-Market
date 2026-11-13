package com.moldy.moldymarket.category.service;

import com.moldy.moldymarket.category.dto.CategoryResponse;
import com.moldy.moldymarket.category.dto.CreateCategoryRequest;
import com.moldy.moldymarket.category.dto.UpdateCategoryRequest;
import com.moldy.moldymarket.category.entity.Category;
import com.moldy.moldymarket.category.entity.CategoryStatus;
import com.moldy.moldymarket.category.mapper.CategoryMapper;
import com.moldy.moldymarket.category.repository.CategoryRepository;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository repository;

    // Public
    public List<CategoryResponse> getActiveTree() {
        return CategoryMapper.toTree(
                repository.findAllByStatusOrderByLevelAscNameAsc(
                    CategoryStatus.ACTIVE));
    }

    public CategoryResponse getById(UUID id) {
        return CategoryMapper.toResponse(findActiveOrThrow(id));
    }

    
    // Admin
    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        Category category;
        if (request.parentId() == null) {
            validateNameUnique(request.name(), null, null);

            category = Category.createRoot(request.name(), request.description());
        } else {
            Category parent = findByIdOrThrow(request.parentId());

            if (!parent.isRoot()) {
                throw new AppException(ErrorCode.CATEGORY_MAX_DEPTH);
            }

            validateNameUnique(request.name(), request.parentId(), null);

            category = Category.createSubCategory(
                    request.name(), request.description(), parent);
        }

        return CategoryMapper.toResponse(repository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID id, UpdateCategoryRequest request) {
        Category category = findByIdOrThrow(id);

        validateNameUnique(request.name(), category.getParentId(), id);
        category.update(request.name(), request.description());
        return CategoryMapper.toResponse(category);
    }

    @Transactional
    public void delete(UUID id) {

        Category category = findByIdOrThrow(id);

        if (category.isRoot()&& repository.existsByParentId(id)) {
            throw new AppException(ErrorCode.CATEGORY_HAS_CHILDREN);
        }

        // TODO: check thêm listings, products
        repository.delete(category);
    }


    public void validateProductCategory(UUID categoryId) {
        Category category = findByIdOrThrow(categoryId);

        if (!category.isActive()) {
            throw new AppException(ErrorCode.CATEGORY_INACTIVE);
        }
        if (!category.isSubCategory()) {
            throw new AppException(ErrorCode.CATEGORY_NOT_LEAF);
        }
    }



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
        boolean duplicate;

        if (parentId == null) {
            if (excludeId == null) {
                duplicate = repository.existsByParentIsNullAndName(name);
            } else {
                duplicate = repository.existsByParentIsNullAndNameAndIdNot(
                                        name, excludeId);
            }
        } else {
            if (excludeId == null) {
                duplicate = repository.existsByParentIdAndName(
                                    parentId, name);
            } else {
                duplicate = repository.existsByParentIdAndNameAndIdNot(
                                    parentId, name, excludeId);
            }
        }

        if (duplicate) {
            throw new AppException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
        }
    }
}