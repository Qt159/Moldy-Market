package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.order.entity.*;
import com.moldy.moldymarket.config.CustomAuthenticationEntryPoint;
import com.moldy.moldymarket.config.CustomAccessDeniedHandler;
import com.moldy.moldymarket.order.enums.*;
import com.moldy.moldymarket.payment.entity.Payment;
import com.moldy.moldymarket.payment.enums.*;
import com.moldy.moldymarket.product.entity.*;
import com.moldy.moldymarket.role.entity.Role;
import com.moldy.moldymarket.security.PermissionConstants;
import com.moldy.moldymarket.security.jwt.JwtService;
import com.moldy.moldymarket.store.entity.*;
import com.moldy.moldymarket.user.entity.*;
import com.moldy.moldymarket.userrole.entity.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "checkout.expiration.enabled=false",
        "sePay.bank.name=VCB", "sePay.bank.account=0000000001",
        "sePay.webhook.secret-key=store-orders-test-only",
        "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true"
})
@AutoConfigureMockMvc
@Transactional
class StoreOrderHttpIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired CustomAuthenticationEntryPoint authenticationEntryPoint;
    @Autowired CustomAccessDeniedHandler accessDeniedHandler;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @PersistenceContext EntityManager em;
    User owner, otherOwner, staff, deniedStaff, foreignStaff, buyer;
    Store store, otherStore;
    Order pending, confirmed;
    UUID categoryId, groupId;

    @BeforeEach void fixtures() {
        owner = user(); otherOwner = user(); staff = user();
        deniedStaff = user(); foreignStaff = user(); buyer = user();
        store = new Store(owner.getId(), "Store order test " + UUID.randomUUID(), "", "Test address");
        otherStore = new Store(otherOwner.getId(), "Other order test " + UUID.randomUUID(), "", "Test address");
        em.persist(store); em.persist(otherStore);
        Role userRole = em.createQuery("select r from Role r where r.name='USER'", Role.class).getSingleResult();
        Role ownerRole = em.createQuery("select r from Role r where r.name='OWNER'", Role.class).getSingleResult();
        em.persist(new UserRole(buyer, userRole, null));
        em.persist(new UserRole(owner, ownerRole, store.getId()));
        em.persist(new StoreStaff(store, staff, Set.of(PermissionConstants.ORDER_PROCESS)));
        em.persist(new StoreStaff(store, deniedStaff, Set.of(PermissionConstants.PRODUCT_CREATE)));
        em.persist(new StoreStaff(otherStore, foreignStaff, Set.of(PermissionConstants.ORDER_PROCESS)));
        categoryId = em.createQuery("select c.id from Category c", UUID.class).setMaxResults(1).getSingleResult();
        var group = CheckoutGroup.builder().buyerId(buyer.getId()).totalAmount(BigDecimal.valueOf(50)).build();
        group.setStatus(CheckoutGroupStatus.PAY_ON_DELIVERY);
        em.persist(group); groupId = group.getId();
        pending = order(group, store, staff, OrderStatus.PENDING_CONFIRMED, 2);
        confirmed = order(group, store, deniedStaff, OrderStatus.CONFIRMED, 1);
        order(group, otherStore, foreignStaff, OrderStatus.PENDING_CONFIRMED, 1);
        order(group, null, staff, OrderStatus.PENDING_CONFIRMED, 1);
        em.persist(Payment.builder().checkoutGroupId(groupId).payerId(buyer.getId())
                .amount(BigDecimal.valueOf(50)).method(PaymentMethod.COD).build());
        em.flush();
        em.createNativeQuery("update orders set created_at=:stamp where checkout_group_id=:id")
                .setParameter("stamp", Instant.now().minusSeconds(60)).setParameter("id", groupId).executeUpdate();
        em.clear();
    }

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void staffSeesPerOrderCodCollectionEvenWhenSharedPaymentIsPending() throws Exception {
        Instant paidAt = Instant.parse("2026-11-22T03:00:00Z");
        var paid = em.find(Order.class, confirmed.getId());
        paid.setStatus(OrderStatus.COMPLETED); paid.setPaidAt(paidAt);
        em.flush(); em.clear();
        mvc.perform(get(path()).header("Authorization", bearer(staff)).param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].paymentMethod").value("COD"))
                .andExpect(jsonPath("$.data.content[0].paymentStatus").value("SUCCESSFUL"))
                .andExpect(jsonPath("$.data.content[0].paidAt").value(paidAt.toString()));
        mvc.perform(get(path()).header("Authorization", bearer(staff)).param("status", "PENDING_CONFIRMED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].paymentStatus").value("PENDING"));
        var payment = em.createQuery("select p from Payment p where p.checkoutGroupId=:id", Payment.class)
                .setParameter("id", groupId).getSingleResult();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        mvc.perform(get("/api/v1/orders/" + confirmed.getId()).header("Authorization", bearer(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.paymentStatus").value("SUCCESSFUL"));
    }

    @Test void buyerCancelsOneCodOrderAndTheOtherOrderKeepsPendingCollection() throws Exception {
        mvc.perform(post("/api/v1/orders/buyer/" + confirmed.getId() + "/cancel").header("Authorization", bearer(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.paymentStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.paymentMethod").value("COD"));
        mvc.perform(get(path()).header("Authorization", bearer(owner)).param("status", "PENDING_CONFIRMED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].paymentStatus").value("PENDING"));
    }

    @Test void ownerReadsAllStaffOrdersWithDatabasePaginationAndStableOrdering() throws Exception {
        List<String> ids = new ArrayList<>(List.of(pending.getId().toString(), confirmed.getId().toString()));
        ids.sort(Comparator.reverseOrder());
        for (int page = 1; page <= 2; page++) {
            mvc.perform(get(path()).header("Authorization", bearer(owner))
                            .param("page", String.valueOf(page)).param("size", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.page").value(page))
                    .andExpect(jsonPath("$.data.size").value(1))
                    .andExpect(jsonPath("$.data.totalElements").value(2))
                    .andExpect(jsonPath("$.data.hasNext").value(page == 1))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].id").value(ids.get(page - 1)))
                    .andExpect(jsonPath("$.data.content[0].storeId").value(store.getId().toString()));
        }
    }

    @Test void delegatedStaffReadsCoworkerOrdersAndDefaultsToOneBasedPageTwenty() throws Exception {
        mvc.perform(get(path()).header("Authorization", bearer(staff)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.content[*].id", containsInAnyOrder(pending.getId().toString(), confirmed.getId().toString())))
                .andExpect(jsonPath("$.data.content[*].sellerId", containsInAnyOrder(staff.getId().toString(), deniedStaff.getId().toString())));
    }

    @Test void missingPermissionAndOtherStoreMembershipCannotReadCustomerDetails() throws Exception {
        for (User actor : List.of(deniedStaff, foreignStaff, otherOwner, buyer)) {
            mvc.perform(get(path()).header("Authorization", bearer(actor)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STORE_ACCESS_DENIED"))
                    .andExpect(jsonPath("$.data.content").doesNotExist());
        }
    }

    @Test void anonymousCannotUseTheStoreOrderRoute() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test void security401HandlerSerializesInstantUsingTheSpringMapper() throws Exception {
        var response = new MockHttpServletResponse();
        authenticationEntryPoint.commence(new MockHttpServletRequest(), response,
                new InsufficientAuthenticationException("test"));
        var body = json.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body.get("code").asText()).isEqualTo("UNAUTHENTICATED");
        assertThat(Instant.parse(body.get("timestamp").asText())).isBeforeOrEqualTo(Instant.now());
    }

    @Test void security403HandlerSerializesInstantUsingTheSpringMapper() throws Exception {
        var response = new MockHttpServletResponse();
        accessDeniedHandler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("test"));
        var body = json.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(body.get("code").asText()).isEqualTo("ACCESS_DENIED");
        assertThat(Instant.parse(body.get("timestamp").asText())).isBeforeOrEqualTo(Instant.now());
    }

    @Test void statusFilterIsStoreScopedAndIncludesCompleteItemSnapshots() throws Exception {
        mvc.perform(get(path()).header("Authorization", bearer(owner)).param("status", "PENDING_CONFIRMED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(pending.getId().toString()))
                .andExpect(jsonPath("$.data.content[0].checkoutGroupId").value(groupId.toString()))
                .andExpect(jsonPath("$.data.content[0].items", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].items[0].quantity").value(1))
                .andExpect(jsonPath("$.data.content[0].items[0].orderId").value(pending.getId().toString()));
    }

    @Test void emptyStoreAndPagePastEndReturnEmptyContentInsteadOf404() throws Exception {
        var emptyOwner = user();
        var empty = new Store(emptyOwner.getId(), "Empty order test " + UUID.randomUUID(), "", "Address");
        em.persist(empty); em.flush();
        mvc.perform(get("/api/v1/stores/" + empty.getId() + "/orders").header("Authorization", bearer(emptyOwner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements").value(0)).andExpect(jsonPath("$.data.hasNext").value(false));
        mvc.perform(get(path()).header("Authorization", bearer(owner)).param("page", "99"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(0)))
                .andExpect(jsonPath("$.data.page").value(99)).andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test void invalidInputsReturn400WithoutInventingAnotherErrorCode() throws Exception {
        for (String[] pair : List.of(new String[]{"page", "0"}, new String[]{"page", "-1"},
                new String[]{"size", "0"}, new String[]{"size", "101"}, new String[]{"status", "BOGUS"},
                new String[]{"page", "2147483647"})) {
            mvc.perform(get(path()).header("Authorization", bearer(owner)).param(pair[0], pair[1]))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mvc.perform(get("/api/v1/stores/not-a-uuid/orders").header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test void blockedStoreUsesTheExistingStoreGuardPolicy() throws Exception {
        em.find(Store.class, store.getId()).changeStatus(StoreStatus.BLOCKED); em.flush(); em.clear();
        mvc.perform(get(path()).header("Authorization", bearer(owner)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STORE_BLOCKED"));
    }

    @Test void viewingPermissionDoesNotGiveStaffTheOwnerCodConfirmationAction() throws Exception {
        String action = "/api/v1/orders/seller/" + pending.getId() + "/confirm";
        mvc.perform(post(action).header("Authorization", bearer(staff)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ORDER_NOT_EXISTED"));
        mvc.perform(post(action).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        mvc.perform(get(path()).header("Authorization", bearer(owner)).param("status", "PENDING_CONFIRMED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(0)));
        assertThat(em.createQuery("select p.status from Payment p where p.checkoutGroupId=:id", PaymentStatus.class)
                .setParameter("id", groupId).getSingleResult()).isEqualTo(PaymentStatus.PENDING);
    }

    private User user() {
        User user = new User(); user.setEmail("store-orders-" + UUID.randomUUID() + "@example.test");
        user.setFullName("Store order fixture"); user.setStatus(UserStatus.ACTIVE); em.persist(user); return user;
    }

    private Order order(CheckoutGroup group, Store shop, User seller, OrderStatus status, int itemCount) {
        var order = Order.builder().checkoutGroup(group).buyerId(buyer.getId()).sellerId(seller.getId())
                .storeId(shop == null ? null : shop.getId()).orderCode("SO" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .status(status).receiverName("Test receiver").receiverPhone("0901234567")
                .shippingAddress("Test address").shippingFee(BigDecimal.ZERO).totalAmount(BigDecimal.TEN.multiply(BigDecimal.valueOf(itemCount))).build();
        for (int i = 0; i < itemCount; i++) {
            var product = new Product(seller.getId(), shop == null ? null : shop.getId(), categoryId,
                    "Store order fixture", "Fixture", BigDecimal.TEN, ProductCondition.USED, PricingStrategy.SELLER_SET, List.of(), Map.of());
            em.persist(product);
            order.addOrderItem(OrderItem.builder().productId(product.getId()).productName("Snapshot fixture")
                    .quantity(1).unitPrice(BigDecimal.TEN).build());
        }
        group.addOrder(order); em.persist(order); return order;
    }

    private String path() { return "/api/v1/stores/" + store.getId() + "/orders"; }
    private String bearer(User user) { return "Bearer " + jwt.generateAccessToken(user); }
}
