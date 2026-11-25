package com.moldy.moldymarket.dispute.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.storage.service.FileStorageService;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.dispute.dto.request.DisputeCreationRequest;
import com.moldy.moldymarket.dispute.dto.request.ReviewDisputeRequest;
import com.moldy.moldymarket.dispute.dto.response.DisputeResponse;
import com.moldy.moldymarket.dispute.entity.DisputeRequest;
import com.moldy.moldymarket.dispute.enums.DisputeDecision;
import com.moldy.moldymarket.dispute.enums.DisputeReason;
import com.moldy.moldymarket.dispute.enums.DisputeStatus;
import com.moldy.moldymarket.dispute.mapper.DisputeMapper;
import com.moldy.moldymarket.dispute.repository.DisputeRepository;
import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.order.service.OrderService;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {

    @Mock DisputeRepository disputeRepository;
    @Mock OrderRepository orderRepository;
    @Mock CurrentUser currentUser;
    @Mock DisputeMapper disputeMapper;
    @Mock OrderService orderService;
    @Mock WalletService walletService;
    @Mock ApplicationEventPublisher applicationEventPublisher;
    @Mock FileStorageService fileStorageService;
    @InjectMocks DisputeService disputeService;

    UUID buyerId = UUID.randomUUID();
    UUID sellerId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    UUID disputeId = UUID.randomUUID();
    User buyer;
    User admin;
    Order completedOrder;

    @BeforeEach
    void setUp() {
        buyer = new User();
        buyer.setId(buyerId);
        admin = new User();
        admin.setId(adminId);

        completedOrder = Order.builder()
                .buyerId(buyerId)
                .sellerId(sellerId)
                .orderCode("ORD-DSP01")
                .status(OrderStatus.DELIVERED)
                .totalAmount(BigDecimal.valueOf(100000))
                .build();
        completedOrder.confirmReceived();
        ReflectionTestUtils.setField(completedOrder, "id", orderId);
    }

    @Test
    void buyerCanOpenDisputeWithinReturnDeadline() {
        when(currentUser.findCurrentUser()).thenReturn(buyer);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(completedOrder));
        when(disputeRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(orderRepository.markDisputed(eq(orderId), any(Instant.class))).thenReturn(1);
        when(disputeMapper.toDisputeResponse(any(DisputeRequest.class))).thenReturn(mock(DisputeResponse.class));

        var request = new DisputeCreationRequest(orderId, DisputeReason.DAMAGED, "Item arrived cracked", List.of("https://img.test/1.jpg"));
        var response = disputeService.createDispute(request);

        assertThat(response).isNotNull();
        verify(disputeRepository).save(any(DisputeRequest.class));
        verify(orderRepository).markDisputed(eq(orderId), any(Instant.class));
    }

    @Test
    void buyerCannotOpenDisputeAfterReturnDeadline() {
        ReflectionTestUtils.setField(completedOrder, "returnDeadline", Instant.now().minus(1, ChronoUnit.DAYS));
        when(currentUser.findCurrentUser()).thenReturn(buyer);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(completedOrder));

        var request = new DisputeCreationRequest(orderId, DisputeReason.DAMAGED, "Too late", List.of());
        assertThatThrownBy(() -> disputeService.createDispute(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DISPUTE_PERIOD_EXPIRED);
        verify(disputeRepository, never()).save(any());
    }

    @Test
    void adminCanRejectOpenDisputeAndRevertOrderToCompleted() {
        var dispute = DisputeRequest.builder()
                .buyerId(buyerId).sellerId(sellerId).orderId(orderId)
                .reason(DisputeReason.WRONG_ITEM).description("Wrong color").build();
        ReflectionTestUtils.setField(dispute, "id", disputeId);

        when(currentUser.findCurrentUser()).thenReturn(admin);
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputeMapper.toDisputeResponse(dispute)).thenReturn(mock(DisputeResponse.class));

        disputeService.giveDecisionForDispute(disputeId, new ReviewDisputeRequest(DisputeDecision.REJECT, "Matches listing"));

        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.REJECTED);
        assertThat(dispute.getReviewedBy()).isEqualTo(adminId);
        assertThat(dispute.getDecisionNote()).isEqualTo("Matches listing");
        verify(orderService).completeDisputeOrder(orderId);
    }

    @Test
    void adminCanApproveOpenDisputeRefundOrderAndCreditBuyerWallet() {
        var dispute = DisputeRequest.builder()
                .buyerId(buyerId).sellerId(sellerId).orderId(orderId)
                .reason(DisputeReason.WRONG_ITEM).description("Wrong color").build();
        ReflectionTestUtils.setField(dispute, "id", disputeId);

        when(currentUser.findCurrentUser()).thenReturn(admin);
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(completedOrder));
        when(disputeMapper.toDisputeResponse(dispute)).thenReturn(mock(DisputeResponse.class));

        disputeService.giveDecisionForDispute(disputeId, new ReviewDisputeRequest(DisputeDecision.APPROVE, "Approved refund"));

        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.APPROVED);
        assertThat(dispute.getReviewedBy()).isEqualTo(adminId);
        assertThat(dispute.getDecisionNote()).isEqualTo("Approved refund");
        verify(orderService).refundDisputeOrder(orderId);
        verify(walletService).creditDisputeRefund(buyerId, orderId, "ORD-DSP01", BigDecimal.valueOf(100000));
    }

    @Test
    void getAllMyDisputeBatchLoadsOrdersAndMapsSummaries() {
        var dispute = DisputeRequest.builder()
                .buyerId(buyerId).sellerId(sellerId).orderId(orderId)
                .reason(DisputeReason.DAMAGED).description("Damaged item").build();
        ReflectionTestUtils.setField(dispute, "id", disputeId);

        var expectedSummary = new com.moldy.moldymarket.dispute.dto.response.DisputeSummaryResponse(
                disputeId, orderId, "ORD-DSP01", "Camera", "https://img.test/cam.jpg",
                BigDecimal.valueOf(100000), DisputeReason.DAMAGED, DisputeStatus.OPEN, null, Instant.now());

        when(currentUser.findCurrentUser()).thenReturn(buyer);
        when(disputeRepository.findAllDisputeByBuyerId(buyerId)).thenReturn(List.of(dispute));
        when(orderRepository.findAllByIdInWithItems(List.of(orderId))).thenReturn(List.of(completedOrder));
        when(disputeMapper.toDisputeSummaryResponse(dispute, completedOrder)).thenReturn(expectedSummary);

        var result = disputeService.getAllMyDispute();

        assertThat(result).containsExactly(expectedSummary);
        verify(orderRepository).findAllByIdInWithItems(List.of(orderId));
    }
}
