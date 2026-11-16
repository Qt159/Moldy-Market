package com.moldy.moldymarket.category.controller;

import com.moldy.moldymarket.category.dto.CategoryResponse;
import com.moldy.moldymarket.category.dto.CreateCategoryRequest;
import com.moldy.moldymarket.category.dto.UpdateCategoryRequest;
import com.moldy.moldymarket.category.service.CategoryService;
import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.security.PermissionConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getTree() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getActiveTree()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getById(id)));
    }

    // Admin (qua permission CATEGORY_MANAGE, không hardcode role)
    @PreAuthorize("hasAuthority('" + PermissionConstants.CATEGORY_MANAGE + "')")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully", categoryService.create(request)));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.CATEGORY_MANAGE + "')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(@PathVariable UUID id,
                                                                @Valid @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", categoryService.update(id, request)));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.CATEGORY_MANAGE + "')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
