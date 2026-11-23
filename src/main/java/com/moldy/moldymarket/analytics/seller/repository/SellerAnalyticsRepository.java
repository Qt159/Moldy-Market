
package com.moldy.moldymarket.analytics.seller.repository;

import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface SellerAnalyticsRepository
        extends JpaRepository<Order, UUID> {

    interface RevenueProjection {
        LocalDate getRevenueDate();
        BigDecimal getRevenue();
        Long getOrderCount();
    }

    interface TopProductProjection {
        UUID getProductId();
        String getProductName();
        Long getTotalSold();
        BigDecimal getTotalRevenue();
    }

    // 1. Total revenue from completed orders.
    @Query(value = """
            SELECT COALESCE(
                SUM(o.total_amount - o.shipping_fee), 0
            )
            FROM orders o
            WHERE o.seller_id = :sellerId
              AND o.order_status = 4
            """, nativeQuery = true)
    BigDecimal getTotalRevenue(
            @Param("sellerId") UUID sellerId
    );

    // 2. Count completed orders.
    @Query(value = """
            SELECT COUNT(*)
            FROM orders o
            WHERE o.seller_id = :sellerId
              AND o.order_status = 4
            """, nativeQuery = true)
    Long countCompletedOrders(
            @Param("sellerId") UUID sellerId
    );

    // 3. Count active products.
    @Query(value = """
            SELECT COUNT(*)
            FROM products p
            WHERE p.seller_id = :sellerId
              AND p.status = 'ACTIVE'
            """, nativeQuery = true)
    Long countActiveProducts(
            @Param("sellerId") UUID sellerId
    );

    // 4. Count sold product quantities.
    @Query(value = """
            SELECT COALESCE(SUM(oi.quantity), 0)
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE o.seller_id = :sellerId
              AND o.order_status = 4
            """, nativeQuery = true)
    Long countSoldProducts(
            @Param("sellerId") UUID sellerId
    );

    // 5. Revenue grouped by day.
    @Query(value = """
            SELECT
                CAST(o.completed_at AS DATE) AS "revenueDate",
                COALESCE(SUM(o.total_amount - o.shipping_fee), 0)
                    AS revenue,
                COUNT(*) AS "orderCount"
            FROM orders o
            WHERE o.seller_id = :sellerId
              AND o.order_status = 4
              AND o.completed_at >= :fromDate
              AND o.completed_at < :toDate
            GROUP BY CAST(o.completed_at AS DATE)
            ORDER BY CAST(o.completed_at AS DATE)
            """, nativeQuery = true)
    List<RevenueProjection> getRevenueChart(
            @Param("sellerId") UUID sellerId,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate
    );

    // 6. Top selling products.
    @Query(value = """
            SELECT
                oi.product_id AS "productId",
                oi.product_name AS "productName",
                SUM(oi.quantity) AS "totalSold",
                SUM(oi.unit_price * oi.quantity) AS "totalRevenue"
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE o.seller_id = :sellerId
              AND o.order_status = 4
            GROUP BY oi.product_id, oi.product_name
            ORDER BY SUM(oi.quantity) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<TopProductProjection> getTopProducts(
            @Param("sellerId") UUID sellerId,
            @Param("limit") int limit
    );
}
