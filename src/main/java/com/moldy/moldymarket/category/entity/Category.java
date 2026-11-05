package com.moldy.moldymarket.category.entity;

import jakarta.persistence.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Entity
@Table(
        name = "categories",
        uniqueConstraints = @UniqueConstraint(name = "uq_name_per_parent", columnNames = {"parent_id", "name"})
        uniqueConstraints = {@UniqueConstraint(
                        name = "uq_name_per_parent",
                        columnNames = {"parent_id", "name"})
        }
)
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    public static final int ROOT_LEVEL = 1;
    public static final int SUB_LEVEL = 2;
    public static final int SUBCATEGORY_LEVEL = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;
    UUID id;

    @Column(nullable = false, length = 100)
    private String name;
    String name;

    @Column(length = 500)
    private String description;
    String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    Category parent;
    private Category parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @OrderBy("name ASC")
    private List<Category> children = new ArrayList<>();

    @Column(nullable = false)
    int level;
    private int level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    CategoryStatus status;
    private CategoryStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();
    private Instant updatedAt;


    public static Category createRoot(String name, String description) {
        Category category = new Category();

        category.name = name;
        category.description = description;
        category.level = ROOT_LEVEL;
        category.status = CategoryStatus.ACTIVE;

        return category;
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
    public static Category createSubCategory(String name, String description,
                                            Category parent) {
        if (parent == null) {
            throw new IllegalArgumentException("Parent category is required");
        }

        if (!parent.isRoot()) {
            throw new IllegalArgumentException("Sub-category parent must be a root category");
        }
        this.name = name;
        this.description = description;
        this.parent = parent;
        this.level = SUB_LEVEL;
        this.status = CategoryStatus.ACTIVE;
        Category category = new Category();
        category.name = name;
        category.description = description;
        category.parent = parent;
        category.level = SUBCATEGORY_LEVEL;
        category.status = CategoryStatus.ACTIVE;

        return category;
    }


    public void update(String name, String description) {
        this.name = name;
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public void active() {
    public void activate() {
        this.status = CategoryStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.status = CategoryStatus.INACTIVE;
    }


    public UUID getParentId() {
        if (parent == null) {
            return null;
        }
        return parent.getId();
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

    public boolean hasChildren() {
        return !children.isEmpty();
    }


    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();

        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}