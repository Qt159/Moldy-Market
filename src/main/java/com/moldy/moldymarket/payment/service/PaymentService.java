package com.moldy.moldymarket.payment.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
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
    OrderRepository orderRepository;

    @NonFinal
    @Value("${sePay.bank.account}")
    String bankAccount;

    @NonFinal
    @Value("${sePay.bank.name}")
    String bankName;

    @Transactional
    public PaymentResponse createPayment(UUID checkoutGroupId, PaymentMethod method){
        var checkoutGroup = checkoutGroupRepository.findById(checkoutGroupId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        CheckoutGroupStatus expected = method == PaymentMethod.COD
                ? CheckoutGroupStatus.PAY_ON_DELIVERY : CheckoutGroupStatus.PENDING_PAYMENT;
        if(checkoutGroup.getStatus() != expected) throw new AppException(ErrorCode.CHECKOUT_GROUP_INVALID_STATUS);
        //prevent retry
        var existedPayment = paymentRepository.findByCheckoutGroupId(checkoutGroup.getId());
        if(existedPayment.isPresent()) return paymentMapper.toPaymentResponse(existedPayment.get());

        String qrBankUrl = null, paymentCode = null;
        if(method == PaymentMethod.ONLINE_BANKING){
            paymentCode = generatePaymentCode();
            qrBankUrl = createQrBankUrl(paymentCode, checkoutGroup.getTotalAmount());
        }

        Payment payment = Payment.builder()
                .checkoutGroupId(checkoutGroup.getId())
                .payerId(checkoutGroup.getBuyerId())
                .amount(checkoutGroup.getTotalAmount())
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

        usersCanView(payment, currentUser.findCurrentUser().getId());
        return paymentMapper.toPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentByUser(){
        var user = currentUser.findCurrentUser();
        return paymentRepository.findAllByPayerIdOrderByCreatedAtDesc(user.getId()).stream().map(paymentMapper::toPaymentResponse).toList();
    }

    @Transactional
    public PaymentResponse markPaid(UUID checkoutGroupId, UUID buyerId){
        return null;
    }



//    @Transactional(readOnly = true)
//    public PaymentResponse getPaymentByOrder( UUID orderId){
//        var user = currentUser.findCurrentUser();
//        var order = orderRepository.findById(orderId)
//                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));
//        var payment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
//                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_EXISTED));
//
//        usersCanView(payment, user.getId());
//        return paymentMapper.toPaymentResponse(payment);
//    }

    private void usersCanView(Payment payment, UUID userId){
        if(!payment.getPayerId().equals(userId))
            throw new AppException(ErrorCode.PAYMENT_NOT_EXISTED);
    }

    private String createQrBankUrl(String paymentCode, BigDecimal amount){
        BigDecimal wholeVnd;
        try {
            wholeVnd = amount.setScale(0, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException invalidAmount) {
            throw new AppException(ErrorCode.INVALID_ORDER_AMOUNT);
        }
        return "https://vietqr.app/img"
                + "?acc=" + URLEncoder.encode(bankAccount, StandardCharsets.UTF_8)
                + "&bank=" + URLEncoder.encode(bankName, StandardCharsets.UTF_8)
                + "&amount=" + wholeVnd.toPlainString()
                + "&des=" + URLEncoder.encode(paymentCode, StandardCharsets.UTF_8);
    }
    private String generatePaymentCode(){
        return "MM" + generate(8);
    }
}
