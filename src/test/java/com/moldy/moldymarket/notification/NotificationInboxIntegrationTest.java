package com.moldy.moldymarket.notification;

import com.moldy.moldymarket.security.jwt.JwtService;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.entity.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"checkout.expiration.enabled=false", "sePay.bank.name=VCB",
        "sePay.bank.account=0000000001", "sePay.webhook.secret-key=notification-test-only"})
@AutoConfigureMockMvc
@Transactional
@Testcontainers
@Import(NotificationInboxIntegrationTest.LocalDynamo.class)
class NotificationInboxIntegrationTest {
    static final String TABLE = "notification-contract-" + UUID.randomUUID();
    @Container static final GenericContainer<?> DYNAMO = new GenericContainer<>("amazon/dynamodb-local:latest")
            .withExposedPorts(8000).withCommand("-jar", "DynamoDBLocal.jar", "-inMemory", "-sharedDb");

    @TestConfiguration(proxyBeanMethods = false)
    static class LocalDynamo {
        @Bean @Primary DynamoDbClient notificationTestDynamoDb() {
            return DynamoDbClient.builder().region(Region.US_EAST_1)
                    .endpointOverride(URI.create("http://" + DYNAMO.getHost() + ":" + DYNAMO.getMappedPort(8000)))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("localtest", "localtest")))
                    .build();
        }
    }

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("aws.dynamodb.notifications-table", () -> TABLE);
    }

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired DynamoDbClient db;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @PersistenceContext EntityManager em;
    User viewer, other;

    @BeforeEach void fixtures() {
        if (!db.listTables().tableNames().contains(TABLE)) {
            db.createTable(CreateTableRequest.builder().tableName(TABLE).billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(AttributeDefinition.builder().attributeName("PK").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("SK").attributeType(ScalarAttributeType.S).build())
                    .keySchema(KeySchemaElement.builder().attributeName("PK").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("SK").keyType(KeyType.RANGE).build()).build());
        }
        viewer = user(); other = user();
    }

    @Test void returnsRealLambdaDataWithCallerScopeAndCursorPagination() throws Exception {
        seed(viewer, "2026-11-20T03:00:00Z", false, false);
        seed(viewer, "2026-11-20T02:00:00Z", false, false);
        seed(viewer, "2026-11-20T01:00:00Z", false, false);
        seed(other, "2026-11-20T04:00:00Z", false, false);
        var result = mvc.perform(get("/api/v1/notifications").param("limit", "1").header("Authorization", bearer(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].userId").value(viewer.getId().toString()))
                .andExpect(jsonPath("$.data.items[0].message").value("Fixture content"))
                .andExpect(jsonPath("$.data.items[0].read").value(false))
                .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-11-20T03:00:00Z"))
                .andExpect(jsonPath("$.data.limit").value(1)).andExpect(jsonPath("$.data.hasMore").value(true))
                .andReturn();
        String token = json.readTree(result.getResponse().getContentAsString()).at("/data/nextToken").asText();
        mvc.perform(get("/api/v1/notifications").param("limit", "1").param("token", token).header("Authorization", bearer(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].createdAt").value("2026-11-20T02:00:00Z"));
        mvc.perform(get("/api/v1/notifications").param("token", token).header("Authorization", bearer(other)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAGINATION_TOKEN_USER_MISMATCH"));
    }

    @Test void deletedOrExpiredPageMayBeEmptyAndStillHaveANextCursor() throws Exception {
        seed(viewer, "2026-11-20T03:00:00Z", true, false);
        seed(viewer, "2026-11-20T02:00:00Z", false, true);
        seed(viewer, "2026-11-20T01:00:00Z", false, false);
        var result = mvc.perform(get("/api/v1/notifications").param("limit", "1").header("Authorization", bearer(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.hasMore").value(true)).andReturn();
        String token = json.readTree(result.getResponse().getContentAsString()).at("/data/nextToken").asText();
        mvc.perform(get("/api/v1/notifications").param("limit", "1").param("token", token).header("Authorization", bearer(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.hasMore").value(true));
    }

    @Test void readDeleteAndRestorePersistAndReadIsIdempotent() throws Exception {
        var key = seed(viewer, "2026-11-20T01:00:00Z", false, false);
        String path = "/api/v1/notifications/" + id(key);
        mvc.perform(patch(path + "/read").header("Authorization", bearer(viewer))).andExpect(status().isNoContent());
        var first = item(key);
        assertThat(first.get("isRead").bool()).isTrue();
        assertThat(first.get("readAt").s()).isNotBlank();
        mvc.perform(patch(path + "/read").header("Authorization", bearer(viewer))).andExpect(status().isNoContent());
        assertThat(item(key).get("readAt")).isEqualTo(first.get("readAt"));
        mvc.perform(patch(path + "/unread").header("Authorization", bearer(viewer))).andExpect(status().isNoContent());
        assertThat(item(key).get("isRead").bool()).isFalse();
        assertThat(item(key)).doesNotContainKey("readAt");
        mvc.perform(patch(path + "/read").header("Authorization", bearer(viewer))).andExpect(status().isNoContent());
        mvc.perform(delete(path).header("Authorization", bearer(viewer))).andExpect(status().isNoContent());
        mvc.perform(get(path).header("Authorization", bearer(viewer))).andExpect(status().isNotFound());
        mvc.perform(delete(path).header("Authorization", bearer(viewer)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOTIFICATION_ALREADY_DELETED"));
        mvc.perform(patch(path + "/restore").header("Authorization", bearer(viewer))).andExpect(status().isNoContent());
        mvc.perform(get(path).header("Authorization", bearer(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.read").value(true));
        mvc.perform(patch(path + "/restore").header("Authorization", bearer(viewer)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_DELETED"));
    }

    @Test void foreignIdsCannotReadOrMutateAndMissingIdsNeverCreateItems() throws Exception {
        var key = seed(viewer, "2026-11-20T01:00:00Z", false, false);
        String path = "/api/v1/notifications/" + id(key);
        for (var request : List.of(get(path), patch(path + "/read"), patch(path + "/unread"), delete(path), patch(path + "/restore"))) {
            mvc.perform(request.header("Authorization", bearer(other))).andExpect(status().isNotFound());
        }
        assertThat(item(key).get("isRead").bool()).isFalse();
        assertThat(item(key)).doesNotContainKey("deletedAt");
        var missing = Map.of("PK", s("USER#" + viewer.getId()), "SK", s("NOTIFICATION#2026-01-01T00:00:00Z#missing"));
        mvc.perform(patch("/api/v1/notifications/" + id(missing) + "/read").header("Authorization", bearer(viewer)))
                .andExpect(status().isNotFound());
        assertThat(item(missing)).isEmpty();
    }

    @Test void rejectsInvalidCursorsAnonymousAccessAndExpiredActions() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notifications").param("token", "bad token").header("Authorization", bearer(viewer)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGINATION_TOKEN"));
        var key = seed(viewer, "2026-11-20T01:00:00Z", false, true);
        mvc.perform(patch("/api/v1/notifications/" + id(key) + "/read").header("Authorization", bearer(viewer)))
                .andExpect(status().isNotFound());
    }

    User user() {
        var user = new User(); user.setEmail("notifications-" + UUID.randomUUID() + "@example.test");
        user.setFullName("Notification test"); user.setStatus(UserStatus.ACTIVE); em.persist(user); em.flush(); return user;
    }
    String bearer(User user) { return "Bearer " + jwt.generateAccessToken(user); }
    static AttributeValue s(String value) { return AttributeValue.fromS(value); }
    String id(Map<String, AttributeValue> key) { return Base64.getUrlEncoder().withoutPadding().encodeToString(key.get("SK").s().getBytes(StandardCharsets.UTF_8)); }
    Map<String, AttributeValue> item(Map<String, AttributeValue> key) {
        return db.getItem(GetItemRequest.builder().tableName(TABLE).key(key).consistentRead(true).build()).item();
    }
    Map<String, AttributeValue> seed(User user, String createdAt, boolean deleted, boolean expired) {
        var key = Map.of("PK", s("USER#" + user.getId()), "SK", s("NOTIFICATION#" + createdAt + "#" + UUID.randomUUID()));
        var item = new HashMap<>(key);
        item.putAll(Map.of("notificationId", s(UUID.randomUUID().toString()), "userId", s(user.getId().toString()),
                "type", s("ORDER_CREATED"), "title", s("Fixture title"), "content", s("Fixture content"),
                "referenceType", s("ORDER"), "referenceId", s(UUID.randomUUID().toString()),
                "createdAt", s(createdAt), "isRead", AttributeValue.fromBool(false),
                "expiresAt", AttributeValue.fromN(String.valueOf(Instant.now().getEpochSecond() + (expired ? -60 : 3600)))));
        if (deleted) item.put("deletedAt", s(createdAt));
        db.putItem(PutItemRequest.builder().tableName(TABLE).item(item).build()); return key;
    }
}
