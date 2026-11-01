package com.moldy.moldymarket.config;

import com.moldy.moldymarket.product.search.SearchTextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Order(4)
@RequiredArgsConstructor
public class SearchIndexInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    private static final List<String> STATEMENTS = List.of(
            "CREATE EXTENSION IF NOT EXISTS pg_trgm",

            // Tìm theo chữ (không dấu, sai chính tả)
            "CREATE INDEX IF NOT EXISTS idx_products_title_trgm ON products USING gin (title_search gin_trgm_ops)",
            "CREATE INDEX IF NOT EXISTS idx_products_search_trgm ON products USING gin (search_text gin_trgm_ops)",

            // Lọc theo thuộc tính (jsonb)
            "CREATE INDEX IF NOT EXISTS idx_products_attributes ON products USING gin (attributes jsonb_path_ops)",

            // Các bộ lọc thường dùng
            "CREATE INDEX IF NOT EXISTS idx_products_category ON products (category_id)",
            "CREATE INDEX IF NOT EXISTS idx_products_store ON products (store_id)",
            "CREATE INDEX IF NOT EXISTS idx_products_status_created ON products (status, created_at DESC)",
            "CREATE INDEX IF NOT EXISTS idx_products_price ON products (price)"
            );

    @Override
    public void run(ApplicationArguments args) {
        for (String sql : STATEMENTS) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception e) {
                log.warn("Search setup step failed: {} -> {}", sql, e.getMessage());
            }
        }
        backfill();
    }

    // Điền title_search/search_text cho các bài đã có từ trước khi thêm tính năng này
    private void backfill() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, title, description FROM products WHERE title_search = ''");
        if (rows.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                "UPDATE products SET title_search = ?, search_text = ? WHERE id = ?",
                rows, 500,
                (ps, row) -> {
                    String title = SearchTextNormalizer.normalize((String) row.get("title"));
                    String description = SearchTextNormalizer.normalize((String) row.get("description"));
                    ps.setString(1, title);
                    ps.setString(2, title + " " + description);
                    ps.setObject(3, row.get("id"));
                });
        log.info("Search backfill completed for {} products", rows.size());
    }

}
