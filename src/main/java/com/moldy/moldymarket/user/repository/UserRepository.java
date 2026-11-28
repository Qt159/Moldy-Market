package com.moldy.moldymarket.user.repository;

import com.moldy.moldymarket.userrole.entity.UserRole;
import jakarta.persistence.LockModeType;
import org.mapstruct.control.MappingControl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import com.moldy.moldymarket.user.entity.User;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    Slice<User> findAllBy(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where lower(u.email) = lower(:email) ")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") UUID userId);
}
