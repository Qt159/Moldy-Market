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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryService categoryService;

    private Category rootCategory;
    private Category subCategory;
    private UUID rootId;
    private UUID subId;

    @BeforeEach
    void setUp() {
        rootId = UUID.randomUUID();
        subId = UUID.randomUUID();

        rootCategory = new Category("Electronics", "All electronic gadgets");
        ReflectionTestUtils.setField(rootCategory, "id", rootId);

        subCategory = new Category("Laptops", "Portable computers", rootCategory);
        ReflectionTestUtils.setField(subCategory, "id", subId);
    }

    @Test
    @DisplayName("should return active tree structured with roots and subcategories")
    void should_returnActiveTree_when_categoriesExist() {
        // Arrange
        when(categoryRepository.findAllByStatusOrderByLevelAscNameAsc(CategoryStatus.ACTIVE))
                .thenReturn(List.of(rootCategory, subCategory));

        // Act
        List<CategoryResponse> tree = categoryService.getActiveTree();

        // Assert
        assertThat(tree).hasSize(1);
        CategoryResponse rootNode = tree.get(0);
        assertThat(rootNode.id()).isEqualTo(rootId);
        assertThat(rootNode.name()).isEqualTo("Electronics");
        assertThat(rootNode.hasChildren()).isTrue();
        assertThat(rootNode.children()).hasSize(1);
        assertThat(rootNode.children().get(0).id()).isEqualTo(subId);
        assertThat(rootNode.children().get(0).name()).isEqualTo("Laptops");
    }

    @Test
    @DisplayName("should return category response when active category exists by id")
    void should_returnCategoryResponse_when_activeCategoryExists() {
        // Arrange
        when(categoryRepository.findById(rootId)).thenReturn(Optional.of(rootCategory));
        when(categoryRepository.existsByParent_Id(rootId)).thenReturn(true);

        // Act
        CategoryResponse response = categoryService.getById(rootId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(rootId);
        assertThat(response.hasChildren()).isTrue();
    }

    @Test
    @DisplayName("should throw CATEGORY_NOT_FOUND when category is inactive or missing")
    void should_throwCategoryNotFound_when_categoryNotFoundOrInactive() {
        // Arrange
        UUID missingId = UUID.randomUUID();
        when(categoryRepository.findById(missingId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> categoryService.getById(missingId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("should create root category when parentId is null and name is unique")
    void should_createRootCategory_when_parentIdIsNull() {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest("Vehicles", "Cars and bikes", null);
        when(categoryRepository.existsByParentIsNullAndName("Vehicles")).thenReturn(false);

        Category saved = new Category("Vehicles", "Cars and bikes");
        ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
        when(categoryRepository.save(any(Category.class))).thenReturn(saved);

        // Act
        CategoryResponse response = categoryService.create(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Vehicles");
        assertThat(response.parentId()).isNull();
        assertThat(response.level()).isEqualTo(Category.ROOT_LEVEL);
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("should create sub-category when parent is root and name is unique")
    void should_createSubCategory_when_parentIsRoot() {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest("Smartphones", "Phones", rootId);
        when(categoryRepository.findById(rootId)).thenReturn(Optional.of(rootCategory));
        when(categoryRepository.existsByParent_IdAndName(rootId, "Smartphones")).thenReturn(false);

        Category saved = new Category("Smartphones", "Phones", rootCategory);
        ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
        when(categoryRepository.save(any(Category.class))).thenReturn(saved);

        // Act
        CategoryResponse response = categoryService.create(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Smartphones");
        assertThat(response.parentId()).isEqualTo(rootId);
        assertThat(response.level()).isEqualTo(Category.SUB_LEVEL);
    }

    @Test
    @DisplayName("should throw CATEGORY_PARENT_NOT_ROOT when trying to create subcategory under a subcategory")
    void should_throwCategoryParentNotRoot_when_parentIsNotRoot() {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest("Gaming Laptops", "Sub sub", subId);
        when(categoryRepository.findById(subId)).thenReturn(Optional.of(subCategory));

        // Act & Assert
        assertThatThrownBy(() -> categoryService.create(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_PARENT_NOT_ROOT);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw CATEGORY_NAME_ALREADY_EXISTS when creating duplicate name under same parent")
    void should_throwCategoryNameAlreadyExists_when_nameDuplicate() {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest("Laptops", "Duplicate", rootId);
        when(categoryRepository.findById(rootId)).thenReturn(Optional.of(rootCategory));
        when(categoryRepository.existsByParent_IdAndName(rootId, "Laptops")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> categoryService.create(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("should update category successfully when name is unique")
    void should_updateCategory_when_requestIsValid() {
        // Arrange
        UpdateCategoryRequest request = new UpdateCategoryRequest("Electronics & Gadgets", "Updated description");
        when(categoryRepository.findById(rootId)).thenReturn(Optional.of(rootCategory));
        when(categoryRepository.existsByParentIsNullAndNameAndIdNot("Electronics & Gadgets", rootId)).thenReturn(false);
        when(categoryRepository.existsByParent_Id(rootId)).thenReturn(true);

        // Act
        CategoryResponse response = categoryService.update(rootId, request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(rootCategory.getName()).isEqualTo("Electronics & Gadgets");
        assertThat(rootCategory.getDescription()).isEqualTo("Updated description");
    }

    @Test
    @DisplayName("should delete leaf category when it has no listings")
    void should_deleteCategory_when_leafAndNoListings() {
        // Arrange
        when(categoryRepository.findById(subId)).thenReturn(Optional.of(subCategory));
        when(productRepository.existsByCategoryId(subId)).thenReturn(false);

        // Act
        categoryService.delete(subId);

        // Assert
        verify(categoryRepository).delete(subCategory);
    }

    @Test
    @DisplayName("should throw CATEGORY_HAS_CHILDREN when deleting root category that has subcategories")
    void should_throwCategoryHasChildren_when_deletingParentCategory() {
        // Arrange
        when(categoryRepository.findById(rootId)).thenReturn(Optional.of(rootCategory));
        when(categoryRepository.existsByParent_Id(rootId)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> categoryService.delete(rootId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_HAS_CHILDREN);

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("should throw CATEGORY_HAS_LISTINGS when deleting category that has active products")
    void should_throwCategoryHasListings_when_productsExist() {
        // Arrange
        when(categoryRepository.findById(subId)).thenReturn(Optional.of(subCategory));
        when(productRepository.existsByCategoryId(subId)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> categoryService.delete(subId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_HAS_LISTINGS);

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("should validate product category successfully when it is active leaf")
    void should_validateProductCategory_when_validActiveLeaf() {
        // Arrange
        when(categoryRepository.findById(subId)).thenReturn(Optional.of(subCategory));

        // Act & Assert (no exception)
        categoryService.validateProductCategory(subId);
    }

    @Test
    @DisplayName("should throw CATEGORY_NOT_LEAF when product is assigned to root category")
    void should_throwCategoryNotLeaf_when_productCategoryIsRoot() {
        // Arrange
        when(categoryRepository.findById(rootId)).thenReturn(Optional.of(rootCategory));

        // Act & Assert
        assertThatThrownBy(() -> categoryService.validateProductCategory(rootId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_NOT_LEAF);
    }
}
