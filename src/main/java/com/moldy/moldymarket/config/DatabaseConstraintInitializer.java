package com.moldy.moldymarket.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class DatabaseConstraintInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    private static final List<String> STATEMENTS = List.of(
            // Root category: tên duy nhất (không phân biệt hoa thường)
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_category_root_name "
                    + "ON categories (lower(name)) WHERE parent_id IS NULL",

            // Sub-category: tên duy nhất trong cùng một root
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_category_sub_name "
                    + "ON categories (parent_id, lower(name)) WHERE parent_id IS NOT NULL"
    );

    @Override
    public void run(ApplicationArguments args) {
        for (String sql : STATEMENTS) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception e) {
                log.warn("Could not apply DB constraint, check existing data: {}", e.getMessage());
            }
        }
    }
}
