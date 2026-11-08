package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.domain.OrderGroupKey;
import com.moldy.moldymarket.order.dto.request.CheckoutGroupRequest;
import com.moldy.moldymarket.order.dto.response.CheckoutResponse;
import com.moldy.moldymarket.order.dto.response.OrderSummaryResponse;
import com.moldy.moldymarket.order.entity.CheckoutGroup;
import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.entity.OrderItem;
import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.mapper.CheckoutMapper;
import com.moldy.moldymarket.order.mapper.OrderMapper;
import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.payment.dto.response.PaymentResponse;
import com.moldy.moldymarket.payment.enums.PaymentMethod;
import com.moldy.moldymarket.payment.service.PaymentService;
import com.moldy.moldymarket.product.entity.Product;
import com.moldy.moldymarket.product.entity.ProductStatus;
import com.moldy.moldymarket.product.repository.ProductRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import static com.moldy.moldymarket.common.utils.CodeGenerator.generate;


@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CheckoutGroupService {

    CheckoutGroupRepository checkoutGroupRepository;
    OrderRepository orderRepository;
    ProductRepository productRepository;
    CurrentUser currentUser;
    CheckoutMapper checkoutMapper;
    PaymentService paymentService;
    OrderMapper orderMapper;

    @Transactional
    public CheckoutResponse createCheckoutGroup(CheckoutGroupRequest request){
        var user = currentUser.findCurrentUser(); //get current user

        //map request item to order items
        //duplicate product and increase quantity
//        Map<UUID, Integer> requestedItems = request.orderItems()
//                .stream()
//                .collect(Collectors.toMap(
//                        OrderItemRequest::productId,
//                        OrderItemRequest::quantity,
//                        Integer::sum
//                ));
        Map<UUID, Integer> requestedItems = new HashMap<>();
        for(var item : request.orderItems()){
            if(item.productId() == null || item.quantity() == null || item.quantity() <= 0)
                throw new AppException(ErrorCode.INVALID_ORDER_ITEMS);

            if(requestedItems.putIfAbsent(item.productId(), item.quantity()) != null){
                throw new AppException(ErrorCode.DUPLICATE_PRODUCT);
            }
        }


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
        //expired at
        Instant expiredAt = null;
        if(request.method() == PaymentMethod.ONLINE_BANKING) expiredAt = Instant.now().plus(15, ChronoUnit.MINUTES);

        //create CheckoutGroup
        CheckoutGroup checkoutGroup = CheckoutGroup.builder()
                .buyerId(user.getId())
                .totalAmount(BigDecimal.ZERO)
                //.platformDiscount(request.platformVoucherId())
                .expiredAt(expiredAt)
                .build();

        checkoutGroupRepository.save(checkoutGroup);

        //create orders
        List<Order> orders = new ArrayList<>();
        for(var entry : groupedProducts.entrySet()){
            Order order = createOrder(
                    checkoutGroup,
                    user.getId(),
                    entry.getKey(),
                    entry.getValue(),
                    requestedItems,
                    request
            );

            checkoutGroup.addOrder(order);
            orders.add(order);
        }

        //calculate checkout total from orders
        BigDecimal checkoutTotal = orders.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        checkoutGroup.setTotalAmount(checkoutTotal);
        checkoutGroup.setOrders(orders);

        orderRepository.saveAll(orders);

        List<OrderSummaryResponse> orderSummaries = orders.stream().map(orderMapper::toSummary).toList();

        //create payment
        PaymentResponse paymentResponse = paymentService.createPayment(checkoutGroup.getId(), user.getId(), checkoutTotal, request.method());

        return CheckoutResponse.builder()
                .id(checkoutGroup.getId())
                .totalAmount(checkoutTotal)
                .expiredAt(expiredAt)
                .orders(orderSummaries)
                .status(CheckoutGroupStatus.PENDING_PAYMENT)
                .paymentResponse(paymentResponse)
                .build();
    }

    @Transactional(readOnly = true)
    public CheckoutResponse getCheckoutById(UUID id){
        var user = currentUser.findCurrentUser();
        var checkoutGroup = checkoutGroupRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CHECKOUT_GROUP_NOT_EXISTED));

        if(!user.getId().equals(checkoutGroup.getBuyerId())) throw new AppException(ErrorCode.NOT_ALLOWED_ACCESS_CHECKOUT_GROUP);

        PaymentResponse paymentResponse = paymentService.getPaymentByCheckoutGroupId(id);
        return CheckoutResponse.builder()
                .id(checkoutGroup.getId())
                .totalAmount(checkoutGroup.getTotalAmount())
                .expiredAt(checkoutGroup.getExpiredAt())
                .orders(checkoutGroup.getOrders().stream()
                        .map(orderMapper::toSummary).toList())
                .status(checkoutGroup.getStatus())
                .paymentResponse(paymentResponse)
                .build();
    }


    private Order createOrder(CheckoutGroup checkoutGroup, UUID buyerId, OrderGroupKey key, List<Product> products, Map<UUID, Integer> quantities, CheckoutGroupRequest request){
        BigDecimal totalAmount = BigDecimal.ZERO;

        Order order = Order.builder()
                .checkoutGroup(checkoutGroup)
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
                .totalAmount(totalAmount)
                .build();

        //calculate total amount of this order
        for(var p : products){
            int quantity = quantities.get(p.getId());

            //todo: subtract quantity, reverse stock

            BigDecimal unitPrice = p.getPrice();

            BigDecimal subtotal = unitPrice.multiply(
                    BigDecimal.valueOf(quantity)
            );

            OrderItem item = OrderItem.builder()
                    .productId(p.getId())
                    .productName(p.getTitle())
                    .unitPrice(p.getPrice())
                    .quantity(quantity)
                    .imageUrl(p.getImages() != null
                            && !p.getImages().isEmpty()
                            ? p.getImages().get(0)
                            : null)
                    .build();
            order.addOrderItem(item);
            totalAmount = totalAmount.add(subtotal);
        }

        //todo: substract shopdiscount
//        totalAmount = totalAmount.subtract(BigDecimal.valueOf(shopDiscount));

        order.setTotalAmount(totalAmount);

        return order;
    }
    private String generateOrderCode(){
        return "ORD-" + generate(5);
    }


}
