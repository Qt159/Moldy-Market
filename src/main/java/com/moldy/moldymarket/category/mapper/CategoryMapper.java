package com.moldy.moldymarket.category.mapper;

import com.moldy.moldymarket.category.dto.CategoryResponse;
import com.moldy.moldymarket.category.entity.Category;

import java.util.List;

public final class CategoryMapper {

    private CategoryMapper() {}

    public static CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getParentId(),
                category.getLevel(),
                category.getStatus(),
                category.hasChildren(),
                null,
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }

 
    public static List<CategoryResponse> toTree(List<Category> categories) {
        return categories.stream()
                .filter(Category::isRoot)
                .map(CategoryMapper::toTreeNode)
                .toList();
    }

    private static CategoryResponse toTreeNode(Category category) {
        List<CategoryResponse> children = category.getChildren()
                .stream()
                .map(CategoryMapper::toResponse)
                .toList();

        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getParentId(),
                category.getLevel(),
                category.getStatus(),
                !children.isEmpty(),
                children,
                null,
                null
        );
    }
}