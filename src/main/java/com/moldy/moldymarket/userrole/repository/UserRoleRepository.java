package com.moldy.moldymarket.userrole.repository;

import com.moldy.moldymarket.userrole.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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


    void deleteByUserIdAndRole_NameAndStoreId(UUID userId, String roleName, UUID storeId);

    //get role of many users at the same time (avoid N+1 query when show users list)
    @Query("select ur from UserRole ur join fetch ur.role where ur.user.id in :userIds")
    List<UserRole> findAllWithRoleByUserIdIn(@Param("userIds")Collection<UUID> userIds);
}
