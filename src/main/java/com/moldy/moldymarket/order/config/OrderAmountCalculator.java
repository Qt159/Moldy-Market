package com.moldy.moldymarket.order.config;

import com.moldy.moldymarket.order.dto.response.OrderAmounts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class OrderAmountCalculator {
    @Value("${app.settlement.platform-rate}")
    BigDecimal platformRate;

    public OrderAmounts calculate(BigDecimal itemsSubtotal, BigDecimal shopDiscount, BigDecimal platformDiscount, BigDecimal shippingFee){
        BigDecimal base = itemsSubtotal.subtract(shopDiscount);
        BigDecimal commission = base.multiply(platformRate).setScale(0, RoundingMode.DOWN);

        BigDecimal buyerPays = base.subtract(platformDiscount).add(shippingFee);
        BigDecimal sellerReceives = base.subtract(commission);
        return new OrderAmounts(buyerPays, sellerReceives, commission);
    }
}
