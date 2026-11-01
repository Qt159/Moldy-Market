package com.moldy.moldymarket.cart.entity;

import com.moldy.moldymarket.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/* 
    Mỗi user chỉ có một giỏ hàng ACTIVE tại một thời điểm.
    User checkout bằng cách tạo Order và xóa CartItem
    Giỏ hàng ABANDONED sau 30 ngày không hoạt động
 */
@Entity
@Table(name = "carts", indexes = {
        @Index(name = "idx_carts_user_id_status", columnList = "user_id, status"),
        @Index(name = "idx_carts_status_last_updated_at", columnList = "status, last_updated_at")
})
@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@Builder
public class Cart {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_carts_user_id"))
    User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    CartStatus status = CartStatus.ACTIVE;

    @OneToMany(mappedBy = "cart", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<CartItem> items = new ArrayList<>();

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "total_items", nullable = false)
    @Builder.Default
    Integer totalItems = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "last_updated_at", nullable = false)
    Instant lastUpdatedAt;

    @Column(name = "abandoned_at", nullable = true)
    Instant abandonedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.lastUpdatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastUpdatedAt = Instant.now();
    }

    
    public void addItem(CartItem cartItem) {
        if (!this.items.contains(cartItem)) {
            this.items.add(cartItem);
            cartItem.setCart(this);
        }
        updateCartTotals();
    }

  
    public void removeItem(CartItem cartItem) {
        this.items.remove(cartItem);
        cartItem.setCart(null);
        updateCartTotals();
    }

   
    public void updateCartTotals() {
        this.subtotal = this.items.stream()
                .map(CartItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalItems = this.items.stream()
                .mapToInt(CartItem::getQuantity)
                .sum();

        this.lastUpdatedAt = Instant.now();
    }

    
    public void clearCart() {
        this.items.clear();
        this.subtotal = BigDecimal.ZERO;
        this.totalItems = 0;
        this.lastUpdatedAt = Instant.now();
    }

    
    public boolean containsListingId(UUID listingId) {
        return this.items.stream()
                .anyMatch(item -> item.getListingId().equals(listingId));
    }

  
    public CartItem findItemByListingId(UUID listingId) {
        return this.items.stream()
                .filter(item -> item.getListingId().equals(listingId))
                .findFirst()
                .orElse(null);
    }
}
