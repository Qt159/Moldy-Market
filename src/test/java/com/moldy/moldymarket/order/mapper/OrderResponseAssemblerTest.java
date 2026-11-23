package com.moldy.moldymarket.order.mapper;

import com.moldy.moldymarket.order.entity.CheckoutGroup;
import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.payment.entity.Payment;
import com.moldy.moldymarket.payment.enums.PaymentMethod;
import com.moldy.moldymarket.payment.enums.PaymentStatus;
import com.moldy.moldymarket.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderResponseAssemblerTest {
    PaymentRepository payments;
    OrderResponseAssembler assembler;
    UUID groupId = UUID.randomUUID();
    @BeforeEach void setup() {
        payments = Mockito.mock(PaymentRepository.class);
        assembler = new OrderResponseAssembler(Mappers.getMapper(OrderMapper.class), payments);
    }

    @Test void codUsesEachOrdersPaidAtWhileTheSharedPaymentRemainsPending() {
        var payment = payment(PaymentMethod.COD, PaymentStatus.PENDING);
        var paid = order(OrderStatus.COMPLETED);
        var stamp = Instant.parse("2026-11-22T03:00:00Z");
        paid.setPaidAt(stamp);
        var unpaid = order(OrderStatus.DELIVERED);
        when(payments.findAllByCheckoutGroupIdIn(List.of(groupId))).thenReturn(List.of(payment));
        var rows = assembler.toResponses(List.of(paid, unpaid));
        assertThat(rows.get(0).paymentMethod()).isEqualTo(PaymentMethod.COD);
        assertThat(rows.get(0).paymentStatus()).isEqualTo(PaymentStatus.SUCCESSFUL);
        assertThat(rows.get(0).paidAt()).isEqualTo(stamp);
        assertThat(rows.get(1).paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(rows.get(1).paidAt()).isNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(payments).findAllByCheckoutGroupIdIn(List.of(groupId));
        verifyNoMoreInteractions(payments);
    }

    @Test void cancellingOneCodOrderDoesNotMarkOtherOrdersFailed() {
        when(payments.findAllByCheckoutGroupIdIn(List.of(groupId)))
                .thenReturn(List.of(payment(PaymentMethod.COD, PaymentStatus.PENDING)));
        var rows = assembler.toResponses(List.of(order(OrderStatus.CANCELLED), order(OrderStatus.CONFIRMED)));
        assertThat(rows.get(0).paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(rows.get(1).paymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @ParameterizedTest @EnumSource(PaymentStatus.class)
    void onlineUsesTheCanonicalPaymentStatus(PaymentStatus status) {
        when(payments.findAllByCheckoutGroupIdIn(List.of(groupId)))
                .thenReturn(List.of(payment(PaymentMethod.ONLINE_BANKING, status)));
        var row = assembler.toResponse(order(OrderStatus.CONFIRMED));
        assertThat(row.paymentMethod()).isEqualTo(PaymentMethod.ONLINE_BANKING);
        assertThat(row.paymentStatus()).isEqualTo(status);
    }

    @Test void missingCheckoutOrPaymentStaysUnknownAndAnEmptyListDoesNotQueryPayments() {
        assertThat(assembler.toResponses(List.of())).isEmpty();
        var legacy = order(OrderStatus.CONFIRMED); legacy.setCheckoutGroup(null);
        assertThat(assembler.toResponse(legacy).paymentMethod()).isNull();
        verifyNoInteractions(payments);
        when(payments.findAllByCheckoutGroupIdIn(List.of(groupId))).thenReturn(List.of());
        var row = assembler.toResponse(order(OrderStatus.CONFIRMED));
        assertThat(row.paymentMethod()).isNull();
        assertThat(row.paymentStatus()).isNull();
    }

    @Test void duplicatePaymentsFailInsteadOfArbitrarilyChoosingOne() {
        when(payments.findAllByCheckoutGroupIdIn(List.of(groupId))).thenReturn(List.of(
                payment(PaymentMethod.COD, PaymentStatus.PENDING), payment(PaymentMethod.ONLINE_BANKING, PaymentStatus.SUCCESSFUL)));
        assertThatThrownBy(() -> assembler.toResponse(order(OrderStatus.CONFIRMED)))
                .isInstanceOf(IllegalStateException.class);
    }

    private Order order(OrderStatus status) {
        var group = CheckoutGroup.builder().buyerId(UUID.randomUUID()).totalAmount(BigDecimal.TEN).build();
        ReflectionTestUtils.setField(group, "id", groupId);
        return Order.builder().checkoutGroup(group).orderCode("ORD-TEST").status(status)
                .buyerId(UUID.randomUUID()).sellerId(UUID.randomUUID()).storeId(UUID.randomUUID())
                .totalAmount(BigDecimal.TEN).shippingFee(BigDecimal.ZERO).build();
    }
    private Payment payment(PaymentMethod method, PaymentStatus status) {
        var payment = Payment.builder().checkoutGroupId(groupId).method(method).amount(BigDecimal.TEN).build();
        payment.setStatus(status); return payment;
    }
}
