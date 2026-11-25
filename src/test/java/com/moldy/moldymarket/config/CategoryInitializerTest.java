package com.moldy.moldymarket.config;

import com.moldy.moldymarket.category.entity.Category;
import com.moldy.moldymarket.category.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryInitializerTest {

    @Mock
    CategoryRepository categoryRepository;

    @InjectMocks
    CategoryInitializer initializer;

    @Test
    void seedsAllRootAndSubCategoriesIdempotentlyWithoutDuplicatingExistingOnes() {
        List<Category> store = new ArrayList<>();

        // Pre-seed one existing root + one existing sub to simulate an existing DB
        Category existingElectronics = new Category("Đồ điện tử", null);
        UUID existingRootId = UUID.randomUUID();
        ReflectionTestUtils.setField(existingElectronics, "id", existingRootId);
        store.add(existingElectronics);

        Category existingPhone = new Category("Điện thoại", null, existingElectronics);
        UUID existingSubId = UUID.randomUUID();
        ReflectionTestUtils.setField(existingPhone, "id", existingSubId);
        store.add(existingPhone);

        when(categoryRepository.findAll()).thenAnswer(invocation -> new ArrayList<>(store));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category cat = invocation.getArgument(0);
            if (cat.getId() == null) {
                ReflectionTestUtils.setField(cat, "id", UUID.randomUUID());
                store.add(cat);
            }
            return cat;
        });

        // First run: fills missing roots & subs while preserving existing UUIDs
        initializer.run();

        long rootCount = store.stream().filter(Category::isRoot).count();
        long subCount = store.stream().filter(Category::isSubCategory).count();
        assertThat(rootCount).isEqualTo(12);
        assertThat(subCount).isEqualTo(68);
        assertThat(existingElectronics.getId()).isEqualTo(existingRootId);
        assertThat(existingElectronics.getDescription()).isNotBlank();
        assertThat(store.stream().filter(c -> c.getName().equals("Điện thoại")).count()).isEqualTo(1);

        int sizeAfterFirstRun = store.size();

        // Second run: completely idempotent, adds zero rows
        initializer.run();
        assertThat(store).hasSize(sizeAfterFirstRun);
    }
}
