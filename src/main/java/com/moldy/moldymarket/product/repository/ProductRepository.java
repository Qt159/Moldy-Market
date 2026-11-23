package com.moldy.moldymarket.product.repository;

import com.moldy.moldymarket.product.entity.Product;
import com.moldy.moldymarket.product.entity.ProductStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
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

    Page<Product> findBySellerIdAndStoreIdIsNullAndStatusIn(
            UUID sellerId, Collection<ProductStatus> statuses,
            Pageable pageable);

    Page<Product> findByStoreIdAndStatusIn(
            UUID storeId,
            Collection<ProductStatus> statuses,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :productId")
    Optional<Product> findByIdForUpdate(@Param("productId") UUID productId);
}
