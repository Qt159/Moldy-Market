package com.moldy.moldymarket.permission.repository;

import com.moldy.moldymarket.permission.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByCode(String code);

    List<Permission> findAllByCodeIn(Collection<String> codes);

    boolean existsByCode(String code);
}
