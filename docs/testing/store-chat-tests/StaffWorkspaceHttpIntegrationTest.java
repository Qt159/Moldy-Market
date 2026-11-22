package com.moldy.moldymarket.store.service;

import com.moldy.moldymarket.product.entity.*;
import com.moldy.moldymarket.role.entity.Role;
import com.moldy.moldymarket.security.PermissionConstants;
import com.moldy.moldymarket.security.jwt.JwtService;
import com.moldy.moldymarket.store.entity.*;
import com.moldy.moldymarket.user.entity.*;
import com.moldy.moldymarket.userrole.entity.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "checkout.expiration.enabled=false", "sePay.bank.name=VCB",
        "sePay.bank.account=0000000001", "sePay.webhook.secret-key=staff-workspace-test-only"
})
@AutoConfigureMockMvc
@Transactional
class StaffWorkspaceHttpIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @PersistenceContext EntityManager em;
    User owner, manager, poster;
    Store store, otherStore;
    StoreStaff membership;

    @BeforeEach void fixtures() {
        owner = user(); manager = user(); poster = user();
        store = new Store(owner.getId(), "Workspace " + UUID.randomUUID(), "", "Test address");
        otherStore = new Store(user().getId(), "Other workspace " + UUID.randomUUID(), "", "Test address");
        em.persist(store); em.persist(otherStore);
        membership = new StoreStaff(store, manager, Set.of(PermissionConstants.STORE_WORKSPACE_ACCESS,
                PermissionConstants.PRODUCT_MANAGE_OWN, PermissionConstants.ORDER_PROCESS));
        em.persist(membership);
        em.persist(new StoreStaff(store, poster, Set.of(PermissionConstants.STORE_WORKSPACE_ACCESS,
                PermissionConstants.PRODUCT_CREATE)));
        Role userRole = em.createQuery("select r from Role r where r.name='USER'", Role.class).getSingleResult();
        em.persist(new UserRole(poster, userRole, null));
        UUID category = em.createQuery("select c.id from Category c", UUID.class).setMaxResults(1).getSingleResult();
        for (Store shop : List.of(store, otherStore)) {
            em.persist(new Product(poster.getId(), shop.getId(), category, "Workspace product", "Test",
                    BigDecimal.TEN, ProductCondition.USED, PricingStrategy.SELLER_SET, List.of(), Map.of()));
        }
        em.flush(); em.clear();
    }

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void publicStoreExposesOnlyTheOwnerIdNeededToStartChat() throws Exception {
        mvc.perform(get("/api/v1/stores/" + store.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerId").value(owner.getId().toString()))
                .andExpect(jsonPath("$.data.id").value(store.getId().toString()))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.phone").doesNotExist())
                .andExpect(jsonPath("$.data.pickupAddress").doesNotExist());
    }

    @Test void staffCanReadOnlyTheirOwnDelegatedProfileWithoutManagingStaff() throws Exception {
        mvc.perform(get("/api/v1/stores/my-store/staff/me").header("Authorization", bearer(poster)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(poster.getId().toString()))
                .andExpect(jsonPath("$.data.storeId").value(store.getId().toString()))
                .andExpect(jsonPath("$.data.permissions", hasItem("PRODUCT_CREATE")))
                .andExpect(jsonPath("$.data.permissions", not(hasItem("ORDER_PROCESS"))));
        mvc.perform(get("/api/v1/stores/my-store/staff").header("Authorization", bearer(poster)))
                .andExpect(status().isForbidden());
    }

    @Test void mergedUserPermissionsAreNotDelegatedStorePermissions() throws Exception {
        mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(poster)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.permissions", hasItem("ORDER_PROCESS")))
                .andExpect(jsonPath("$.data.permissions", hasItem("PRODUCT_MANAGE_OWN")));
        mvc.perform(get(path(store, "orders")).header("Authorization", bearer(poster)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STORE_ACCESS_DENIED"));
        mvc.perform(get(path(store, "products")).header("Authorization", bearer(poster)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STORE_ACCESS_DENIED"));
    }

    @Test void ownerAndDelegatedManagerReadOnlyThisStoresProducts() throws Exception {
        for (User actor : List.of(owner, manager)) {
            mvc.perform(get(path(store, "products")).header("Authorization", bearer(actor)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(1))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].storeId").value(store.getId().toString()));
        }
        mvc.perform(get(path(otherStore, "products")).header("Authorization", bearer(manager)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STORE_ACCESS_DENIED"));
    }

    @Test void revokedPermissionAndBlockedStoreDenyTheSameToken() throws Exception {
        String token = bearer(manager);
        em.find(StoreStaff.class, membership.getId()).setPermissions(Set.of(PermissionConstants.STORE_WORKSPACE_ACCESS));
        em.flush(); em.clear();
        mvc.perform(get(path(store, "products")).header("Authorization", token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STORE_ACCESS_DENIED"));
        em.find(Store.class, store.getId()).changeStatus(StoreStatus.BLOCKED); em.flush(); em.clear();
        mvc.perform(get(path(store, "products")).header("Authorization", bearer(owner)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STORE_BLOCKED"));
    }

    @Test void productsAndSelfProfileRequireAuthentication() throws Exception {
        mvc.perform(get(path(store, "products"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/stores/my-store/staff/me")).andExpect(status().isUnauthorized());
    }

    private User user() {
        var user = new User(); user.setEmail("workspace-" + UUID.randomUUID() + "@example.test");
        user.setFullName("Workspace fixture"); user.setStatus(UserStatus.ACTIVE); em.persist(user); return user;
    }
    private String bearer(User user) { return "Bearer " + jwt.generateAccessToken(user); }
    private String path(Store shop, String resource) { return "/api/v1/stores/" + shop.getId() + "/" + resource; }
}
