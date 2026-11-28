package com.moldy.moldymarket.user.repository;

import com.moldy.moldymarket.user.entity.UserStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserStatusHistoryRepository extends JpaRepository<UserStatusHistory, UUID> {
    List<UserStatusHistory> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
