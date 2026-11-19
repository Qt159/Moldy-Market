package com.moldy.moldymarket.payment.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import com.moldy.moldymarket.payment.dto.response.PaymentResponse;
import com.moldy.moldymarket.payment.entity.Payment;
import com.moldy.moldymarket.payment.enums.PaymentMethod;
import com.moldy.moldymarket.payment.mapper.PaymentMapper;
import com.moldy.moldymarket.payment.repository.PaymentRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

    @NonFinal
    @Value("${sePay.bank.account}")
    String bankAccount;

    @NonFinal
    @Value("${sePay.bank.name}")
    String bankName;

    @Transactional
    public PaymentResponse createPayment(UUID checkoutGroupId, PaymentMethod method){
        var checkoutGroup = checkoutGroupRepository.findById(checkoutGroupId)
                .orElseThrow(() -> new AppException(ErrorCode.CHECKOUT_GROUP_NOT_EXISTED));

        String qrBankUrl = null, paymentCode = null;
        if(method == PaymentMethod.ONLINE_BANKING){
            if(!checkoutGroup.getStatus().equals(CheckoutGroupStatus.PENDING_PAYMENT)) throw new AppException(ErrorCode.CHECKOUT_GROUP_INVALID_STATUS);
            paymentCode = generatePaymentCode();
            qrBankUrl = createQrBankUrl(paymentCode, checkoutGroup.getTotalAmount());
        }
        //prevent retry
        var existedPayment = paymentRepository.findByCheckoutGroupId(checkoutGroup.getId());
        if(existedPayment.isPresent()) return paymentMapper.toPaymentResponse(existedPayment.get());


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
        var user = currentUser.findCurrentUser();
        var payment = paymentRepository.findByCheckoutGroupId(checkoutGroupId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_EXISTED));
        usersCanView(payment, user.getId());
        return paymentMapper.toPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentByUser(){
        var user = currentUser.findCurrentUser();
        return paymentRepository.findAllByPayerIdOrderByCreatedAtDesc(user.getId()).stream().map(paymentMapper::toPaymentResponse).toList();
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

    @Transactional
    public int expirePending(UUID checkoutGroupId){
        return paymentRepository.expirePending(checkoutGroupId);
    }

    private void usersCanView(Payment payment, UUID userId){
        if(!payment.getPayerId().equals(userId))
            throw new AppException(ErrorCode.PAYMENT_NOT_EXISTED);
    }

    private String createQrBankUrl(String paymentCode, BigDecimal amount){
        return "https://vietqr.app/img"
                + "?acc=" + URLEncoder.encode(bankAccount, StandardCharsets.UTF_8)
                + "&bank=" + URLEncoder.encode(bankName, StandardCharsets.UTF_8)
                + "&amount=" + toWholeVnd(amount).toPlainString()
                + "&des=" + URLEncoder.encode(paymentCode, StandardCharsets.UTF_8);
    }
    private BigDecimal toWholeVnd(BigDecimal amount){
        try {
            return amount.setScale(0, RoundingMode.UNNECESSARY);   //throws exception if amount is 15000.050
        } catch (ArithmeticException e) {
            throw new AppException(ErrorCode.INVALID_PAYMENT_AMOUNT);
        }
    }
    private String generatePaymentCode(){
        return "MM" + generate(8);
    }
}
