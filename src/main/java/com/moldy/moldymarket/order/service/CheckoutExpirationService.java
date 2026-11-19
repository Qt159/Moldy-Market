package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import com.moldy.moldymarket.payment.service.PaymentService;
import com.moldy.moldymarket.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckoutExpirationService {

    private final ProductService productService;
    private final PaymentService paymentService;
    private final CheckoutGroupRepository checkoutGroupRepository;

    @Transactional
    public void expireOne(UUID checkoutGroupId){

        if(paymentService.expirePending(checkoutGroupId) == 0) return;
        var cg = checkoutGroupRepository.findByIdForUpdate(checkoutGroupId).orElse(null);
        if (cg == null || cg.getStatus() != CheckoutGroupStatus.PENDING_PAYMENT) return;

        cg.markExpired();
        for(var order : cg.getOrders()){
            order.markExpired();
            for(var oderItem : order.getOrderItems()){
                productService.releaseProduct(oderItem.getProductId(), cg.getId());
            }
        }
    }
}
