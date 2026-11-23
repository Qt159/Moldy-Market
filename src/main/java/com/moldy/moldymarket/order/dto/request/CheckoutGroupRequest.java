package com.moldy.moldymarket.order.dto.request;

import com.moldy.moldymarket.payment.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

public record CheckoutGroupRequest(
        @NotEmpty @Size(max = 20)
        List<@Valid OrderItemRequest> orderItems,
        //List<UUID> shopVoucherId,
        //UUID platformVoucherId,
        @NotBlank @Size(max = 255)
        String receiverName,
        @NotBlank @Pattern(regexp = "^(\\+84|0)\\d{9,10}$", message = "Invalid Vietnamese phone nunmber")
        String phoneNumber,
        @NotBlank @Size(max = 500)
        String shippingAddress,
        @NotNull
        PaymentMethod method,
        @Size(max = 500)
        String note,
        @Size(max = 50)
        String voucherCode) {

    public CheckoutGroupRequest(
            List<OrderItemRequest> orderItems,
            String receiverName,
            String phoneNumber,
            String shippingAddress,
            PaymentMethod method,
            String note) {
        this(orderItems, receiverName, phoneNumber, shippingAddress, method, note, null);
    }
}
