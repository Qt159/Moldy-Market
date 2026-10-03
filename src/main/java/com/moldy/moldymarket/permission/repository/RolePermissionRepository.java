package com.moldy.moldymarket.permission.repository;

import com.moldy.moldymarket.permission.entity.GrantType;
import com.moldy.moldymarket.permission.entity.Permission;
import com.moldy.moldymarket.permission.entity.RolePermission;
import com.moldy.moldymarket.role.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    boolean existsByRoleAndPermission(Role role, Permission permission);

    @Query("""
        SELECT rp.permission.code
        FROM RolePermission rp
        WHERE rp.role.id IN :roleIds
        AND rp.grantType = :grantType
        """)
    List<String> findPermissionCodesByRoleIdsAndGrantType(
            @Param("roleIds") List<UUID> roleIds,
            @Param("grantType")GrantType grantType
    );
}
