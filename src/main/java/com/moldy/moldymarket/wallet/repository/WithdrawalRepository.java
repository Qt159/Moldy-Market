package com.moldy.moldymarket.wallet.repository;

import com.moldy.moldymarket.wallet.dto.request.WithdrawalRequest;
import com.moldy.moldymarket.wallet.entity.Withdrawal;
import com.moldy.moldymarket.wallet.enums.WithdrawalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {
    Optional<Withdrawal> findByIdempotencyKey(String idempotencyKey);
    List<Withdrawal> findByWalletIdOrderByRequestedAtDesc(Long walletId);

    //prevent users spam many requests when the previous ones haven't handled yet as well as for the first withdrawal
    boolean existsByWalletIdAndBankAccountNumberAndStatus(Long walletId, String bankAccountNumber, WithdrawalStatus status);

    @Query("""
            select coalesce(sum(w.amount), 0) from Withdrawal w
            where w.walletId =: walletId\s
                       and w.requestedAt >=: since
                       and w.status <> com.moldy.moldymarket.wallet.enums.WithdrawalStatus.REJECTED
                       and w.status <> com.moldy.moldymarket.wallet.enums.WithdrawalStatus.FAILED
           \s""")
    BigDecimal sumRecentWithdrawals(@Param("walletId") Long walletId, @Param("since")Instant since);


}
