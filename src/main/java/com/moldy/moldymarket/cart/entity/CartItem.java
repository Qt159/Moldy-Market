package com.moldy.moldymarket.cart.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/*
    - Không lưu trực tiếp đối tượng Listing để tránh coupling quá chặt
   - Lưu listingId để có thể trace sản phẩm
   - Lưu priceAtAddTime để ghi nhận giá tại thời điểm thêm vào
   - Không auto-sync giá nếu seller thay đổi giá (user sẽ biết khi checkout)
*/
@Entity
@Table(name = "cart_items", indexes = {
        @Index(name = "idx_cart_items_cart_id", columnList = "cart_id"),
        @Index(name = "idx_cart_items_listing_id", columnList = "listing_id"),
        @Index(name = "idx_cart_items_cart_id_listing_id", columnList = "cart_id, listing_id", unique = true)
})
@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@Builder
public class CartItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cart_items_cart_id"))
    Cart cart;

    //Không dùng @ManyToOne đến Listing để tránh coupling quá chặt.
    
    @Column(name = "listing_id", nullable = false, columnDefinition = "UUID")
    UUID listingId;

   
    @Column(name = "seller_id", nullable = false, columnDefinition = "UUID")
    UUID sellerId;

    
    @Column(name = "quantity", nullable = false)
    @Builder.Default
    Integer quantity = 1;

    /*
     - Giá của sản phẩm tại thời điểm được thêm vào giỏ
     - Nếu seller sau đó thay đổi giá, giỏ vẫn lưu giá cũ
    */
    @Column(name = "price_at_add_time", nullable = false, precision = 19, scale = 2)
    BigDecimal priceAtAddTime;

    @Column(name = "total_price", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    BigDecimal totalPrice = BigDecimal.ZERO;

    
     //Ghi chú: "Hỏi trước khi mua", "Cẩn thận khi giao",...
    @Column(name = "note", length = 500)
    String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.quantity == null) {
            this.quantity = 1;
        }
        if (this.priceAtAddTime != null) {
            this.totalPrice = this.priceAtAddTime.multiply(new BigDecimal(this.quantity));
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
        // Cập nhật totalPrice nếu quantity thay đổi
        if (this.priceAtAddTime != null && this.quantity != null) {
            this.totalPrice = this.priceAtAddTime.multiply(new BigDecimal(this.quantity));
        }
    }


    public void updateQuantity(Integer newQuantity) {
        if (newQuantity != null && newQuantity > 0) {
            this.quantity = newQuantity;
            if (this.priceAtAddTime != null) {
                this.totalPrice = this.priceAtAddTime.multiply(new BigDecimal(newQuantity));
            }
            this.updatedAt = Instant.now();
        }
    }
}
