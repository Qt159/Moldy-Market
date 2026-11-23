package com.moldy.moldymarket.payment.mapper;

import com.moldy.moldymarket.payment.dto.response.PaymentResponse;
import com.moldy.moldymarket.payment.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {
    @Mapping(target = "paymentStatus", source = "status")
    PaymentResponse toPaymentResponse(Payment payment);

}
