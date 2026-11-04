package com.moldy.moldymarket.payment.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.repository.OrderRepository;
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
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentService {
    PaymentRepository paymentRepository;
    PaymentMapper paymentMapper;
    OrderRepository orderRepository;
    UserRepository userRepository;

    static String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    static SecureRandom RANDOM = new SecureRandom();

    @NonFinal
    @Value("${sePay.bank.account}")
    String bankAccount;

    @NonFinal
    @Value("${sePay.bank.name}")
    String bankName;

    @Transactional
    public PaymentResponse createPayment(Authentication authentication, UUID orderId){
        var user = currentUser(authentication);
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        if(!order.getBuyerId().equals(user.getId())) throw new AppException(ErrorCode.ORDER_NOT_EXISTED);

        if(!order.getStatus().equals(OrderStatus.PENDING_PAYMENT) || order.getExpiredAt() == null || order.getExpiredAt().isBefore(Instant.now()))
            throw new AppException(ErrorCode.INVALID_ORDER_STATE);

        //prevent retry
        var existedPayment = paymentRepository.findByOrderIdAndStatus(order.getId(), PaymentStatus.PENDING);
        if(existedPayment.isPresent()) return paymentMapper.toPaymentResponse(existedPayment.get());

        String code = generatePaymentCode();
        BigDecimal amount = order.getTotalAmount();

        Payment payment = Payment.builder()
                .orderId(order.getId())
                .payerId(user.getId())
                .amount(amount)
                .method(PaymentMethod.ONLINE_BANKING)
                .paymentCode(code)
                .qrBankUrl(createQrBankUrl(code, amount))
                .expiresAt(order.getExpiredAt())
                .build();
        return paymentMapper.toPaymentResponse(paymentRepository.save(payment));


    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Authentication authentication, UUID id){
        var user = currentUser(authentication);
        var payment = paymentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_EXISTED));
        usersCanView(payment, user.getId(), isAdmin(authentication));
        return paymentMapper.toPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentByUser(Authentication authentication){
        var user = currentUser(authentication);
        return paymentRepository.findAllByPayerIdOrderByCreatedAtDesc(user.getId()).stream().map(paymentMapper::toPaymentResponse).toList();
    }



    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrder(Authentication authentication, UUID orderId){
        var user = currentUser(authentication);
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));
        var payment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_EXISTED));

        usersCanView(payment, user.getId(), isAdmin(authentication));
        return paymentMapper.toPaymentResponse(payment);
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }
    private boolean isAdmin(Authentication authentication){
        return authentication.getAuthorities().stream()
                .anyMatch(role -> Objects.equals(role.getAuthority(), "ROLE_ADMIN"));
    }
    private void usersCanView(Payment payment, UUID userId, boolean isAdmin){
        if(!isAdmin && !payment.getPayerId().equals(userId))
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
        StringBuilder sb = new StringBuilder("MM");
        for(int i = 0; i < 8; i++){
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
