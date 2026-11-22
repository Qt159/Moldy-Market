package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.order.entity.*;
import com.moldy.moldymarket.order.mapper.OrderMapper;
import com.moldy.moldymarket.payment.entity.Payment;
import com.moldy.moldymarket.payment.enums.PaymentStatus;
import com.moldy.moldymarket.payment.enums.PaymentMethod;
import com.moldy.moldymarket.payment.mapper.PaymentMapper;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class OrderMapperContractTest {
    @Test void paymentJsonContractIncludesTheActualStatus() {
        var payment=Payment.builder().build(); payment.setStatus(PaymentStatus.SUCCESSFUL);
        payment.setMethod(PaymentMethod.COD);
        assertThat(Mappers.getMapper(PaymentMapper.class).toPaymentResponse(payment).paymentStatus())
                .isEqualTo("SUCCESSFUL");
        assertThat(Mappers.getMapper(PaymentMapper.class).toPaymentResponse(payment).method())
                .isEqualTo(PaymentMethod.COD);
    }
    @Test void orderAndItemResponsesKeepTheirParentIds() {
        var checkout=CheckoutGroup.builder().build(); var checkoutId=UUID.randomUUID();
        ReflectionTestUtils.setField(checkout,"id",checkoutId);
        var order=Order.builder().build(); var orderId=UUID.randomUUID();
        ReflectionTestUtils.setField(order,"id",orderId);
        checkout.addOrder(order);
        order.addOrderItem(OrderItem.builder().quantity(1).build());
        var mapper=Mappers.getMapper(OrderMapper.class);
        var response=mapper.toOrderResponse(order);
        assertThat(response.checkoutGroupId()).isEqualTo(checkoutId);
        assertThat(response.items().get(0).orderId()).isEqualTo(orderId);
        assertThat(response.items().get(0).quantity()).isEqualTo(1);
        assertThat(mapper.toSummary(order).checkoutGroupId()).isEqualTo(checkoutId);
    }
}
