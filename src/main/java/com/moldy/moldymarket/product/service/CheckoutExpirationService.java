package com.moldy.moldymarket.product.service;

import com.moldy.moldymarket.order.entity.CheckoutGroup;
import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CheckoutExpirationService {

    private final CheckoutGroupRepository checkoutGroupRepository;
    private final ProductService productService;

    @Transactional
    public void expireExpiredCheckouts(){
        List<CheckoutGroup> checkoutGroups = checkoutGroupRepository.findExpiredCheckouts(Instant.now(),
                Set.of(
                        CheckoutGroupStatus.PENDING_PAYMENT
                ));
        for(var cg : checkoutGroups){
            cg.markExpired();
            for(var order : cg.getOrders()){
                order.markExpired();
                for(var oderItem : order.getOrderItems()){
                    productService.releaseProduct(oderItem.getProductId(), cg.getId());
                }
            }
        }

    }
}
