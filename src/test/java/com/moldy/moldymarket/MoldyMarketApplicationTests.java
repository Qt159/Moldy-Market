package com.moldy.moldymarket;

import com.moldy.moldymarket.category.entity.Category;
import com.moldy.moldymarket.category.entity.CategoryStatus;
import com.moldy.moldymarket.category.repository.CategoryRepository;
import com.moldy.moldymarket.order.entity.CheckoutGroup;
import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.payment.entity.Payment;
import com.moldy.moldymarket.payment.enums.PaymentMethod;
import com.moldy.moldymarket.payment.enums.PaymentStatus;
import com.moldy.moldymarket.payment.enums.TransferType;
import com.moldy.moldymarket.payment.repository.PaymentRepository;
import com.moldy.moldymarket.payment.service.PaymentTransactionService;
import com.moldy.moldymarket.payment.webhook.IncomingTransfer;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.repository.UserRepository;
import com.moldy.moldymarket.wallet.enums.EscrowStatus;
import com.moldy.moldymarket.wallet.repository.EscrowHoldRepository;
import com.moldy.moldymarket.wallet.repository.WalletRepository;
import com.moldy.moldymarket.wallet.repository.WithdrawalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.cors.allowed-origins=https://client.example.test")
@AutoConfigureMockMvc
class MoldyMarketApplicationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CategoryRepository categories;
    @Autowired private UserRepository users;
    @Autowired private CheckoutGroupRepository checkouts;
    @Autowired private OrderRepository orders;
    @Autowired private PaymentRepository payments;
    @Autowired private PaymentTransactionService bankNotifications;
    @Autowired private EscrowHoldRepository escrow;
    @Autowired private WalletRepository wallets;
    @Autowired private WithdrawalRepository withdrawals;

    @Test
    void contextLoads() {
    }

    @Test
    void loginPreflightAllowsTheConfiguredClientAndCredentials() throws Exception {
        mvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://client.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Moldy-Auth, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://client.example.test"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void loginPreflightRejectsAnUnconfiguredOrigin() throws Exception {
        mvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Moldy-Auth"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownStoreRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/stores/my-store"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void bothCategoryRoutesExposeTheTreeAndDetailWithoutAuthentication() throws Exception {
        var root = categories.findAllByStatusOrderByLevelAscNameAsc(CategoryStatus.ACTIVE).stream()
                .filter(Category::isRoot)
                .findFirst()
                .orElseThrow();
        for (String path : new String[]{"/api/v1/categories", "/api/categories"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].id").isString())
                    .andExpect(jsonPath("$.data[0].children").isArray());
            mvc.perform(get(path + "/" + root.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(root.getId().toString()))
                    .andExpect(jsonPath("$.data.hasChildren").value(true));
        }
    }

    @Test
    void ordinaryUserCannotChangeDisbursements() throws Exception {
        var actor = user("buyer@example.test").roles("USER");
        mvc.perform(patch("/api/v1/withdrawals/1/approve").with(actor))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/escrows/orders/1/capture").with(actor))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/escrows/orders/1/dispute/resolve?outcome=REFUND_BUYER").with(actor))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void adminRecordsReceiptDisputeAndRefundWithoutMovingBankFunds() throws Exception {
        User buyer = testUser();
        User seller = testUser();
        User admin = testUser();
        BigDecimal amount = new BigDecimal("100.00");
        var group = checkouts.save(CheckoutGroup.builder().buyerId(buyer.getId()).totalAmount(amount).build());
        var order = Order.builder()
                .orderCode(UUID.randomUUID().toString().substring(0, 10))
                .buyerId(buyer.getId()).sellerId(seller.getId()).status(OrderStatus.PENDING_PAYMENT)
                .receiverName("Test buyer").receiverPhone("0123456789").shippingAddress("Test address")
                .shippingFee(BigDecimal.ZERO).totalAmount(amount).build();
        group.addOrder(order);
        orders.save(order);
        var payment = payments.save(Payment.builder()
                .checkoutGroupId(group.getId()).payerId(buyer.getId()).amount(amount)
                .method(PaymentMethod.ONLINE_BANKING).paymentCode("MM" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .build());

        bankNotifications.process(new IncomingTransfer("sepay", UUID.randomUUID().toString(), Instant.now(),
                "test-account", "test", null, payment.getPaymentCode(), null, payment.getPaymentCode(),
                TransferType.IN, amount, null, null));
        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        String path = "/api/v1/payments/admin/" + payment.getId() + "/confirm";
        String receipt = "{\"bankReference\":\"" + UUID.randomUUID() + "\",\"amount\":100.00}";
        mvc.perform(patch(path).with(user(buyer.getEmail()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(receipt))
                .andExpect(status().isForbidden());

        var operator = user(admin.getEmail()).roles("ADMIN");
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(patch(path).with(operator).contentType(MediaType.APPLICATION_JSON).content(receipt))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESSFUL"));
        }
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        var buyerWallet = wallets.findByUserId(buyer.getId()).orElseThrow();
        assertEquals(0, buyerWallet.getHeldBalance().compareTo(amount));
        assertEquals(EscrowStatus.HELD, escrow.findByOrderId(order.getId().toString()).orElseThrow().getStatus());

        String dispute = "/api/v1/escrows/orders/" + order.getId() + "/dispute";
        mvc.perform(post(dispute).with(operator)).andExpect(status().isOk());
        assertEquals(OrderStatus.DISPUTED, order.getStatus());
        mvc.perform(post(dispute + "/resolve").param("outcome", "REFUND_BUYER").with(operator))
                .andExpect(status().isOk());
        assertEquals(OrderStatus.REFUNDED, order.getStatus());
        assertEquals(0, buyerWallet.getAvailableBalance().compareTo(amount));
        assertEquals(0, buyerWallet.getHeldBalance().signum());

        String withdraw = """
                {"amount":100.00,"bankName":"Test","bankAccountNumber":"12345678",
                 "bankAccountHolder":"Test buyer","idempotencyKey":"local-test-withdrawal"}
                """;
        var buyerSession = user(buyer.getEmail()).roles("USER");
        mvc.perform(get("/api/v1/wallets/" + buyer.getId() + "/balance")
                        .with(user(seller.getEmail()).roles("USER")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/withdrawals").with(buyerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdraw.replaceFirst("\\{", "{\"userId\":\"" + seller.getId() + "\",")))
                .andExpect(status().isForbidden());

        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/v1/withdrawals").with(buyerSession)
                            .contentType(MediaType.APPLICATION_JSON).content(withdraw))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                    .andExpect(jsonPath("$.data.maskedBankAccountNumber").value("****5678"));
        }
        long withdrawalId = withdrawals.findByIdempotencyKey("wd:" + buyerWallet.getId() + ":local-test-withdrawal")
                .orElseThrow().getId();
        String operation = "/api/v1/withdrawals/" + withdrawalId;
        mvc.perform(patch(operation + "/approve").with(operator)).andExpect(status().isOk());
        mvc.perform(patch(operation + "/complete").with(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        assertEquals(0, buyerWallet.getPendingWithdrawBalance().signum());
    }

    private User testUser() {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setFullName("Payment test");
        return users.save(user);
    }

}
