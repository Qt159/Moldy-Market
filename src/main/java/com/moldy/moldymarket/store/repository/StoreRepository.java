package com.moldy.moldymarket.store.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import com.moldy.moldymarket.store.entity.Store;

import java.util.Optional;
import java.util.UUID;

public interface StoreRepository extends JpaRepository<Store, UUID> {
    boolean existsByNameIgnoreCase(String name);

    Optional<Store> findByOwnerId(UUID ownerId);

    boolean existsByOwnerId(UUID ownerId);

    Slice<Store> findAllBy(Pageable pageable);
}
