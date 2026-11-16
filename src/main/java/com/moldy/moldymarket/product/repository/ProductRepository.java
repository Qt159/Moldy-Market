package com.moldy.moldymarket.product.repository;

import com.moldy.moldymarket.product.entity.Product;
import com.moldy.moldymarket.product.entity.ProductStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    boolean existsByCategoryId(UUID categoryId);

    Optional<Product> findByIdAndStatusNot(UUID id, ProductStatus status);

    long countBySellerIdAndStoreIdIsNullAndStatusIn(UUID sellerId, Collection<ProductStatus> statuses);

    long countByStoreIdAndStatusIn(UUID storeId, Collection<ProductStatus> statuses);

    @Query("select p from Product p where p.id in :productIds")
    Set<Product> findAllByIds(@Param("productIds") List<UUID> productIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :productId")
    Optional<Product> findByIdForUpdate(UUID productId);



    @Modifying
    @Query("""
    UPDATE Product p
    SET p.status = com.moldy.moldymarket.product.entity.ProductStatus.SOLD,
        p.reservedCheckoutGroupId = null
    WHERE p.id = :productId
      AND p.status = com.moldy.moldymarket.product.entity.ProductStatus.RESERVED
      AND p.reservedCheckoutGroupId = :checkoutGroupId
""")
    int markProductSold(UUID productId, UUID checkoutGroupId);


}
