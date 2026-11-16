package com.moldy.moldymarket;

import com.moldy.moldymarket.category.entity.Category;
import com.moldy.moldymarket.category.entity.CategoryStatus;
import com.moldy.moldymarket.category.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MoldyMarketApplicationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CategoryRepository categories;

    @Test
    void contextLoads() {
    }

    @Test
    void frontendCanLoadTheCategoryTreeAndDetailAndFilterProducts() throws Exception {
        Category root = categories.findAllByStatusOrderByLevelAscNameAsc(CategoryStatus.ACTIVE).stream()
                .filter(Category::isRoot)
                .findFirst()
                .orElseThrow();
        String categoryId = root.getId().toString();

        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].children").isArray());

        mvc.perform(get("/api/v1/categories/{id}", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(categoryId))
                .andExpect(jsonPath("$.data.hasChildren").value(true));

        mvc.perform(get("/api/v1/products/search")
                        .param("categoryId", categoryId)
                        .param("page", "1")
                        .param("size", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray());
    }
}
