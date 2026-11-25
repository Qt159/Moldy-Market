package com.moldy.moldymarket.voucher.repository;

import com.moldy.moldymarket.voucher.model.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, UUID> {
    Optional<Voucher> findByCode(String code);
    boolean existsByCode(String code);

    List<Voucher> findAllByStore_IdOrderByCreatedAtDesc(UUID storeId);

    @Query("""
            select v from Voucher v
            where v.active = true
              and v.remainingQuantity > 0
              and v.expiredAt > :now
              and v.issuer = com.moldy.moldymarket.voucher.model.VoucherIssuer.PLATFORM
            order by v.expiredAt asc
            """)
    List<Voucher> findAvailablePlatformVouchers(@Param("now") Instant now);

    @Query("""
            select v from Voucher v
            where v.active = true
              and v.remainingQuantity > 0
              and v.expiredAt > :now
              and (
                v.issuer = com.moldy.moldymarket.voucher.model.VoucherIssuer.PLATFORM
                or (v.issuer = com.moldy.moldymarket.voucher.model.VoucherIssuer.STORE and v.store.id in :storeIds)
              )
            order by v.issuer asc, v.expiredAt asc
            """)
    List<Voucher> findAvailableForStores(@Param("storeIds") Collection<UUID> storeIds, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voucher v where v.code = :code")
    Optional<Voucher> findByCodeForUpdate(@Param("code") String code);

    @Modifying
    @Query(value = """
            UPDATE vouchers
               SET remaining_quantity = remaining_quantity - 1,
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = :id AND active = true
               AND expired_at > CURRENT_TIMESTAMP
               AND remaining_quantity > 0
            """, nativeQuery = true)
    int consumeOne(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            UPDATE vouchers
               SET remaining_quantity = remaining_quantity - 1,
                   reserved_quantity = reserved_quantity + 1,
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = :id AND active = true
               AND expired_at > CURRENT_TIMESTAMP
               AND remaining_quantity > 0
            """, nativeQuery = true)
    int reserveOne(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            UPDATE vouchers
               SET reserved_quantity = reserved_quantity - 1,
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = :id AND reserved_quantity > 0
            """, nativeQuery = true)
    int finalizeReservation(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            UPDATE vouchers
               SET reserved_quantity = reserved_quantity - 1,
                   remaining_quantity = remaining_quantity + 1,
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = :id AND reserved_quantity > 0
            """, nativeQuery = true)
    int releaseReservation(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            UPDATE vouchers
               SET remaining_quantity = remaining_quantity + 1,
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = :id
            """, nativeQuery = true)
    int restoreOne(@Param("id") UUID id);
}
