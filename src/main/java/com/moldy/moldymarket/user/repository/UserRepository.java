package com.moldy.moldymarket.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.moldy.moldymarket.user.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByGoogleId(String googleId);
}
