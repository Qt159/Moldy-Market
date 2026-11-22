package com.moldy.moldymarket.payment.service;

import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.entity.CheckoutGroup;
import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
import com.moldy.moldymarket.order.repository.*;
import com.moldy.moldymarket.payment.entity.Payment;
import com.moldy.moldymarket.payment.enums.PaymentMethod;
import com.moldy.moldymarket.payment.mapper.PaymentMapper;
import com.moldy.moldymarket.payment.repository.PaymentRepository;
import com.moldy.moldymarket.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock PaymentRepository payments;
    @Mock PaymentMapper mapper;
    @Mock CheckoutGroupRepository checkouts;
    @Mock CurrentUser currentUser;
    @Mock OrderRepository orders;
    @InjectMocks PaymentService service;
    UUID checkoutId=UUID.randomUUID(),buyerId=UUID.randomUUID();
    CheckoutGroup checkout;
    @BeforeEach void setup() {
        checkout=CheckoutGroup.builder().buyerId(buyerId).totalAmount(BigDecimal.valueOf(45000)).build();
        ReflectionTestUtils.setField(checkout,"id",checkoutId);
        ReflectionTestUtils.setField(service,"bankAccount","test-account");
        ReflectionTestUtils.setField(service,"bankName","test-bank");
    }
    @Test void buildsBankPaymentFromTheStoredBuyerAndTotal() {
        when(checkouts.findById(checkoutId)).thenReturn(Optional.of(checkout));
        when(payments.findByCheckoutGroupId(checkoutId)).thenReturn(Optional.empty());
        service.createPayment(checkoutId,PaymentMethod.ONLINE_BANKING);
        var payment=ArgumentCaptor.forClass(Payment.class); verify(payments).save(payment.capture());
        assertThat(payment.getValue().getPayerId()).isEqualTo(buyerId);
        assertThat(payment.getValue().getAmount()).isEqualByComparingTo("45000");
        assertThat(payment.getValue().getQrBankUrl()).contains("amount=45000");
    }
    @Test void codDoesNotCreateABankQr() {
        checkout.setStatus(CheckoutGroupStatus.PAY_ON_DELIVERY);
        when(checkouts.findById(checkoutId)).thenReturn(Optional.of(checkout));
        when(payments.findByCheckoutGroupId(checkoutId)).thenReturn(Optional.empty());
        service.createPayment(checkoutId,PaymentMethod.COD);
        var payment=ArgumentCaptor.forClass(Payment.class); verify(payments).save(payment.capture());
        assertThat(payment.getValue().getQrBankUrl()).isNull();
        assertThat(payment.getValue().getPaymentCode()).isNull();
    }
    @Test void fractionalVndIsRejectedAsAnOrderAmountError() {
        checkout.setTotalAmount(new BigDecimal("10000.05"));
        when(checkouts.findById(checkoutId)).thenReturn(Optional.of(checkout));
        when(payments.findByCheckoutGroupId(checkoutId)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.createPayment(checkoutId,PaymentMethod.ONLINE_BANKING))
                .hasFieldOrPropertyWithValue("errorCode",com.moldy.moldymarket.common.exception.ErrorCode.INVALID_ORDER_AMOUNT);
        verify(payments,never()).save(any());
    }
    @Test void readingByCheckoutDoesNotExposeAnotherBuyersPayment() {
        var user=new User(); user.setId(UUID.randomUUID()); when(currentUser.findCurrentUser()).thenReturn(user);
        when(payments.findByCheckoutGroupId(checkoutId)).thenReturn(Optional.of(Payment.builder().payerId(buyerId).build()));
        assertThatThrownBy(()->service.getPaymentByCheckoutGroupId(checkoutId)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(mapper);
    }
}
