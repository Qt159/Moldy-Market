package com.moldy.moldymarket.payment.service;

import com.moldy.moldymarket.order.entity.CheckoutGroup;
import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import com.moldy.moldymarket.order.service.CheckoutExpirationService;
import com.moldy.moldymarket.payment.entity.*;
import com.moldy.moldymarket.payment.enums.*;
import com.moldy.moldymarket.payment.mapper.PaymentTransactionMapper;
import com.moldy.moldymarket.payment.repository.*;
import com.moldy.moldymarket.payment.webhook.IncomingTransfer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentTransactionServiceTest {
    @Mock PaymentRepository paymentRepository;
    @Mock PaymentTransactionRepository paymentTransactionRepository;
    @Mock PaymentTransactionMapper paymentTransactionMapper;
    @Mock ApplicationEventPublisher events;
    @Mock CheckoutGroupRepository checkouts;
    @Mock CheckoutExpirationService expiration;
    @InjectMocks PaymentTransactionService service;
    UUID checkoutId=UUID.randomUUID();
    Payment payment;
    PaymentTransaction transaction;
    CheckoutGroup checkout;
    @BeforeEach void setup() {
        checkout=CheckoutGroup.builder().expiredAt(Instant.now().plusSeconds(900)).build();
        ReflectionTestUtils.setField(checkout,"id",checkoutId);
        payment=Payment.builder().checkoutGroupId(checkoutId).payerId(UUID.randomUUID()).amount(BigDecimal.TEN)
                .method(PaymentMethod.ONLINE_BANKING).expiredAt(checkout.getExpiredAt()).build();
        ReflectionTestUtils.setField(payment,"id",UUID.randomUUID());
        transaction=new PaymentTransaction();
        when(paymentTransactionMapper.toPaymentTransaction(any())).thenReturn(transaction);
        when(paymentTransactionRepository.saveAndFlush(transaction)).thenReturn(transaction);
        lenient().when(paymentRepository.findCheckoutGroupIdByPaymentCode("MM12345678")).thenReturn(Optional.of(checkoutId));
        lenient().when(checkouts.findByIdForUpdate(checkoutId)).thenReturn(Optional.of(checkout));
        lenient().when(paymentRepository.findByCheckoutGroupIdForUpdate(checkoutId)).thenReturn(Optional.of(payment));
    }
    @Test void validIncomingTransferLocksCheckoutBeforePaymentAndPublishesConfirmation() {
        service.process(incoming(TransferType.IN,BigDecimal.TEN));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESSFUL);
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(payment.getPaymentTransaction()).isSameAs(transaction);
        assertThat(transaction.getMatchStatus()).isEqualTo(MatchStatus.MATCHED);
        var locks=inOrder(checkouts,paymentRepository);
        locks.verify(checkouts).findByIdForUpdate(checkoutId);
        locks.verify(paymentRepository).findByCheckoutGroupIdForUpdate(checkoutId);
        verify(events).publishEvent(any(Object.class));
    }
    @Test void laterTransfersForASuccessfulPaymentAreDuplicates() {
        payment.setStatus(PaymentStatus.SUCCESSFUL);
        service.process(incoming(TransferType.IN,BigDecimal.TEN));
        assertThat(transaction.getMatchStatus()).isEqualTo(MatchStatus.DUPLICATE);
        verifyNoInteractions(events,expiration);
    }
    @Test void lateTransferIsRecordedAndCannotCompleteAnExpiredCheckout() {
        checkout.setExpiredAt(Instant.now().minusSeconds(1));
        service.process(incoming(TransferType.IN,BigDecimal.TEN));
        assertThat(transaction.getMatchStatus()).isEqualTo(MatchStatus.LATE);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(expiration).expire(eq(checkoutId),any());
        verifyNoInteractions(events);
    }
    @Test void wrongAmountDoesNotChangeThePayment() {
        service.process(incoming(TransferType.IN,BigDecimal.ONE));
        assertThat(transaction.getMatchStatus()).isEqualTo(MatchStatus.AMOUNT_MISMATCH);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(events);
    }
    @Test void outgoingMoneyCannotPayAnOrder() {
        service.process(incoming(TransferType.OUT,BigDecimal.TEN));
        assertThat(transaction.getMatchStatus()).isEqualTo(MatchStatus.IGNORED);
        verifyNoInteractions(events,checkouts,paymentRepository);
    }
    @Test void unknownCodeIsRetainedForReconciliation() {
        when(paymentRepository.findCheckoutGroupIdByPaymentCode("MM12345678")).thenReturn(Optional.empty());
        service.process(incoming(TransferType.IN,BigDecimal.TEN));
        assertThat(transaction.getMatchStatus()).isEqualTo(MatchStatus.UNMATCHED);
        verifyNoInteractions(events,checkouts);
    }
    @Test void listenerFailureIsNotSwallowed() {
        doThrow(new IllegalStateException("Confirmation failed")).when(events).publishEvent(any(Object.class));
        assertThatThrownBy(()->service.process(incoming(TransferType.IN,BigDecimal.TEN)))
                .hasMessage("Confirmation failed");
    }
    private IncomingTransfer incoming(TransferType type,BigDecimal amount) {
        return new IncomingTransfer("sepay",UUID.randomUUID().toString(),Instant.now(),"test-account","test-bank",null,
                "MM12345678",null,"MM12345678",type,amount,null,"{}");
    }
}
