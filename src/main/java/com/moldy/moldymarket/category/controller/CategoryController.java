package com.moldy.moldymarket.category.controller;

import com.moldy.moldymarket.category.dto.CategoryResponse;
import com.moldy.moldymarket.category.dto.CreateCategoryRequest;
import com.moldy.moldymarket.category.dto.UpdateCategoryRequest;
import com.moldy.moldymarket.category.service.CategoryService;
import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.security.annotation.IsAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Danh mục (Categories)", description = "Quản lý danh mục sản phẩm ")
@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService service;

    @Operation(summary = "Xem toàn bộ danh mục", description = "Lấy toàn bộ danh mục sản phẩm đang hoạt động")
    @GetMapping("/api/categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories() {
        return ResponseEntity.ok(
                ApiResponse.success(service.getActiveTree()));
    }

    @Operation(summary = "Xem chi tiết danh mục", description = "Lấy thông tin chi tiết một danh mục theo UUID")
    @GetMapping("/api/categories/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(service.getById(id)));
    }

    @Operation(summary = "Tạo danh mục mới", description = "Admin tạo danh mục sản phẩm mới")
    @IsAdmin
    @PostMapping("/api/admin/categories")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Category created successfully",
                        service.create(request)));
    }

    @Operation(summary = "Cập nhật danh mục", description = "Admin cập nhật thông tin danh mục sản phẩm")
    @IsAdmin
    @PutMapping("/api/admin/categories/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Category updated successfully",
                service.update(id, request)));
    }

    @Operation(summary = "Xóa danh mục", description = "Admin xóa danh mục sản phẩm theo ID")
    @IsAdmin
    @DeleteMapping("/api/admin/categories/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id) {
        service.delete(id);

        return ResponseEntity.noContent().build();
    }
}