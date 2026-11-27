package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.offer.entity.Offer;
import com.moldy.moldymarket.offer.enums.OfferStatus;
import com.moldy.moldymarket.offer.repository.OfferRepository;
import com.moldy.moldymarket.order.domain.OrderGroupKey;
import com.moldy.moldymarket.order.dto.request.CheckoutGroupRequest;
import com.moldy.moldymarket.order.dto.response.CheckoutResponse;
import com.moldy.moldymarket.order.dto.response.OrderSummaryResponse;
import com.moldy.moldymarket.order.entity.CheckoutGroup;
import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.entity.OrderItem;
import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.event.OrderCreatedEvent;
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
import com.moldy.moldymarket.store.service.StoreAccessGuard;
import com.moldy.moldymarket.voucher.model.Voucher;
import com.moldy.moldymarket.voucher.model.VoucherIssuer;
import com.moldy.moldymarket.voucher.service.VoucherService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    StoreAccessGuard storeAccessGuard;
    ApplicationEventPublisher applicationEventPublisher;
    VoucherService voucherService;
    OfferRepository offerRepository;

    @Transactional
    public CheckoutResponse createCheckoutGroup(CheckoutGroupRequest request){
        var user = currentUser.findCurrentUser(); //get current user
        Instant now = Instant.now();

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
            if(item.productId() == null || item.quantity() == null || item.quantity() != 1)
                throw new AppException(ErrorCode.INVALID_ORDER_ITEMS);

            if(requestedItems.putIfAbsent(item.productId(), 1) != null){
                throw new AppException(ErrorCode.DUPLICATE_PRODUCT);
            }
        }


        List<Product> products = new ArrayList<>();
        // Lock in the same order for every buyer before reading status, price or reservation.
        for (UUID productId : requestedItems.keySet().stream().sorted().toList()) {
            products.add(productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND)));
        }

        //validate products
        for (var product : products) {
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new AppException(ErrorCode.PRODUCT_NOT_ACTIVE);
            }

            if (product.isReservedAt(now)) {
                throw new AppException(ErrorCode.PRODUCT_RESERVED);
            }

            if (product.getSellerId() == null) {
                throw new AppException(ErrorCode.INVALID_PRODUCT);
            }

            boolean isSeller = product.getSellerId().equals(user.getId());

            boolean isStoreMember = product.getStoreId() != null
                    && storeAccessGuard.isStoreMember(
                            product.getStoreId(),
                            user.getId()
            );

            if (isSeller || isStoreMember) {
                throw new AppException(ErrorCode.CHECKOUT_OWN_PRODUCT);
            }
        }

        Map<OrderGroupKey, List<Product>> groupedProducts = products.stream()
                .collect(Collectors.groupingBy(
                        product -> new OrderGroupKey(product.getSellerId(),
                                product.getStoreId())
                ));
        //expired at
        Instant expiredAt = null;
        if(request.method() == PaymentMethod.ONLINE_BANKING) expiredAt = now.plus(15, ChronoUnit.MINUTES);

        //create CheckoutGroup
        CheckoutGroup checkoutGroup = CheckoutGroup.builder()
                .buyerId(user.getId())
                .totalAmount(BigDecimal.ZERO)
                //.platformDiscount(request.platformVoucherId())
                .expiredAt(expiredAt)
                .build();

        if (request.method() == PaymentMethod.COD) checkoutGroup.setStatus(CheckoutGroupStatus.PAY_ON_DELIVERY);
        checkoutGroupRepository.save(checkoutGroup);

        Map<UUID, Offer> acceptedOffersByProductId = new HashMap<>();
        for (Product product : products) {
            offerRepository.findFirstByBuyerIdAndProductIdAndStatusAndCheckoutGroupIdIsNullAndAcceptedExpiresAtAfter(
                    user.getId(), product.getId(), OfferStatus.ACCEPTED, now)
                    .ifPresent(offer -> {
                        offer.linkCheckoutGroup(checkoutGroup.getId(), now);
                        acceptedOffersByProductId.put(product.getId(), offer);
                    });
        }

        for (Product product : products) {
            product.reserveForCheckout(checkoutGroup.getId(), expiredAt, now);
        }

        //create orders
        List<Order> orders = new ArrayList<>();
        for(var entry : groupedProducts.entrySet()){
            Order order = createOrder(
                    checkoutGroup,
                    user.getId(),
                    entry.getKey(),
                    entry.getValue(),
                    requestedItems,
                    request,
                    acceptedOffersByProductId
            );

            checkoutGroup.addOrder(order);
            orders.add(order);
        }

        if (request.voucherCode() != null && !request.voucherCode().isBlank() && !acceptedOffersByProductId.isEmpty()) {
            throw new AppException(ErrorCode.OFFER_VOUCHER_NOT_ALLOWED);
        }

        BigDecimal appliedDiscount = applyVoucherIfPresent(checkoutGroup, orders, request);

        //calculate checkout total from orders
        BigDecimal checkoutTotal = orders.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        checkoutGroup.setTotalAmount(checkoutTotal);
        checkoutGroup.setDiscountAmount(appliedDiscount);
        checkoutGroup.setOrders(orders);

        orderRepository.saveAll(orders);
        orders.forEach(order -> applicationEventPublisher.publishEvent(new OrderCreatedEvent(
                order.getId(), order.getBuyerId(), order.getSellerId(), order.getOrderCode())));

        List<OrderSummaryResponse> orderSummaries = orders.stream().map(orderMapper::toSummary).toList();

        //create payment
        PaymentResponse paymentResponse = paymentService.createPayment(checkoutGroup.getId(), request.method());

        return CheckoutResponse.builder()
                .id(checkoutGroup.getId())
                .totalAmount(checkoutTotal)
                .discountAmount(appliedDiscount)
                .voucherCode(checkoutGroup.getVoucherCode())
                .expiredAt(expiredAt)
                .orders(orderSummaries)
                .status(checkoutGroup.getStatus())
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
                .discountAmount(checkoutGroup.getDiscountAmount() == null ? BigDecimal.ZERO : checkoutGroup.getDiscountAmount())
                .voucherCode(checkoutGroup.getVoucherCode())
                .expiredAt(checkoutGroup.getExpiredAt())
                .orders(checkoutGroup.getOrders().stream()
                        .map(orderMapper::toSummary).toList())
                .status(checkoutGroup.getStatus())
                .paymentResponse(paymentResponse)
                .build();
    }

    private BigDecimal applyVoucherIfPresent(CheckoutGroup checkoutGroup, List<Order> orders, CheckoutGroupRequest request) {
        if (request.voucherCode() == null || request.voucherCode().isBlank()) {
            checkoutGroup.setPlatformDiscount(BigDecimal.ZERO);
            return BigDecimal.ZERO;
        }
        Voucher voucher = voucherService.validateAndGet(request.voucherCode());
        boolean isPlatform = voucher.getIssuer() == VoucherIssuer.PLATFORM;
        List<Order> eligibleOrders;
        if (isPlatform) {
            eligibleOrders = orders;
        } else {
            UUID voucherStoreId = voucher.getStore() == null ? null : voucher.getStore().getId();
            if (voucherStoreId == null) {
                throw new AppException(ErrorCode.VOUCHER_NOT_APPLICABLE);
            }
            eligibleOrders = orders.stream()
                    .filter(order -> voucherStoreId.equals(order.getStoreId()))
                    .toList();
            if (eligibleOrders.isEmpty()) {
                throw new AppException(ErrorCode.VOUCHER_STORE_MISMATCH);
            }
        }
        BigDecimal eligibleSubtotal = eligibleOrders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = voucherService.calculateDiscount(voucher, eligibleSubtotal)
                .setScale(0, RoundingMode.DOWN);
        distributeDiscount(eligibleOrders, discount, isPlatform);
        boolean reserveForOnline = request.method() == PaymentMethod.ONLINE_BANKING;
        voucherService.consumeVoucher(voucher, reserveForOnline);
        checkoutGroup.setVoucherId(voucher.getId());
        checkoutGroup.setVoucherCode(voucher.getCode());
        checkoutGroup.setPlatformDiscount(isPlatform ? discount : BigDecimal.ZERO);
        return discount;
    }

    private void distributeDiscount(List<Order> targetOrders, BigDecimal totalDiscount, boolean isPlatform) {
        if (totalDiscount.signum() <= 0 || targetOrders.isEmpty()) {
            return;
        }
        if (targetOrders.size() == 1) {
            Order order = targetOrders.get(0);
            BigDecimal applied = totalDiscount.min(order.getTotalAmount());
            if (isPlatform) {
                order.setPlatformDiscount(applied);
            } else {
                order.setShopDiscount(applied);
            }
            order.setTotalAmount(order.getTotalAmount().subtract(applied));
            return;
        }
        BigDecimal eligibleTotal = targetOrders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (eligibleTotal.signum() <= 0) {
            return;
        }
        BigDecimal remaining = totalDiscount;
        for (int i = 0; i < targetOrders.size(); i++) {
            Order order = targetOrders.get(i);
            BigDecimal share = (i == targetOrders.size() - 1)
                    ? remaining.min(order.getTotalAmount())
                    : totalDiscount.multiply(order.getTotalAmount())
                        .divide(eligibleTotal, 0, RoundingMode.DOWN)
                        .min(remaining)
                        .min(order.getTotalAmount());
            remaining = remaining.subtract(share);
            if (isPlatform) {
                order.setPlatformDiscount(share);
            } else {
                order.setShopDiscount(share);
            }
            order.setTotalAmount(order.getTotalAmount().subtract(share));
        }
    }


    private Order createOrder(CheckoutGroup checkoutGroup, UUID buyerId, OrderGroupKey key,
                              List<Product> products, Map<UUID, Integer> quantities, CheckoutGroupRequest request,
                              Map<UUID, Offer> acceptedOffersByProductId){
        BigDecimal totalAmount = BigDecimal.ZERO;

        Order order = Order.builder()
                .checkoutGroup(checkoutGroup)
                .buyerId(buyerId)
                .sellerId(key.sellerId())
                .storeId(key.storeId())
                .orderCode(generateOrderCode())
                .status(request.method() == PaymentMethod.COD ? OrderStatus.PENDING_CONFIRMED : OrderStatus.PENDING_PAYMENT)
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

           Offer acceptedOffer = acceptedOffersByProductId.get(p.getId());
           BigDecimal unitPrice = acceptedOffer != null ? acceptedOffer.getPrice() : p.getPrice();

           BigDecimal subtotal = unitPrice.multiply(
                   BigDecimal.valueOf(quantity)
           );

            OrderItem item = OrderItem.builder()
                    .productId(p.getId())
                    .productName(p.getTitle())
                    .unitPrice(unitPrice)
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
