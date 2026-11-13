package com.moldy.moldymarket.category.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "categories",
        uniqueConstraints = @UniqueConstraint(name = "uq_name_per_parent", columnNames = {"parent_id", "name"})
)
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    public static final int ROOT_LEVEL = 1;
    public static final int SUB_LEVEL = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false, length = 100)
    String name;

    @Column(length = 500)
    String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    Category parent;

    @Column(nullable = false)
    int level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    CategoryStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();

    // Tạo Root Category
    public Category(String name, String description) {
        this.name = name;
        this.description = description;
        this.level = ROOT_LEVEL;
        this.status = CategoryStatus.ACTIVE;
    }

    // Tạo Sub-category
    public Category(String name, String description, Category parent) {
        if (parent == null || !parent.isRoot()) {
            throw new IllegalArgumentException("Sub-category parent must be a root category");
        }
        this.name = name;
        this.description = description;
        this.parent = parent;
        this.level = SUB_LEVEL;
        this.status = CategoryStatus.ACTIVE;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public void active() {
        this.status = CategoryStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public UUID getParentId() {
        return parent == null ? null : parent.getId();
    }

    public boolean isRoot() {
        return parent == null;
    }

    public boolean isSubCategory() {
        return parent != null;
    }

    public boolean isActive() {
        return status == CategoryStatus.ACTIVE;
    }
}
