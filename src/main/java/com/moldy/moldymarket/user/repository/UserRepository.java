package com.moldy.moldymarket.user.repository;

import com.moldy.moldymarket.userrole.entity.UserRole;
import org.mapstruct.control.MappingControl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import com.moldy.moldymarket.user.entity.User;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByPhone(String phone);

    Slice<User> findAllBy(Pageable pageable);

}
