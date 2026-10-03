package com.moldy.moldymarket.userrole.repository;

import com.moldy.moldymarket.userrole.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findAllByUserId(UUID userId);

    List<UserRole> findAllByUserIdAndStoreId(
            UUID userId,
            UUID storeId
    );

    boolean existsByUserIdAndRole_Name(
            UUID userId,
            String roleName
    );
}
