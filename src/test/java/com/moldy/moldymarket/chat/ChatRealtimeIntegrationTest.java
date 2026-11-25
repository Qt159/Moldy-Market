package com.moldy.moldymarket.chat;

import com.moldy.moldymarket.chat.entity.Conversation;
import com.moldy.moldymarket.chat.enums.ConversationStatus;
import com.moldy.moldymarket.chat.mapper.MessageMapper;
import com.moldy.moldymarket.chat.service.ConversationService;
import com.moldy.moldymarket.chat.dto.request.StartConversationRequest;
import com.moldy.moldymarket.role.entity.Role;
import com.moldy.moldymarket.store.entity.Store;
import com.moldy.moldymarket.store.entity.StoreStaff;
import com.moldy.moldymarket.security.jwt.JwtService;
import com.moldy.moldymarket.user.entity.*;
import com.moldy.moldymarket.userrole.entity.UserRole;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.*;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "checkout.expiration.enabled=false", "sePay.bank.name=VCB",
        "sePay.bank.account=0000000001", "sePay.webhook.secret-key=chat-test-only"
})
@AutoConfigureMockMvc
class ChatRealtimeIntegrationTest {
    @LocalServerPort int port;
    @Autowired JwtService jwt;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @Autowired SimpUserRegistry registry;
    @Autowired MockMvc mvc;
    @Autowired ConversationService conversations;
    @MockitoSpyBean MessageMapper mapper;
    @Autowired PlatformTransactionManager transactions;
    @PersistenceContext EntityManager em;
    WebSocketStompClient client;
    List<StompSession> sessions = new ArrayList<>();
    List<User> actors = new ArrayList<>();
    UUID roomId;
    TransactionTemplate tx;

    @BeforeEach void setup() {
        tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            Role userRole = em.createQuery("select r from Role r where r.name='USER'", Role.class).getSingleResult();
            for (int i = 0; i < 3; i++) {
                var user = new User(); user.setEmail("realtime-" + UUID.randomUUID() + "@example.test");
                user.setStatus(UserStatus.ACTIVE); user.setFullName("Người dùng " + i); em.persist(user);
                em.persist(new UserRole(user, userRole, null));
                actors.add(user);
            }
            var room = new Conversation(); room.setParticipant1Id(actors.get(0).getId());
            room.setParticipant2Id(actors.get(1).getId()); room.setLastMessageAt(Instant.EPOCH);
            em.persist(room); em.flush(); roomId = room.getId();
        });
        client = new WebSocketStompClient(new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        client.setMessageConverter(new StringMessageConverter() {
            @Override protected boolean supportsMimeType(MessageHeaders headers) { return true; }
        });
    }

    @AfterEach void cleanup() {
        for (StompSession session : sessions) if (session.isConnected()) session.disconnect();
        if (client != null) client.stop();
        tx.executeWithoutResult(status -> {
            var actorIds = actors.stream().map(User::getId).toList();
            var roomIds = em.createQuery("select c.id from Conversation c where c.participant1Id in :ids or c.participant2Id in :ids", UUID.class)
                    .setParameter("ids", actorIds).getResultList();
            if (!roomIds.isEmpty()) {
                em.createQuery("delete from Message m where m.conversation.id in :ids").setParameter("ids", roomIds).executeUpdate();
                em.createQuery("delete from Conversation c where c.id in :ids").setParameter("ids", roomIds).executeUpdate();
            }
            em.createQuery("delete from StoreStaff s where s.user.id in :ids").setParameter("ids", actorIds).executeUpdate();
            em.createQuery("delete from Store s where s.ownerId in :ids").setParameter("ids", actorIds).executeUpdate();
            em.createQuery("delete from UserRole ur where ur.user.id in :ids").setParameter("ids", actorIds).executeUpdate();
            for (User actor : actors) em.remove(em.find(User.class, actor.getId()));
        });
    }

    @Test void textTravelsToBothParticipantsWithStableIdentityAndPersists() throws Exception {
        var sender = connect(actors.get(0)); var recipient = connect(actors.get(1));
        var sent = subscribe(sender, actors.get(0), "/user/queue/messages");
        var received = subscribe(recipient, actors.get(1), "/user/queue/messages");
        send(sender, "{\"type\":\"TEXT\",\"content\":\"Xin chào từ STOMP\"}");
        var first = json.readTree(Objects.requireNonNull(sent.poll(10, TimeUnit.SECONDS), "Sender echo missing"));
        var second = json.readTree(Objects.requireNonNull(received.poll(10, TimeUnit.SECONDS), "Recipient message missing"));
        assertThat(first.get("id").asLong()).isEqualTo(second.get("id").asLong());
        assertThat(first.get("conversationId").asText()).isEqualTo(roomId.toString());
        assertThat(first.get("senderId").asText()).isEqualTo(actors.get(0).getId().toString());
        assertThat(second.get("content").asText()).isEqualTo("Xin chào từ STOMP");
        assertThat(Instant.parse(first.get("sentAt").asText())).isBeforeOrEqualTo(Instant.now());
        tx.executeWithoutResult(status -> {
            assertThat(em.createQuery("select count(m) from Message m where m.conversation.id=:id", Long.class)
                    .setParameter("id", roomId).getSingleResult()).isEqualTo(1);
            assertThat(em.find(Conversation.class, roomId).getLastMessageAt()).isAfter(Instant.EPOCH);
        });
    }

    @Test void closedBlankAndForeignMessagesReturnErrorsWithoutSaving() throws Exception {
        var sender = connect(actors.get(0)); var foreign = connect(actors.get(2));
        var senderErrors = subscribe(sender, actors.get(0), "/user/queue/errors");
        var foreignErrors = subscribe(foreign, actors.get(2), "/user/queue/errors");
        send(foreign, "{\"type\":\"TEXT\",\"content\":\"Not a member\"}");
        assertThat(json.readTree(Objects.requireNonNull(foreignErrors.poll(10, TimeUnit.SECONDS))).get("code").asText()).isEqualTo("CHAT_002");
        send(sender, "{\"type\":\"TEXT\",\"content\":\"   \"}");
        assertThat(json.readTree(Objects.requireNonNull(senderErrors.poll(10, TimeUnit.SECONDS))).get("code").asText()).isEqualTo("COMMON_002");
        send(sender, "{\"type\":null,\"content\":\"Invalid type\"}");
        assertThat(json.readTree(Objects.requireNonNull(senderErrors.poll(10, TimeUnit.SECONDS))).get("code").asText()).isEqualTo("COMMON_002");
        tx.executeWithoutResult(status -> em.find(Conversation.class, roomId).setStatus(ConversationStatus.CLOSED));
        send(sender, "{\"type\":\"TEXT\",\"content\":\"Closed room\"}");
        assertThat(json.readTree(Objects.requireNonNull(senderErrors.poll(10, TimeUnit.SECONDS))).get("code").asText()).isEqualTo("COMMON_002");
        tx.executeWithoutResult(status -> assertThat(em.createQuery("select count(m) from Message m where m.conversation.id=:id", Long.class)
                .setParameter("id", roomId).getSingleResult()).isZero());
    }

    @Test void mappingFailureReturnsGenericErrorAndRollsBackTheWholeSend() throws Exception {
        doThrow(new IllegalStateException("Internal test detail")).when(mapper)
                .toMessageResponse(any(com.moldy.moldymarket.chat.entity.Message.class));
        var sender = connect(actors.get(0));
        var errors = subscribe(sender, actors.get(0), "/user/queue/errors");
        send(sender, "{\"type\":\"TEXT\",\"content\":\"Rollback fixture\"}");
        String body = Objects.requireNonNull(errors.poll(10, TimeUnit.SECONDS));
        assertThat(json.readTree(body).get("code").asText()).isEqualTo("COMMON_005");
        assertThat(body).doesNotContain("Internal test detail");
        tx.executeWithoutResult(status -> {
            assertThat(em.createQuery("select count(m) from Message m where m.conversation.id=:id", Long.class)
                    .setParameter("id", roomId).getSingleResult()).isZero();
            assertThat(em.find(Conversation.class, roomId).getLastMessageAt()).isEqualTo(Instant.EPOCH);
        });
    }

    @Test void inboxNamesAreVisibleOnlyToParticipantsAndContainNoContactDetails() throws Exception {
        mvc.perform(get("/api/v1/conversations").header("Authorization", bearer(0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].participant1Name").value("Người dùng 0"))
                .andExpect(jsonPath("$.data[0].participant2Name").value("Người dùng 1"))
                .andExpect(jsonPath("$.data[0].canSend").value(true))
                .andExpect(jsonPath("$.data[0].email").doesNotExist())
                .andExpect(jsonPath("$.data[0].phone").doesNotExist());
        mvc.perform(get("/api/v1/conversations").header("Authorization", bearer(2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(post("/api/v1/conversations").header("Authorization", bearer(1))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetUserId\":\"" + actors.get(0).getId() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(roomId.toString()))
                .andExpect(jsonPath("$.data.participant1Name").value("Người dùng 0"));
    }

    @Test void ownShopStaffCannotStartOrSendButCanChatWithAnotherOwner() throws Exception {
        tx.executeWithoutResult(status -> {
            var shop = new Store(actors.get(0).getId(), "Chat policy " + UUID.randomUUID(), "", "");
            var other = new Store(actors.get(2).getId(), "Other chat " + UUID.randomUUID(), "", "");
            em.persist(shop); em.persist(other);
            em.persist(new StoreStaff(shop, em.find(User.class, actors.get(1).getId()), Set.of()));
        });
        for (int[] pair : List.of(new int[]{0, 1}, new int[]{1, 0})) {
            mvc.perform(post("/api/v1/conversations").header("Authorization", bearer(pair[0]))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"targetUserId\":\"" + actors.get(pair[1]).getId() + "\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CHAT_STORE_MEMBER"));
        }
        mvc.perform(get("/api/v1/conversations").header("Authorization", bearer(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].canSend").value(false))
                .andExpect(jsonPath("$.data[0].sendBlockedReason").value("CHAT_STORE_MEMBER"));
        var sender = connect(actors.get(1)); var errors = subscribe(sender, actors.get(1), "/user/queue/errors");
        send(sender, "{\"type\":\"TEXT\",\"content\":\"Staff customer chat\"}");
        assertThat(json.readTree(Objects.requireNonNull(errors.poll(10, TimeUnit.SECONDS))).get("code").asText()).isEqualTo("CHAT_005");
        mvc.perform(post("/api/v1/conversations").header("Authorization", bearer(1))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetUserId\":\"" + actors.get(2).getId() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.canSend").value(true));
    }

    @Test void unavailableMissingAndSelfRecipientsCannotStartAndInvalidPagingReturns400() throws Exception {
        tx.executeWithoutResult(status -> em.find(User.class, actors.get(1).getId()).setStatus(UserStatus.LOCKED));
        mvc.perform(post("/api/v1/conversations").header("Authorization", bearer(0)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + actors.get(1).getId() + "\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CHAT_PARTICIPANT_UNAVAILABLE"));
        mvc.perform(post("/api/v1/conversations").header("Authorization", bearer(0)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        mvc.perform(post("/api/v1/conversations").header("Authorization", bearer(0)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + actors.get(0).getId() + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CHAT_INVALID_PARTICIPANTS"));
        for (String[] pair : List.of(new String[]{"page", "-1"}, new String[]{"size", "0"}, new String[]{"size", "101"})) {
            mvc.perform(get("/api/v1/conversations/" + roomId + "/messages").header("Authorization", bearer(0)).param(pair[0], pair[1]))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
    }

    @Test void readEndpointActuallyUpdatesOnlyTheCallerAndRejectsOutsiders() throws Exception {
        long messageId = fixtureMessage(1);
        mvc.perform(patch("/api/v1/conversations/" + roomId + "/read").header("Authorization", bearer(2))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"messageId\":" + messageId + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CHAT_NOT_PARTICIPANT"));
        mvc.perform(patch("/api/v1/conversations/" + roomId + "/read").header("Authorization", bearer(0))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"messageId\":" + messageId + "}"))
                .andExpect(status().isNoContent());
        tx.executeWithoutResult(status -> {
            var room = em.find(Conversation.class, roomId);
            assertThat(room.getLastReadAtParticipant1()).isNotNull();
            assertThat(room.getLastReadAtParticipant2()).isNull();
        });
    }

    @Test void unreadCountsOnlyIncomingAndReadIsBoundedMonotonicAndPrivate() throws Exception {
        mvc.perform(get("/api/v1/conversations").header("Authorization", bearer(0)))
                .andExpect(jsonPath("$.data[0].unreadCount").value(0));
        long first = fixtureMessage(1);
        long second = fixtureMessage(1);
        fixtureMessage(0);
        mvc.perform(get("/api/v1/conversations").header("Authorization", bearer(0)))
                .andExpect(jsonPath("$.data[0].unreadCount").value(2));
        var a = connect(actors.get(0)); var b = connect(actors.get(1));
        var ownReceipts = subscribe(a, actors.get(0), "/user/queue/chat-reads");
        var peerReceipts = subscribe(b, actors.get(1), "/user/queue/chat-reads");
        readThrough(0, first);
        var receipt = json.readTree(Objects.requireNonNull(peerReceipts.poll(10, TimeUnit.SECONDS)));
        assertThat(receipt.get("messageId").asLong()).isEqualTo(first);
        assertThat(receipt.get("readerId").asText()).isEqualTo(actors.get(0).getId().toString());
        assertThat(ownReceipts.poll(10, TimeUnit.SECONDS)).isNotNull();
        mvc.perform(get("/api/v1/conversations").header("Authorization", bearer(0)))
                .andExpect(jsonPath("$.data[0].unreadCount").value(1))
                .andExpect(jsonPath("$.data[0].lastReadMessageIdParticipant1").value(first))
                .andExpect(jsonPath("$.data[0].lastReadMessageIdParticipant2").isEmpty());
        readThrough(0, second);
        assertThat(json.readTree(Objects.requireNonNull(peerReceipts.poll(10, TimeUnit.SECONDS))).get("messageId").asLong()).isEqualTo(second);
        readThrough(0, first);
        assertThat(peerReceipts.poll(200, TimeUnit.MILLISECONDS)).isNull();
        mvc.perform(get("/api/v1/conversations").header("Authorization", bearer(0)))
                .andExpect(jsonPath("$.data[0].unreadCount").value(0))
                .andExpect(jsonPath("$.data[0].lastReadMessageIdParticipant1").value(second));
        mvc.perform(get("/api/v1/conversations").header("Authorization", bearer(1)))
                .andExpect(jsonPath("$.data[0].unreadCount").value(1));
        mvc.perform(patch("/api/v1/conversations/" + roomId + "/read").header("Authorization", bearer(0))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"messageId\":9223372036854775807}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/conversations/" + roomId + "/read").header("Authorization", bearer(0)))
                .andExpect(status().isBadRequest());
    }

    @Test void rolledBackReadDoesNotNotifyOrChangeUnreadAndForeignMessageCannotBeAcknowledged() throws Exception {
        long messageId = fixtureMessage(1);
        var peer = connect(actors.get(1));
        var receipts = subscribe(peer, actors.get(1), "/user/queue/chat-reads");
        tx.executeWithoutResult(status -> {
            conversations.markAsRead(roomId, actors.get(0).getId(), messageId);
            status.setRollbackOnly();
        });
        assertThat(receipts.poll(200, TimeUnit.MILLISECONDS)).isNull();
        mvc.perform(get("/api/v1/conversations").header("Authorization", bearer(0)))
                .andExpect(jsonPath("$.data[0].unreadCount").value(1))
                .andExpect(jsonPath("$.data[0].lastReadMessageIdParticipant1").isEmpty());
        UUID other = conversations.startOrGetConversation(actors.get(0).getId(), new StartConversationRequest(actors.get(2).getId())).id();
        mvc.perform(patch("/api/v1/conversations/" + other + "/read").header("Authorization", bearer(0))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"messageId\":" + messageId + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test void legacyTimestampReadsRemainReadWhenSwitchingToIdsAndClosedHistoryCanBeRead() throws Exception {
        long first = fixtureMessage(1); long second = fixtureMessage(1);
        tx.executeWithoutResult(status -> {
            var room = em.find(Conversation.class, roomId);
            room.setLastReadAtParticipant1(Instant.parse("2026-01-01T01:00:00Z"));
            room.setStatus(ConversationStatus.CLOSED);
        });
        mvc.perform(get("/api/v1/conversations").header("Authorization", bearer(0)))
                .andExpect(jsonPath("$.data[0].unreadCount").value(0));
        readThrough(0, first);
        mvc.perform(get("/api/v1/conversations").header("Authorization", bearer(0)))
                .andExpect(jsonPath("$.data[0].unreadCount").value(0))
                .andExpect(jsonPath("$.data[0].lastReadMessageIdParticipant1").value(second));
        fixtureMessage(1);
        mvc.perform(get("/api/v1/conversations").header("Authorization", bearer(0)))
                .andExpect(jsonPath("$.data[0].unreadCount").value(1));
    }

    private long fixtureMessage(int sender) {
        return tx.execute(status -> {
            var message = new com.moldy.moldymarket.chat.entity.Message();
            message.setConversation(em.find(Conversation.class, roomId));
            message.setSenderId(actors.get(sender).getId());
            message.setType(com.moldy.moldymarket.chat.enums.MessageType.TEXT);
            message.setContent("Read fixture");
            // Equal timestamps prove the cursor uses stable IDs rather than clock precision.
            message.setSentAt(Instant.parse("2026-01-01T00:00:00Z"));
            em.persist(message); em.flush(); return message.getId();
        });
    }

    private void readThrough(int actor, long messageId) throws Exception {
        mvc.perform(patch("/api/v1/conversations/" + roomId + "/read").header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"messageId\":" + messageId + "}"))
                .andExpect(status().isNoContent());
    }

    @Test void concurrentStartsInBothDirectionsCreateOneRoom() throws Exception {
        var executor = Executors.newFixedThreadPool(2); var ready = new CountDownLatch(2); var go = new CountDownLatch(1);
        try {
            var a = executor.submit(() -> { ready.countDown(); go.await(); return conversations.startOrGetConversation(
                    actors.get(0).getId(), new StartConversationRequest(actors.get(2).getId())).id(); });
            var b = executor.submit(() -> { ready.countDown(); go.await(); return conversations.startOrGetConversation(
                    actors.get(2).getId(), new StartConversationRequest(actors.get(0).getId())).id(); });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); go.countDown();
            assertThat(a.get(15, TimeUnit.SECONDS)).isEqualTo(b.get(15, TimeUnit.SECONDS));
            tx.executeWithoutResult(status -> assertThat(em.createQuery(
                    "select count(c) from Conversation c where (c.participant1Id=:a and c.participant2Id=:b) or (c.participant1Id=:b and c.participant2Id=:a)", Long.class)
                    .setParameter("a", actors.get(0).getId()).setParameter("b", actors.get(2).getId()).getSingleResult()).isEqualTo(1));
        } finally { go.countDown(); executor.shutdownNow(); }
    }

    private String bearer(int actor) { return "Bearer " + jwt.generateAccessToken(actors.get(actor)); }

    private StompSession connect(User user) throws Exception {
        var headers = new StompHeaders(); headers.add("Authorization", "Bearer " + jwt.generateAccessToken(user));
        var session = client.connectAsync("http://127.0.0.1:" + port + "/chat", new org.springframework.web.socket.WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() {}).get(10, TimeUnit.SECONDS);
        sessions.add(session); return session;
    }

    private BlockingQueue<String> subscribe(StompSession session, User user, String destination) throws Exception {
        BlockingQueue<String> result = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { result.add((String) payload); }
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            var registered = registry.getUser(user.getId().toString());
            if (registered != null && registered.getSessions().stream().flatMap(value -> value.getSubscriptions().stream())
                    .anyMatch(value -> destination.equals(value.getDestination()))) return result;
            Thread.sleep(10);
        }
        throw new AssertionError("Subscription not ready");
    }

    private void send(StompSession session, String payload) {
        var headers = new StompHeaders(); headers.setDestination("/app/conversations/" + roomId + "/send");
        headers.setContentType(MimeTypeUtils.APPLICATION_JSON); session.send(headers, payload);
    }
}
