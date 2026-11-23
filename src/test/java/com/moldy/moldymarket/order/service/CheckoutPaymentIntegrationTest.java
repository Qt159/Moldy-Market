package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.cart.entity.Cart;
import com.moldy.moldymarket.cart.entity.CartItem;
import com.moldy.moldymarket.cart.service.CartService;
import com.moldy.moldymarket.order.dto.request.*;
import com.moldy.moldymarket.order.dto.response.CheckoutResponse;
import com.moldy.moldymarket.order.entity.CheckoutGroup;
import com.moldy.moldymarket.order.enums.*;
import com.moldy.moldymarket.payment.enums.*;
import com.moldy.moldymarket.payment.repository.PaymentRepository;
import com.moldy.moldymarket.payment.service.PaymentTransactionService;
import com.moldy.moldymarket.payment.webhook.IncomingTransfer;
import com.moldy.moldymarket.product.entity.*;
import com.moldy.moldymarket.user.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"checkout.expiration.enabled=false","sePay.webhook.secret-key=checkout-integration-test-secret"})
@AutoConfigureMockMvc
class CheckoutPaymentIntegrationTest {
    @Autowired CheckoutGroupService checkoutService;
    @Autowired PaymentTransactionService transferService;
    @Autowired PaymentRepository payments;
    @Autowired OrderService orders;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MockMvc mvc;
    @MockitoSpyBean CartService carts;
    @PersistenceContext EntityManager em;
    TransactionTemplate tx;
    List<UUID> userIds=new ArrayList<>(), productIds=new ArrayList<>();
    List<UUID> checkoutIds=new CopyOnWriteArrayList<>();
    List<String> externalIds=new ArrayList<>();
    UUID buyerId, sellerId, otherSellerId;
    String buyerEmail, sellerEmail, otherSellerEmail;

    @BeforeEach void fixtures() {
        tx=new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status->{
            var buyer=user(); var seller=user(); var otherSeller=user();
            buyerId=buyer.getId(); buyerEmail=buyer.getEmail();
            sellerId=seller.getId(); sellerEmail=seller.getEmail();
            otherSellerId=otherSeller.getId(); otherSellerEmail=otherSeller.getEmail();
            UUID category=em.createQuery("select c.id from Category c",UUID.class).setMaxResults(1).getSingleResult();
            for(int i=0;i<3;i++) {
                var product=new Product(i==1?otherSellerId:sellerId,null,category,"Checkout test","Test fixture",
                        BigDecimal.valueOf((i+1)*10),ProductCondition.USED,PricingStrategy.SELLER_SET,List.of(),Map.of());
                em.persist(product); productIds.add(product.getId());
            }
            var cart=Cart.builder().user(buyer).build();
            for(int i=0;i<3;i++) cart.addItem(CartItem.builder().listingId(productIds.get(i))
                    .sellerId(i==1?otherSellerId:sellerId).priceAtAddTime(BigDecimal.valueOf((i+1)*10))
                    .totalPrice(BigDecimal.valueOf((i+1)*10)).build());
            em.persist(cart); em.flush();
        });
        authenticate(buyerEmail,"USER");
    }

    @AfterEach void cleanup() {
        reset(carts);
        SecurityContextHolder.clearContext();
        tx.executeWithoutResult(status->{
            if(!checkoutIds.isEmpty()) {
                delete("delete from Payment p where p.checkoutGroupId in :ids",checkoutIds);
                delete("delete from OrderItem i where i.order.checkoutGroup.id in :ids",checkoutIds);
                delete("delete from Order o where o.checkoutGroup.id in :ids",checkoutIds);
                delete("delete from CheckoutGroup c where c.id in :ids",checkoutIds);
            }
            if(!externalIds.isEmpty()) delete("delete from PaymentTransaction p where lower(p.provider)='sepay' and p.externalId in :ids",externalIds);
            if(!userIds.isEmpty()) {
                delete("delete from CartItem i where i.cart.user.id in :ids",userIds);
                delete("delete from Cart c where c.user.id in :ids",userIds);
            }
            if(!productIds.isEmpty()) delete("delete from Product p where p.id in :ids",productIds);
            if(!userIds.isEmpty()) delete("delete from User u where u.id in :ids",userIds);
        });
    }

    @Test void onlinePaymentConfirmsBothOrdersAndKeepsUnselectedCartItems() {
        var checkout=create(PaymentMethod.ONLINE_BANKING,productIds.subList(0,2));
        assertThat(checkout.orders()).hasSize(2);
        assertThat(checkout.paymentResponse().paymentStatus()).isEqualTo("PENDING");
        assertThat(checkout.paymentResponse().method()).isEqualTo(PaymentMethod.ONLINE_BANKING);
        assertThat(checkout.orders()).allMatch(order->checkout.id().equals(order.checkoutGroupId()));
        var incoming=incoming(checkout.id());
        transferService.process(incoming);
        assertPaid(checkout.id());
        transferService.process(incoming(checkout.id()));
        tx.executeWithoutResult(status->{
            assertThat(cartProducts()).containsExactly(productIds.get(2));
            assertThat(em.createQuery("select p.matchStatus from PaymentTransaction p where p.externalId=:id",MatchStatus.class)
                    .setParameter("id",externalIds.get(1)).getSingleResult()).isEqualTo(MatchStatus.DUPLICATE);
        });
    }

    @Test void failingCartWriteRollsBackReceiptPaymentOrdersAndProductsAndCanRetry() {
        var checkout=create(PaymentMethod.ONLINE_BANKING,productIds.subList(0,2));
        var incoming=incoming(checkout.id());
        doThrow(new IllegalStateException("Injected cart failure")).when(carts).removePurchasedItems(any(UUID.class),anyCollection());
        assertThatThrownBy(()->transferService.process(incoming)).isInstanceOf(RuntimeException.class);
        reset(carts);
        tx.executeWithoutResult(status->{
            var group=em.find(CheckoutGroup.class,checkout.id());
            assertThat(group.getStatus()).isEqualTo(CheckoutGroupStatus.PENDING_PAYMENT);
            assertThat(group.getOrders()).allMatch(order->order.getStatus()==OrderStatus.PENDING_PAYMENT);
            assertThat(payments.findByCheckoutGroupId(checkout.id()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(cartProducts()).containsExactlyInAnyOrderElementsOf(productIds);
            assertThat(em.find(Product.class,productIds.get(0)).getStatus()).isEqualTo(ProductStatus.ACTIVE);
            assertThat(em.createQuery("select count(p) from PaymentTransaction p where p.externalId=:id",Long.class)
                    .setParameter("id",incoming.externalId()).getSingleResult()).isZero();
        });
        transferService.process(incoming);
        assertPaid(checkout.id());
    }

    @Test void lateMoneyIsRetainedForReconciliationWithoutSellingOrRemovingCart() {
        var checkout=create(PaymentMethod.ONLINE_BANKING,List.of(productIds.get(0)));
        tx.executeWithoutResult(status->em.createNativeQuery("update checkout_groups set expired_at=:deadline where id=:id")
                .setParameter("deadline",Instant.now().minusSeconds(1)).setParameter("id",checkout.id()).executeUpdate());
        var incoming=incoming(checkout.id()); transferService.process(incoming);
        tx.executeWithoutResult(status->{
            assertThat(em.find(CheckoutGroup.class,checkout.id()).getStatus()).isEqualTo(CheckoutGroupStatus.EXPIRED);
            var product=em.find(Product.class,productIds.get(0));
            assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
            assertThat(product.getReservedCheckoutId()).isNull();
            assertThat(cartProducts()).hasSize(3);
            assertThat(em.createQuery("select p.matchStatus from PaymentTransaction p where p.externalId=:id",MatchStatus.class)
                    .setParameter("id",incoming.externalId()).getSingleResult()).isEqualTo(MatchStatus.LATE);
        });
    }

    @Test void codMovesThroughOwnerConfirmationAdminDeliveryAndCollection() {
        var checkout=create(PaymentMethod.COD,List.of(productIds.get(0)));
        UUID orderId=checkout.orders().get(0).id();
        assertThat(checkout.status()).isEqualTo(CheckoutGroupStatus.PAY_ON_DELIVERY);
        assertThat(checkout.paymentResponse().method()).isEqualTo(PaymentMethod.COD);
        authenticate(sellerEmail,"USER"); orders.confirmOrder(orderId);
        assertThatThrownBy(()->orders.startShipping(orderId)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        authenticate(buyerEmail,"ADMIN"); orders.startShipping(orderId); orders.markDelivered(orderId); orders.receivedCOD(orderId);
        orders.receivedCOD(orderId);
        tx.executeWithoutResult(status->{
            assertThat(payments.findByCheckoutGroupId(checkout.id()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESSFUL);
            assertThat(em.find(CheckoutGroup.class,checkout.id()).getStatus()).isEqualTo(CheckoutGroupStatus.PAID);
            assertThat(cartProducts()).containsExactlyInAnyOrder(productIds.get(1),productIds.get(2));
        });
    }

    @Test void twoBuyersCannotReserveTheSameProduct() throws Exception {
        var executor=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var first=executor.submit(()->compete(start,buyerEmail));
            var second=executor.submit(()->compete(start,otherSellerEmail));
            start.countDown();
            assertThat(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("CREATED","PRODUCT_RESERVED");
        } finally { executor.shutdownNow(); executor.awaitTermination(15,TimeUnit.SECONDS); }
    }

    @Test void cancellingOneCodOrderPersistsTheReducedAmountAndCollectsOnlyTheOther() {
        var checkout=create(PaymentMethod.COD,productIds.subList(0,2));
        var cancelled=checkout.orders().stream().filter(order->order.items().get(0).productId().equals(productIds.get(0))).findFirst().orElseThrow();
        var payable=checkout.orders().stream().filter(order->!order.id().equals(cancelled.id())).findFirst().orElseThrow();
        orders.cancelOrderFromBuyer(cancelled.id());
        tx.executeWithoutResult(status->{
            assertThat(payments.findByCheckoutGroupId(checkout.id()).orElseThrow().getAmount()).isEqualByComparingTo("20");
            assertThat(cartProducts()).hasSize(3);
            assertThat(em.find(Product.class,productIds.get(0)).getReservedCheckoutId()).isNull();
        });
        authenticate(otherSellerEmail,"USER"); orders.confirmOrder(payable.id());
        authenticate(buyerEmail,"ADMIN"); orders.startShipping(payable.id()); orders.markDelivered(payable.id()); orders.receivedCOD(payable.id());
        tx.executeWithoutResult(status->{
            assertThat(payments.findByCheckoutGroupId(checkout.id()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESSFUL);
            assertThat(em.find(CheckoutGroup.class,checkout.id()).getStatus()).isEqualTo(CheckoutGroupStatus.PAID);
            assertThat(cartProducts()).containsExactlyInAnyOrder(productIds.get(0),productIds.get(2));
        });
    }

    @Test void cancellingAnUnpaidBankOrderInvalidatesItsWholeQrAndCannotSellANewerHold() {
        var checkout=create(PaymentMethod.ONLINE_BANKING,productIds.subList(0,2));
        var incoming=incoming(checkout.id());
        orders.cancelOrderFromBuyer(checkout.orders().get(0).id());
        var newer=create(PaymentMethod.COD,List.of(productIds.get(0)));
        transferService.process(incoming);
        tx.executeWithoutResult(status->{
            var old=em.find(CheckoutGroup.class,checkout.id());
            assertThat(old.getStatus()).isEqualTo(CheckoutGroupStatus.CANCELLED);
            assertThat(old.getOrders()).allMatch(order->order.getStatus()==OrderStatus.CANCELLED);
            assertThat(em.find(Product.class,productIds.get(0)).getReservedCheckoutId()).isEqualTo(newer.id());
            assertThat(cartProducts()).hasSize(3);
            assertThat(payments.findByCheckoutGroupId(checkout.id()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        });
    }

    @Test void signedHttpWebhookPaysOnceRejectsTamperingAndAcknowledgesRetries() throws Exception {
        var checkout=create(PaymentMethod.ONLINE_BANKING,productIds.subList(0,2));
        String code=tx.execute(status->payments.findByCheckoutGroupId(checkout.id()).orElseThrow().getPaymentCode());
        long id=System.currentTimeMillis()*1000+ThreadLocalRandom.current().nextInt(1000);
        externalIds.add(Long.toString(id));
        String body="{\"id\":"+id+",\"gateway\":\"test-bank\",\"transactionDate\":\"2026-11-20 10:00:00\",\"accountNumber\":\"test-account\",\"content\":\""+code+"\",\"code\":\""+code+"\",\"description\":\"Người mua chuyển khoản\",\"transferType\":\"in\",\"transferAmount\":30}";
        String timestamp=Long.toString(Instant.now().getEpochSecond());
        var mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec("checkout-integration-test-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String signature="sha256="+HexFormat.of().formatHex(mac.doFinal((timestamp+"."+body).getBytes(StandardCharsets.UTF_8)));
        mvc.perform(post("/api/v1/payment_transactions/webhook/sepay").contentType("application/json")
                .header("X-SePay-Timestamp",timestamp).header("X-SePay-Signature",signature).content(body.replace("\"transferAmount\":30","\"transferAmount\":31")))
                .andExpect(status().isUnauthorized());
        for(int retry=0;retry<2;retry++) mvc.perform(post("/api/v1/payment_transactions/webhook/sepay").contentType("application/json")
                .header("X-SePay-Timestamp",timestamp).header("X-SePay-Signature",signature).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertPaid(checkout.id());
        tx.executeWithoutResult(status->assertThat(em.createQuery("select count(p) from PaymentTransaction p where p.externalId=:id",Long.class)
                .setParameter("id",Long.toString(id)).getSingleResult()).isEqualTo(1L));
    }

    private String compete(CountDownLatch start,String email) throws Exception {
        start.await(); authenticate(email,"USER");
        try { create(PaymentMethod.COD,List.of(productIds.get(0))); return "CREATED"; }
        catch(com.moldy.moldymarket.common.exception.AppException error) { return error.getErrorCode().name(); }
        finally { SecurityContextHolder.clearContext(); }
    }
    private CheckoutResponse create(PaymentMethod method,List<UUID> ids) {
        var result=checkoutService.createCheckoutGroup(new CheckoutGroupRequest(ids.stream()
                .map(id->new OrderItemRequest(id,null)).toList(),"Checkout test","0901234567","Test address",method,null));
        checkoutIds.add(result.id()); return result;
    }
    private User user() {
        var user=new User(); user.setEmail("checkout-"+UUID.randomUUID()+"@test.invalid");
        user.setFullName("Checkout test"); user.setStatus(UserStatus.ACTIVE); em.persist(user); em.flush(); userIds.add(user.getId()); return user;
    }
    private IncomingTransfer incoming(UUID checkoutId) {
        String code=tx.execute(status->payments.findByCheckoutGroupId(checkoutId).orElseThrow().getPaymentCode());
        BigDecimal amount=tx.execute(status->payments.findByCheckoutGroupId(checkoutId).orElseThrow().getAmount());
        String externalId="checkout-test-"+UUID.randomUUID(); externalIds.add(externalId);
        return new IncomingTransfer("sepay",externalId,Instant.now(),"test-account","test-bank",null,code,null,code,TransferType.IN,amount,null,"{}");
    }
    private void assertPaid(UUID checkoutId) {
        tx.executeWithoutResult(status->{
            var group=em.find(CheckoutGroup.class,checkoutId);
            assertThat(group.getStatus()).isEqualTo(CheckoutGroupStatus.PAID);
            assertThat(group.getOrders()).allMatch(order->order.getStatus()==OrderStatus.CONFIRMED && order.getPaidAt()!=null);
            assertThat(em.find(Product.class,productIds.get(0)).getStatus()).isEqualTo(ProductStatus.SOLD);
            assertThat(em.find(Product.class,productIds.get(1)).getStatus()).isEqualTo(ProductStatus.SOLD);
        });
    }
    private List<UUID> cartProducts() {
        return em.createQuery("select i.listingId from CartItem i where i.cart.user.id=:buyer",UUID.class).setParameter("buyer",buyerId).getResultList();
    }
    private void authenticate(String email,String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email,"unused",List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
    private void delete(String query,Collection<?> ids) { em.createQuery(query).setParameter("ids",ids).executeUpdate(); }
}
