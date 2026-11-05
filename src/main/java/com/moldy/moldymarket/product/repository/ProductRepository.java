package com.moldy.moldymarket.product.repository;

import com.moldy.moldymarket.product.entity.Product;
import com.moldy.moldymarket.product.entity.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    boolean existsByCategoryId(UUID categoryId);

    Optional<Product> findByIdAndStatusNot(UUID id, ProductStatus status);

    long countBySellerIdAndStoreIdIsNullAndStatusIn(UUID sellerId, Collection<ProductStatus> statuses);

    long countByStoreIdAndStatusIn(UUID storeId, Collection<ProductStatus> statuses);

    @Query("select p from Product p where p.id in :productIds")
    Set<Product> findAllByIds(@Param("productIds") List<UUID> productIds);
}
