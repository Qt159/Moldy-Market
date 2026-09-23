package com.moldy.moldymarket.store.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.moldy.moldymarket.store.entity.Store;

import java.util.UUID;

public interface StoreRepository extends JpaRepository<Store, UUID> {
    boolean existsByNameIgnoreCase(String name);
}
