package com.moldy.moldymarket.voucher.repository;

import com.moldy.moldymarket.voucher.model.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, UUID> {
    Optional<Voucher> findByCode(String code);
    boolean existsByCode(String code);

    java.util.List<Voucher> findAllByStore_IdOrderByCreatedAtDesc(UUID storeId);

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
