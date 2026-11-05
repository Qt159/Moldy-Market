package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.domain.OrderGroupKey;
import com.moldy.moldymarket.order.dto.request.CheckoutGroupRequest;
import com.moldy.moldymarket.order.dto.request.OrderItemRequest;
import com.moldy.moldymarket.order.dto.response.CheckoutResponse;
import com.moldy.moldymarket.order.entity.CheckoutGroup;
import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.mapper.CheckoutMapper;
import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.product.entity.Product;
import com.moldy.moldymarket.product.entity.ProductStatus;
import com.moldy.moldymarket.product.repository.ProductRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CheckoutGroupService {

    CheckoutGroupRepository checkoutGroupRepository;
    OrderRepository orderRepository;
    ProductRepository productRepository;
    CurrentUser currentUser;
    CheckoutMapper checkoutMapper;

    static String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    static SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public CheckoutResponse createCheckoutGroup(CheckoutGroupRequest request){
        var user = currentUser.findCurrentUser(); //get current user

        //map request item to order items
        Map<UUID, Integer> requestedItems = request.orderItems()
                .stream()
                .collect(Collectors.toMap(
                        OrderItemRequest::productId,
                        OrderItemRequest::quantity,
                        Integer::sum
                ));

        Set<Product> products = productRepository.findAllByIds(requestedItems.keySet().stream().toList());

        if(products.size() != requestedItems.size()){
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        //validate products
        for(var p : products){
            if(!p.getStatus().equals(ProductStatus.ACTIVE)){
                throw new AppException(ErrorCode.PRODUCT_NOT_ACTIVE);
            }
            if(p.getSellerId() == null) {
                throw new AppException(ErrorCode.INVALID_PRODUCT);
            }
        }
        Map<OrderGroupKey, List<Product>> groupedProducts = products.stream()
                .collect(Collectors.groupingBy(
                        product -> new OrderGroupKey(product.getSellerId(),
                                product.getStoreId())
                ));

        //generate CheckoutGroup info
        Instant now = Instant.now();
        String paymentCode = generatePaymentCode();

        Instant expiredAt = now.plus(15, ChronoUnit.MINUTES);

        //calculate total
        BigDecimal checkoutTotal = BigDecimal.ZERO;

        for(List<Product> group : groupedProducts.values()){
            for(Product product : group){
                int quantity = requestedItems.get(product.getId());

                BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));

                checkoutTotal = checkoutTotal.add(subtotal);
            }
        }

        //create CheckoutGroup
        CheckoutGroup checkoutGroup = CheckoutGroup.builder()
                .buyerId(user.getId())
                .totalAmount(checkoutTotal)
                .platformDiscount(request.platformVoucherId())
                .paymentCode(paymentCode)
                .expiredAt(expiredAt)
                .build();

        checkoutGroupRepository.save(checkoutGroup);

        //create orders
        List<Order> orders = new ArrayList<>();
        for(var entry : groupedProducts.entrySet()){
            OrderGroupKey key = entry.getKey();

            List<Product> groupProducts = entry.getValue();

            Order order = createOrder(checkoutGroup, user.getId(), key, groupProducts, requestedItems, request);

            orders.add(order);
        }
        orderRepository.saveAll(orders);

        return checkoutMapper.toResponse(checkoutGroup);
    }

    private Order createOrder(CheckoutGroup checkoutGroup, UUID buyerId, OrderGroupKey key, List<Product> products, Map<UUID, Integer> quantities, CheckoutGroupRequest request){
        BigDecimal itemsTotal = BigDecimal.ZERO;

        Order order = Order.builder()
                .checkoutGroupId(checkoutGroup.getId())
                .buyerId(buyerId)
                .sellerId(key.sellerId())
                .storeId(key.storeId())
                .orderCode(generateOrderCode())
                .status(OrderStatus.PENDING_PAYMENT)
                .receiverName(request.receiverName())
                .receiverPhone(request.phoneNumber())
                .shippingAddress(request.shippingAddress())
                .note(request.note())
                .shippingFee(BigDecimal.ZERO)
                .build();

    }
    private String generatePaymentCode(){

    }
    private String generateOrderCode(){

    }


}
