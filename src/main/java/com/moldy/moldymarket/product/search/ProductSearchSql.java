package com.moldy.moldymarket.product.search;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ProductSearchSql {

    /** Nhóm facet có thể bỏ qua bộ lọc của chính nó (dùng ở giai đoạn 3). */
    public enum Facet { CATEGORY, CONDITION, PRICE }

    public record Where(String sql, MapSqlParameterSource params) {}

    private static final int FUZZY_MIN_LENGTH = 4;
    private static final Pattern NUMBER = Pattern.compile("-?\\d{1,15}(\\.\\d{1,6})?");

    // Cùng quy tắc hiển thị công khai với ProductSpecifications.sellerAndStoreVisible()
     static final String VISIBILITY = """
            p.status = 'ACTIVE'
            AND (p.store_id IS NULL OR NOT EXISTS (
                    SELECT 1 FROM stores s WHERE s.id = p.store_id AND s.status = 'BLOCKED'))
            AND (p.store_id IS NOT NULL OR EXISTS (
                    SELECT 1 FROM users u WHERE u.id = p.seller_id AND u.status = 'ACTIVE'))
            """;

    private ProductSearchSql() {}

    public static Where where(ProductSearchCriteria c, Set<Facet> skip) {
        StringBuilder sql = new StringBuilder(VISIBILITY);
        MapSqlParameterSource params = new MapSqlParameterSource();

        // Từ khóa: mọi từ đều phải khớp (chuỗi con, hoặc gần giống với từ từ 4 ký tự)
        List<String> tokens = c.tokens();
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            params.addValue("tl" + i, "%" + token + "%"); // token chỉ có a-z, 0-9 nên không có % hay _
            if (token.length() >= FUZZY_MIN_LENGTH) {
                params.addValue("tf" + i, token);
                sql.append(" AND (p.search_text LIKE :tl").append(i)
                        .append(" OR CAST(:tf").append(i).append(" AS text) <% p.search_text)");
            } else {
                sql.append(" AND p.search_text LIKE :tl").append(i);
            }
        }

        // Category: chọn root thì gồm cả các sub của nó
        if (!skip.contains(Facet.CATEGORY) && !c.categoryIds().isEmpty()) {
            params.addValue("cats", c.categoryIds());
            sql.append(" AND p.category_id IN (SELECT cat.id FROM categories cat"
                    + " WHERE cat.id IN (:cats) OR cat.parent_id IN (:cats))");
        }

        if (!skip.contains(Facet.CONDITION) && !c.conditions().isEmpty()) {
            params.addValue("conds", c.conditions());
            sql.append(" AND p.condition IN (:conds)");
        }

        if (!skip.contains(Facet.PRICE)) {
            if (c.minPrice() != null) {
                params.addValue("minPrice", c.minPrice());
                sql.append(" AND p.price >= :minPrice");
            }
            if (c.maxPrice() != null) {
                params.addValue("maxPrice", c.maxPrice());
                sql.append(" AND p.price <= :maxPrice");
            }
        }

        if (c.storeId() != null) {
            params.addValue("storeId", c.storeId());
            sql.append(" AND p.store_id = :storeId");
        }

        if (c.sellerType() == SellerType.STORE) {
            sql.append(" AND p.store_id IS NOT NULL");
        } else if (c.sellerType() == SellerType.PERSONAL) {
            sql.append(" AND p.store_id IS NULL");
        }

        appendAttributes(sql, params, c.attributes());

        return new Where(sql.toString(), params);
    }

    // Thuộc tính: khác khóa thì AND, cùng khóa nhiều giá trị thì OR.
    // jsonb_build_object dựng JSON ngay trong SQL từ tham số, không phải tự escape chuỗi JSON.
    private static void appendAttributes(StringBuilder sql, MapSqlParameterSource params,
                                         Map<String, List<String>> attributes) {
        int i = 0;
        for (Map.Entry<String, List<String>> entry : attributes.entrySet()) {
            String keyParam = "ak" + i;
            params.addValue(keyParam, entry.getKey());

            List<String> alternatives = new ArrayList<>();
            int j = 0;
            for (String value : entry.getValue()) {
                String base = "av" + i + "_" + j;

                params.addValue(base, value);
                alternatives.add("p.attributes @> jsonb_build_object(CAST(:" + keyParam + " AS text), CAST(:"
                        + base + " AS text))");

                // Giá trị trông như số hoặc boolean thì thử thêm kiểu đó, vì jsonb phân biệt "8" và 8
                if (NUMBER.matcher(value).matches()) {
                    params.addValue(base + "n", new BigDecimal(value));
                    alternatives.add("p.attributes @> jsonb_build_object(CAST(:" + keyParam + " AS text), CAST(:"
                            + base + "n AS numeric))");
                }
                if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                    params.addValue(base + "b", Boolean.parseBoolean(value));
                    alternatives.add("p.attributes @> jsonb_build_object(CAST(:" + keyParam + " AS text), CAST(:"
                            + base + "b AS boolean))");
                }
                j++;
            }
            sql.append(" AND (").append(String.join(" OR ", alternatives)).append(")");
            i++;
        }
    }

    public static String orderBy(ProductSearchCriteria c, MapSqlParameterSource params) {
        return switch (c.sort()) {
            case RELEVANCE -> {
                params.addValue("qn", c.normalizedQuery());
                params.addValue("qp", c.normalizedQuery() + "%");
                yield "(2 * word_similarity(CAST(:qn AS text), p.title_search)"
                        + " + word_similarity(CAST(:qn AS text), p.search_text)"
                        + " + CASE WHEN p.title_search LIKE :qp THEN 1 ELSE 0 END) DESC,"
                        + " p.created_at DESC, p.id";
            }
            case NEWEST -> "p.created_at DESC, p.id";
            case PRICE_ASC -> "p.price ASC, p.created_at DESC, p.id";
            case PRICE_DESC -> "p.price DESC, p.created_at DESC, p.id";
        };
    }
}