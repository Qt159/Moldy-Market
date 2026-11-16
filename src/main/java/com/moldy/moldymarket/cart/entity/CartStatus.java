package com.moldy.moldymarket.cart.entity;


public enum CartStatus {
    ACTIVE,           
    ABANDONED,        // Bỏ không (chưa check out sau 30 ngày)
    COMPLETED         // Đã checkout, giỏ hàng được xóa
}
