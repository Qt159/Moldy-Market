package com.moldy.moldymarket.product.service;

import com.moldy.moldymarket.category.entity.Category;
import com.moldy.moldymarket.category.repository.CategoryRepository;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.product.dto.ProductFacets;
import com.moldy.moldymarket.product.dto.ProductResponse;
import com.moldy.moldymarket.product.dto.ProductSearchRequest;
import com.moldy.moldymarket.product.dto.ProductSearchResponse;
import com.moldy.moldymarket.product.entity.Product;
import com.moldy.moldymarket.product.entity.ProductCondition;
import com.moldy.moldymarket.product.mapper.ProductMapper;
import com.moldy.moldymarket.product.repository.ProductRepository;
import com.moldy.moldymarket.product.search.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.SqlReturnType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // bắt buộc: SET LOCAL ở repository chỉ có tác dụng trong transaction
public class ProductSearchService {

    private static final int MAX_TOKENS = 8;
    private static final int MAX_ATTR_VALUE_LENGTH = 50;
    private static final Pattern ATTR_KEY = Pattern.compile("[A-Za-z0-9_]{1,30}");
    private static final int MIN_SUGGEST_LENGTH = 3;
    private static final int MAX_SUGGEST_LENGTH = 50;
    private static final int MAX_SUGGESTIONS = 8;

    private final ProductSearchRepository searchRepository;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final CategoryRepository categoryRepository;

    public ProductSearchResponse search(ProductSearchRequest request, Pageable pageable) {
        ProductSearchCriteria criteria = toCriteria(request);

        long offset = pageable.getOffset();
        int size = pageable.getPageSize();
        if (offset + size > ProductSearchRepository.MAX_RESULT_WINDOW) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Only the first " + ProductSearchRepository.MAX_RESULT_WINDOW
                            + " results can be browsed, please refine your search");
        }

        List<UUID> ids = searchRepository.findPageIds(criteria, size, offset);

        long total;
        boolean capped = false;
        if (ids.size() < size && (offset == 0 || !ids.isEmpty())) {
            total = offset + ids.size(); // đã tới cuối danh sách, khỏi đếm
        } else {
            long counted = searchRepository.countCapped(criteria);
            if (counted > ProductSearchRepository.MAX_RESULT_WINDOW) {
                total = ProductSearchRepository.MAX_RESULT_WINDOW;
                capped = true;
            } else {
                total = counted;
            }
        }

        // Tải đầy đủ dữ liệu của đúng các sản phẩm trong trang, giữ nguyên thứ tự đã sắp xếp
        Map<UUID, Product> byId = productRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<ProductResponse> items = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(productMapper::toPublicResponse)
                .toList();

        int totalPages = (int) Math.ceil((double) total / size);
        ProductFacets facets = Boolean.TRUE.equals(request.facets()) ? buildFacets(criteria) : null;
        return new ProductSearchResponse(items, pageable.getPageNumber() + 1, size, total, totalPages, capped, facets);
    }

    public List<String> suggest(String q) {
        String normalized = SearchTextNormalizer.normalize(q);
        if (normalized.length() < MIN_SUGGEST_LENGTH) {
            return List.of();
        }
        if (normalized.length() > MAX_SUGGEST_LENGTH) {
            normalized = normalized.substring(0, MAX_SUGGEST_LENGTH).trim();
        }
        return searchRepository.suggestTitles(normalized, MAX_SUGGESTIONS);
    }

    // ---- Kiểm tra và chuẩn hóa đầu vào ----

    private ProductSearchCriteria toCriteria(ProductSearchRequest r) {
        if (r.minPrice() != null && r.maxPrice() != null && r.minPrice().compareTo(r.maxPrice()) > 0) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "minPrice must not be greater than maxPrice");
        }

        List<String> tokens = tokenize(r.q());

        ProductSortOption sort = ProductSortOption.fromParam(r.sort());
        if (sort == null || (sort == ProductSortOption.RELEVANCE && tokens.isEmpty())) {
            sort = tokens.isEmpty() ? ProductSortOption.NEWEST : ProductSortOption.RELEVANCE;
        }

        List<String> conditions = orEmpty(r.condition()).stream().map(ProductCondition::name).distinct().toList();

        return new ProductSearchCriteria(
                String.join(" ", tokens),
                tokens,
                orEmpty(r.categoryId()).stream().distinct().toList(),
                conditions,
                r.minPrice(),
                r.maxPrice(),
                r.storeId(),
                r.sellerType(),
                parseAttributes(r.attr()),
                sort);
    }

    private List<String> tokenize(String q) {
        String normalized = SearchTextNormalizer.normalize(q);
        if (normalized.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(normalized.split(" ")).distinct().limit(MAX_TOKENS).toList();
    }

    // "brand:Apple" -> {brand: [Apple]}
    private Map<String, List<String>> parseAttributes(List<String> raw) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String item : orEmpty(raw)) {
            int colon = item == null ? -1 : item.indexOf(':');
            if (colon <= 0) {
                throw badAttr();
            }
            String key = item.substring(0, colon).trim();
            String value = item.substring(colon + 1).trim();
            if (!ATTR_KEY.matcher(key).matches() || value.isEmpty() || value.length() > MAX_ATTR_VALUE_LENGTH) {
                throw badAttr();
            }
            List<String> values = result.computeIfAbsent(key, k -> new ArrayList<>());
            if (!values.contains(value)) {
                values.add(value);
            }
        }
        return result;
    }

    private AppException badAttr() {
        return new AppException(ErrorCode.INVALID_REQUEST, "attr must look like key:value");
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    // ---- Facets ----
    private ProductFacets buildFacets(ProductSearchCriteria criteria) {
        List<ProductFacets.CategoryFacet> categories =
                buildCategoryFacets(searchRepository.countByCategory(criteria));

        List<ProductFacets.ConditionFacet> conditions = searchRepository.countByCondition(criteria).entrySet().stream()
                .map(e -> new ProductFacets.ConditionFacet(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(ProductFacets.ConditionFacet::count).reversed())
                .toList();

        return new ProductFacets(categories, conditions,
                buildPriceFacets(searchRepository.countByPriceRange(criteria)));
    }

    // Gom số đếm của các sub về root: root (tổng) -> các sub
    private List<ProductFacets.CategoryFacet> buildCategoryFacets(Map<UUID, Long> countByCategory) {
        if (countByCategory.isEmpty()) {
            return List.of();
        }

        List<Category> subs = categoryRepository.findAllById(countByCategory.keySet()).stream()
                .filter(Category::isSubCategory)
                .toList();
        Set<UUID> rootIds = subs.stream().map(Category::getParentId).collect(Collectors.toSet());
        Map<UUID, Category> roots = categoryRepository.findAllById(rootIds).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        Map<UUID, List<Category>> subsByRoot = subs.stream()
                .collect(Collectors.groupingBy(Category::getParentId));

        Comparator<ProductFacets.CategoryFacet> byCountDesc =
                Comparator.comparingLong(ProductFacets.CategoryFacet::count).reversed();

        List<ProductFacets.CategoryFacet> result = new ArrayList<>();
        for (Map.Entry<UUID, List<Category>> entry : subsByRoot.entrySet()) {
            Category root = roots.get(entry.getKey());
            if (root == null) {
                continue;
            }
            List<ProductFacets.CategoryFacet> children = entry.getValue().stream()
                    .map(sub -> new ProductFacets.CategoryFacet(
                            sub.getId(), sub.getName(), countByCategory.get(sub.getId()), List.of()))
                    .sorted(byCountDesc)
                    .toList();
            long total = children.stream().mapToLong(ProductFacets.CategoryFacet::count).sum();
            result.add(new ProductFacets.CategoryFacet(root.getId(), root.getName(), total, children));
        }
        result.sort(byCountDesc);
        return result;
    }

    private List<ProductFacets.PriceRangeFacet> buildPriceFacets(long[] counts) {
        long[] bounds = ProductSearchRepository.PRICE_BOUNDS;
        List<ProductFacets.PriceRangeFacet> result = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            BigDecimal min = i == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(bounds[i - 1]);
            BigDecimal max = i == bounds.length ? null : BigDecimal.valueOf(bounds[i]);
            result.add(new ProductFacets.PriceRangeFacet(min, max, counts[i]));
        }
        return result;
    }
}