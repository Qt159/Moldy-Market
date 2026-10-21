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
                category.getUpdatedAt());
    }

 
    public static List<CategoryResponse> toTree(List<Category> categories) {
        return categories.stream()
                .filter(Category::isRoot)
                .filter(Category::isActive)
                .map(category -> toTreeNode(category, categories))
                .toList();
    }

    private static CategoryResponse toTreeNode(Category category, 
                                        List<Category> allCategories) {
        List<CategoryResponse> children =  allCategories.stream()
                .filter(child -> child.getParentId() != null)
                .filter(child -> child.getParentId().equals(category.getId()))
                .filter(Category::isActive)
                .map(child -> toResponse(child))
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
                null);
    }
}