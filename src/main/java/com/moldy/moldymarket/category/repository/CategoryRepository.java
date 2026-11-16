package com.moldy.moldymarket.category.repository;

import com.moldy.moldymarket.category.entity.Category;
import com.moldy.moldymarket.category.entity.CategoryStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    boolean existsByParentIdAndName(UUID parentId, String name);

    boolean existsByParentIdAndNameAndIdNot(UUID parentId, String name, UUID excludeId);

    boolean existsByParentIsNullAndName(String name);

    boolean existsByParentIsNullAndNameAndIdNot(String name, UUID excludeId);

    boolean existsByParentId(UUID parentId);

    List<Category> findAllByStatusOrderByLevelAscNameAsc(CategoryStatus status);
}