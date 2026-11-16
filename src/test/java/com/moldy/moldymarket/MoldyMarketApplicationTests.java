package com.moldy.moldymarket;

import com.moldy.moldymarket.category.entity.Category;
import com.moldy.moldymarket.category.entity.CategoryStatus;
import com.moldy.moldymarket.category.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.cors.allowed-origins=https://client.example.test")
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
    void loginPreflightAllowsTheConfiguredClientAndCredentials() throws Exception {
        mvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://client.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Moldy-Auth, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://client.example.test"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void loginPreflightRejectsAnUnconfiguredOrigin() throws Exception {
        mvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Moldy-Auth"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownStoreRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/stores/my-store"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void bothCategoryRoutesExposeTheTreeAndDetailWithoutAuthentication() throws Exception {
        var root = categories.findAllByStatusOrderByLevelAscNameAsc(CategoryStatus.ACTIVE).stream()
                .filter(Category::isRoot)
                .findFirst()
                .orElseThrow();
        for (String path : new String[]{"/api/v1/categories", "/api/categories"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].id").isString())
                    .andExpect(jsonPath("$.data[0].children").isArray());
            mvc.perform(get(path + "/" + root.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(root.getId().toString()))
                    .andExpect(jsonPath("$.data.hasChildren").value(true));
        }
    }
}
