package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.dto.request.OrderCreationRequest;
import com.moldy.moldymarket.order.dto.response.OrderResponse;
import com.moldy.moldymarket.order.entity.OrderItem;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.mapper.OrderMapper;
import com.moldy.moldymarket.order.repository.OrderItemRepository;
import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.order.repository.OrderStatusHistoryRepository;
import com.moldy.moldymarket.product.entity.ProductStatus;
import com.moldy.moldymarket.product.repository.ProductRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderService {
    OrderRepository orderRepository;
    OrderStatusHistoryRepository orderStatusHistoryRepository;
    CurrentUser currentUser;
    OrderMapper orderMapper;
    ProductRepository productRepository;
    OrderItemRepository orderItemRepository;

    static String PREFIX = "ORDMM";
    static String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    static int RANDOM_LENGTH = 5;
    static SecureRandom RANDOM = new SecureRandom();


    @Transactional
    public OrderResponse createOrder(OrderCreationRequest request){
        var user = currentUser.findCurrentUser(); //get current user
        //calculate total amount
        var orderItems = request.orderItems();
        if(orderItems == null || orderItems.isEmpty()) throw new AppException(ErrorCode.ORDER_ITEMS_LIST_NOT_EXISTED);
        Map<UUID, Integer> productList = new HashMap<>();
        for(var item : orderItems){
            if(item.productId() == null || item.quantity() == null || item.quantity() <= 0)
                throw new AppException(ErrorCode.INVALID_ORDER_ITEMS);

            if(productList.putIfAbsent(item.productId(), item.quantity()) != null){
                throw new AppException(ErrorCode.DUPLICATE_PRODUCT);
            }
        }

        var products = productRepository.findAllByIds(new ArrayList<>(productList.keySet()));
        if(products.size() != productList.size()) throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        var order = orderMapper.toOrder(request);
        BigDecimal itemsTotal = BigDecimal.ZERO;
        for(var product : products){

            if(!product.getStatus().equals(ProductStatus.ACTIVE)) throw new AppException(ErrorCode.PRODUCT_NOT_ACTIVE);
            //todo: validate if product stock is sufficient or not
//            if(productList.get(product.getId()).equals())

            Integer quantity = productList.get(product.getId());

            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));

            OrderItem orderItem = OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getTitle())
                    .unitPrice(product.getPrice())
                    .quantity(quantity)
                    .imageUrl( product.getImages() != null
                            && !product.getImages().isEmpty()
                            ? product.getImages().getFirst()
                            : null)
                    .build();
            order.addOrderItem(orderItem);
            orderItemRepository.save(orderItem);

            itemsTotal = itemsTotal.add(subtotal);
        }

        BigDecimal shippingFee = BigDecimal.ZERO;
        BigDecimal shopDiscount = BigDecimal.ZERO;
        BigDecimal platformDiscount = BigDecimal.ZERO;

        BigDecimal totalAmount = itemsTotal.add(shippingFee).subtract(shopDiscount).subtract(platformDiscount);
        if(totalAmount.compareTo(BigDecimal.ZERO) < 0){
            throw new AppException(ErrorCode.INVALID_ORDER_AMOUNT);
        }
        //create order
        order.setBuyerId(user.getId());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setOrderCode(orderCodeGenerator());
        order.setTotalAmount(totalAmount);
        order.setPlatformDiscount(platformDiscount);
        order.setShopDiscount(shopDiscount);
        order.setShippingFee(shippingFee);



        return orderMapper.toOrderResponse(orderRepository.save(order));
    }


    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrderByBuyerId(){
        var user = currentUser.findCurrentUser();
        var orders = orderRepository.findAllByBuyerId(user.getId());
        if(orders.isEmpty())
            throw new AppException(ErrorCode.ORDER_LIST_NOT_FOUND);
        return orders.stream().map(orderMapper::toOrderResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId){
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));
        return orderMapper.toOrderResponse(order);
    }

    private String orderCodeGenerator(){
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));

        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < RANDOM_LENGTH; i++){
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return PREFIX + "-" + date + "-" + sb;
    }

    //todo: shipping simulator
}
