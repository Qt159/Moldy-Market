package com.moldy.moldymarket.payment.service;

import com.moldy.moldymarket.payment.dto.request.ConfirmPaymentRequest;
import com.moldy.moldymarket.payment.entity.PaymentTransaction;
import com.moldy.moldymarket.payment.enums.MatchStatus;
import com.moldy.moldymarket.payment.enums.TransferType;
import com.moldy.moldymarket.payment.repository.PaymentTransactionRepository;
import com.moldy.moldymarket.security.annotation.IsAdmin;
import com.moldy.moldymarket.wallet.service.EscrowService;
import com.moldy.moldymarket.wallet.service.WalletService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import java.util.stream.Stream;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import com.moldy.moldymarket.payment.dto.response.PaymentResponse;
import com.moldy.moldymarket.payment.entity.Payment;
import com.moldy.moldymarket.payment.enums.PaymentMethod;
import com.moldy.moldymarket.payment.enums.PaymentStatus;
import com.moldy.moldymarket.payment.mapper.PaymentMapper;
import com.moldy.moldymarket.payment.repository.PaymentRepository;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import static com.moldy.moldymarket.common.utils.CodeGenerator.generate;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentService {
    PaymentRepository paymentRepository;
    PaymentMapper paymentMapper;
    CheckoutGroupRepository checkoutGroupRepository;
    CurrentUser currentUser;
    PaymentTransactionRepository paymentTransactionRepository;
    EscrowService escrowService;
    WalletService walletService;

    @NonFinal
    @Value("${sePay.bank.account}")
    String bankAccount;

    @NonFinal
    @Value("${sePay.bank.name}")
    String bankName;

    @Transactional
    public PaymentResponse createPayment(UUID checkoutGroupId, UUID buyerId, BigDecimal amount, PaymentMethod method){
        var checkoutGroup = checkoutGroupRepository.findById(checkoutGroupId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        if(!checkoutGroup.getStatus().equals(CheckoutGroupStatus.PENDING_PAYMENT)) throw new AppException(ErrorCode.CHECKOUT_GROUP_INVALID_STATUS);
        //prevent retry
        var existedPayment = paymentRepository.findByCheckoutGroupIdAndStatus(checkoutGroup.getId(), PaymentStatus.PENDING);
        if(existedPayment.isPresent()) return paymentMapper.toPaymentResponse(existedPayment.get());

        String qrBankUrl = null, paymentCode = null;
        if(method == PaymentMethod.ONLINE_BANKING){
            if (bankAccount.isBlank() || bankName.isBlank()) {
                throw new AppException(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE);
            }
            paymentCode = generatePaymentCode();
            qrBankUrl = createQrBankUrl(paymentCode, amount);
        }

        Payment payment = Payment.builder()
                .checkoutGroupId(checkoutGroup.getId())
                .payerId(buyerId)
                .amount(amount)
                .method(method)
                .paymentCode(paymentCode)
                .qrBankUrl(qrBankUrl)
                .expiredAt(checkoutGroup.getExpiredAt())
                .build();
        return paymentMapper.toPaymentResponse(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID id){
        var user = currentUser.findCurrentUser();
        var payment = paymentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_EXISTED));
        usersCanView(payment, user.getId());
        return paymentMapper.toPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByCheckoutGroupId(UUID checkoutGroupId){
        var payment = paymentRepository.findByCheckoutGroupId(checkoutGroupId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_EXISTED));

        return paymentMapper.toPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentByUser(){
        var user = currentUser.findCurrentUser();
        return paymentRepository.findAllByPayerIdOrderByCreatedAtDesc(user.getId()).stream().map(paymentMapper::toPaymentResponse).toList();
    }

    @IsAdmin
    @Transactional(readOnly = true)
    public Slice<PaymentResponse> listPending(Pageable pageable) {
        return paymentRepository.findByMethodAndStatusOrderByCreatedAtAsc(
                PaymentMethod.ONLINE_BANKING, PaymentStatus.PENDING, pageable)
                .map(paymentMapper::toPaymentResponse);
    }

    @IsAdmin
    @Transactional
    public PaymentResponse confirmTransfer(UUID paymentId, ConfirmPaymentRequest request) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_EXISTED));
        String reference = request.bankReference().trim();
        if (payment.getMethod() != PaymentMethod.ONLINE_BANKING
                || payment.getAmount().compareTo(request.amount()) != 0) {
            throw new AppException(ErrorCode.PAYMENT_CONFIRMATION_CONFLICT);
        }

        PaymentTransaction receipt = payment.getPaymentTransaction();
        if (payment.getStatus() == PaymentStatus.SUCCESSFUL && receipt != null
                && "MANUAL".equals(receipt.getProvider()) && reference.equals(receipt.getExternalId())) {
            return paymentMapper.toPaymentResponse(payment);
        }
        if (payment.getStatus() != PaymentStatus.PENDING
                || paymentTransactionRepository.existsByProviderAndExternalId("MANUAL", reference)) {
            throw new AppException(ErrorCode.PAYMENT_CONFIRMATION_CONFLICT);
        }

        var checkout = checkoutGroupRepository.findByIdForUpdate(payment.getCheckoutGroupId())
                .orElseThrow(() -> new AppException(ErrorCode.CHECKOUT_GROUP_NOT_EXISTED));
        var orders = checkout.getOrders();
        BigDecimal orderTotal = orders.stream().map(order -> order.getTotalAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (orders.isEmpty() || orderTotal.compareTo(payment.getAmount()) != 0
                || orders.stream().anyMatch(order -> order.getStatus() != OrderStatus.PENDING_PAYMENT
                        || !payment.getPayerId().equals(order.getBuyerId()))) {
            throw new AppException(ErrorCode.PAYMENT_CONFIRMATION_CONFLICT);
        }
        checkout.markPaid();
        orders.stream().flatMap(order -> Stream.of(order.getBuyerId(), order.getSellerId()))
                .distinct().sorted().forEach(walletService::ensureWalletForUser);

        var now = Instant.now();
        var adminId = currentUser.findCurrentUser().getId();
        var bankReceipt = PaymentTransaction.builder()
                .provider("MANUAL").externalId(reference).gateway("BANK_TRANSFER")
                .transactionDate(now).accountNumber(bankAccount).content("Confirmed by admin " + adminId)
                .paymentCode(payment.getPaymentCode()).transferType(TransferType.IN).transferAmount(request.amount())
                .referenceCode(reference).matchStatus(MatchStatus.MATCHED).build();
        try {
            paymentTransactionRepository.saveAndFlush(bankReceipt);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.PAYMENT_CONFIRMATION_CONFLICT);
        }
        payment.setPaymentTransaction(bankReceipt);
        payment.setStatus(PaymentStatus.SUCCESSFUL);
        payment.setPaidAt(now);
        for (var order : orders) {
            escrowService.recordReceivedPayment(order);
            order.setPaidAt(now);
            order.setConfirmedAt(now);
            order.setStatus(OrderStatus.CONFIRMED);
        }
        return paymentMapper.toPaymentResponse(payment);
    }

    private void usersCanView(Payment payment, UUID userId){
        if(!payment.getPayerId().equals(userId))
            throw new AppException(ErrorCode.PAYMENT_NOT_EXISTED);
    }

    private String createQrBankUrl(String paymentCode, BigDecimal amount){
        return "https://vietqr.app/img"
                + "?acc=" + URLEncoder.encode(bankAccount, StandardCharsets.UTF_8)
                + "&bank=" + URLEncoder.encode(bankName, StandardCharsets.UTF_8)
                + "&amount=" + amount.setScale(0, RoundingMode.UNNECESSARY).toPlainString()
                + "&des=" + URLEncoder.encode(paymentCode, StandardCharsets.UTF_8);
    }
    private String generatePaymentCode(){
        return "MM" + generate(8);
    }
}
