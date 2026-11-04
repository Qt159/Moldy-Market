package com.moldy.moldymarket.product.search;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.security.SecureRandom;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class ProductSearchRepository {

    /** Chỉ duyệt và đếm tối đa 10.000 kết quả đầu: đủ dùng và giữ truy vấn rẻ. */
    public static final int MAX_RESULT_WINDOW = 10_000;

    private final NamedParameterJdbcTemplate jdbc;

    /** Mốc giá (VNĐ) của các khoảng giá: dưới mốc đầu, giữa các mốc, và từ mốc cuối trở lên. */
    public static final long[] PRICE_BOUNDS = {500_000, 1_000_000, 3_000_000, 5_000_000, 10_000_000};

    /** Lấy id của một trang kết quả, đã sắp xếp. Chỉ lấy id, dữ liệu đầy đủ tải riêng cho 20 dòng của trang. */
    public List<UUID> findPageIds(ProductSearchCriteria criteria, int limit, long offset) {
        applyFuzzyThreshold();
        ProductSearchSql.Where where = ProductSearchSql.where(criteria, Set.of());
        MapSqlParameterSource params = where.params();
        String orderBy = ProductSearchSql.orderBy(criteria, params);
        params.addValue("limit", limit);
        params.addValue("offset", offset);

        String sql = "SELECT p.id FROM products p WHERE " + where.sql()
                + " ORDER BY " + orderBy + " LIMIT :limit OFFSET :offset";
        return jdbc.query(sql, params, (rs, i) -> rs.getObject("id", UUID.class));
    }

    /** Đếm có trần: trả tối đa MAX_RESULT_WINDOW + 1 để biết "có vượt trần hay không". */
    public long countCapped(ProductSearchCriteria criteria) {
        applyFuzzyThreshold();
        ProductSearchSql.Where where = ProductSearchSql.where(criteria, Set.of());
        where.params().addValue("cap", MAX_RESULT_WINDOW + 1);

        String sql = "SELECT COUNT(*) FROM (SELECT 1 FROM products p WHERE " + where.sql() + " LIMIT :cap) t";
        Long count = jdbc.queryForObject(sql, where.params(), Long.class);
        return count == null ? 0 : count;
    }

    // Mỗi facet bỏ qua bộ lọc của chính nó, để người dùng vẫn thấy các lựa chọn khác để đổi sang
    public Map<UUID, Long> countByCategory(ProductSearchCriteria criteria) {
        applyFuzzyThreshold();
        ProductSearchSql.Where where = ProductSearchSql.where(criteria, Set.of(ProductSearchSql.Facet.CATEGORY));
        String sql = "SELECT p.category_id AS id, COUNT(*) AS cnt FROM products p WHERE " + where.sql()
                + " GROUP BY p.category_id";
        Map<UUID, Long> result = new LinkedHashMap<>();
        jdbc.query(sql, where.params(), (RowCallbackHandler) rs ->
                result.put(rs.getObject("id", UUID.class), rs.getLong("cnt")));
        return result;
    }

    public Map<String, Long> countByCondition(ProductSearchCriteria criteria) {
        applyFuzzyThreshold();
        ProductSearchSql.Where where = ProductSearchSql.where(criteria, Set.of(ProductSearchSql.Facet.CONDITION));
        String sql = "SELECT p.condition AS value, COUNT(*) AS cnt FROM products p WHERE " + where.sql()
                + " GROUP BY p.condition";

        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.query(sql, where.params(), (RowCallbackHandler) rs ->
                result.put(rs.getString("value"), rs.getLong("cnt")));
        return result;
    }

    /** Một lần quét, đếm đồng thời mọi khoảng giá. Phần tử i tương ứng với khoảng thứ i. */
    public long[] countByPriceRange(ProductSearchCriteria criteria) {
        applyFuzzyThreshold();
        ProductSearchSql.Where where = ProductSearchSql.where(criteria, Set.of(ProductSearchSql.Facet.PRICE));

        int buckets = PRICE_BOUNDS.length + 1;
        StringBuilder select = new StringBuilder("SELECT ");
        for (int i = 0; i < buckets; i++) {
            if (i > 0) {
                select.append(", ");
            }
            select.append("COUNT(*) FILTER (WHERE ").append(priceCondition(i)).append(") AS b").append(i);
        }
        String sql = select + " FROM products p WHERE " + where.sql();

        return jdbc.queryForObject(sql, where.params(), (rs, rowNum) -> {
            long[] counts = new long[buckets];
            for (int i = 0; i < buckets; i++) {
                counts[i] = rs.getLong("b" + i);
            }
            return counts;
        });
    }

    // Ngưỡng "gần giống" cho toán tử <% của pg_trgm. SET LOCAL chỉ có hiệu lực trong transaction hiện tại.
    private void applyFuzzyThreshold() {
        jdbc.getJdbcTemplate().execute("SET LOCAL pg_trgm.word_similarity_threshold = 0.3");
    }

    // Các mốc là hằng số trong code (không phải dữ liệu người dùng) nên nối thẳng vào SQL là an toàn
    private static String priceCondition(int i) {
        if (i == 0) {
            return "p.price < " + PRICE_BOUNDS[0];
        }
        if (i == PRICE_BOUNDS.length) {
            return "p.price >= " + PRICE_BOUNDS[i - 1];
        }
        return "p.price >= " + PRICE_BOUNDS[i - 1] + " AND p.price < " + PRICE_BOUNDS[i];
    }

    /**
     * Gợi ý tiêu đề: không trùng tiêu đề, ưu tiên (1) bắt đầu bằng từ khóa, (2) có từ bắt đầu bằng từ khóa,
     * (3) chứa từ khóa; cùng mức thì tiêu đề nào có nhiều sản phẩm hơn xếp trước.
     */
    public List<String> suggestTitles(String normalizedQuery, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("contains", "%" + normalizedQuery + "%")
                .addValue("prefix", normalizedQuery + "%")
                .addValue("wordStart", "% " + normalizedQuery + "%")
                .addValue("limit", limit);

        String sql = "SELECT MIN(p.title) AS title FROM products p WHERE " + ProductSearchSql.VISIBILITY
                + " AND p.title_search LIKE :contains"
                + " GROUP BY p.title_search"
                + " ORDER BY CASE WHEN p.title_search LIKE :prefix THEN 0"
                + "               WHEN p.title_search LIKE :wordStart THEN 1 ELSE 2 END,"
                + "          COUNT(*) DESC, p.title_search"
                + " LIMIT :limit";

        return jdbc.query(sql, params, (rs, i) -> rs.getString("title"));
    }
}
