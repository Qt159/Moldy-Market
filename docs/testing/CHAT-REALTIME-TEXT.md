# Chat realtime TEXT — code Backend để Chu chép

> Tài liệu chép code cũ, chỉ giữ để học. Chu đã cho phép sửa trực tiếp phần chat; bản hiện tại có tên participant và ChatAccessPolicy. Không chép các snapshot cũ dưới đây đè lên service/controller hiện tại. Xem CHAT-NAMES-AND-POLICY.md.

Ngày: 2026-11-21. Trạng thái: IMPLEMENTED_LOCAL sau khi Chu chép, trợ lý đối chiếu và sửa các lỗi chép.

Bản code dưới đây ban đầu được thử trên working tree review riêng; nay đã cài tests và kiểm tra Backend chính. Full Maven verify/package: 300 tests, 299 pass, 1 QA-010 disabled có sẵn, 0 failures/errors. Trong đó có 12 tests chat mới; 3 tests chạy server cổng ngẫu nhiên + SockJS/STOMP thật + PostgreSQL và tự dọn fixture. Các lỗi chép đã sửa: regex SEND, gọi findForMessage có lock, bỏ save thứ hai, import validation exception của messaging và broadcast=false cho domain error. Helper authenticate cũ không còn dùng đã bỏ. Các bước chép dưới đây lưu lại để học; Chu không cần chép lại.

## Cách chép

1. Thay toàn bộ nội dung 5 file dưới đây theo thứ tự 1 đến 5. Giữ đúng package/path trong project Backend hiện tại, không tạo thêm project hoặc package cùng tên.
2. Các file Java giống hệt cũng nằm trong docs/testing/chat-realtime-code/ để mở và copy trực tiếp. Không chép thư mục này vào src; chỉ thay nội dung từng file đích.
3. Tests do trợ lý quản lý. Chu không cần chép tests. Bản tests đã kiểm tra lưu trong docs/testing/chat-realtime-tests/; trợ lý sẽ cài vào src/test sau khi đối chiếu code Chu đã chép.
4. IntelliJ: reload Maven nếu cần, build/rebuild để MapStruct sinh lại implementation, rồi restart Backend. Không chỉnh MessageMapperImpl trong target/generated-sources.
5. Báo “xong” để trợ lý đối chiếu code và nối frontend gửi/nhận. Hiện UI vẫn chỉ đọc; bước này chưa bật nút gửi trên frontend.

Không cần sửa database, ErrorCode, SendMessageRequest, WebSocketConfig, application.properties hoặc .env.local cho bước này. Giữ các cấu hình local hiện có.

## Contract sau khi chép

- SockJS endpoint /chat. STOMP CONNECT có native header Authorization: Bearer <accessToken>; không đặt token trong URL.
- SEND /app/conversations/{conversationId}/send với { "type": "TEXT", "content": "Xin chào" }.
- SUBSCRIBE /user/queue/messages để nhận MessageResponse cho cả sender và recipient. conversationId là ID thật của hội thoại; senderId do server lấy từ principal. Tin nhận sau khi server lưu thành công là dấu hiệu đã được server ghi nhận, chưa có read receipt.
- SUBSCRIBE /user/queue/errors để nhận {code,errorMessage,timestamp}. code dùng ErrorCode.getCode(): CHAT_002 khi sender không thuộc hội thoại, COMMON_002 khi TEXT rỗng hoặc hội thoại CLOSED, COMMON_005 cho lỗi nội bộ. Đây là mã STOMP, khác tên enum trong REST.
- Token hết hạn hoặc tài khoản bị khóa sẽ bị từ chối ở SEND/SUBSCRIBE tiếp theo. Frontend cần lấy token mới và mở lại kết nối. Không tự gửi lại tin khi mất kết nối: kết quả cũ có thể đã được lưu; đối chiếu REST history trước để tránh trùng.
- Kênh lỗi dùng broadcast=false: chỉ session đã gửi yêu cầu nhận lỗi. Tin thành công được gửi cho các session của đúng hai tài khoản như flow hiện có.
- Giữ API REST history/inbox. PATCH đánh dấu đã đọc vẫn chưa triển khai trong task này. Không thêm flow ảnh/file, đính kèm sản phẩm hoặc hộp thư chung gian hàng.

## 1. MessageMapper.java

File đích: src/main/java/com/moldy/moldymarket/chat/mapper/MessageMapper.java

Map conversation.id sang conversationId để frontend biết tin thuộc hội thoại nào. attachment chưa có dữ liệu sản phẩm để map; phần này vẫn giữ null như trước.

```java
package com.moldy.moldymarket.chat.mapper;

import com.moldy.moldymarket.chat.dto.request.SendMessageRequest;
import com.moldy.moldymarket.chat.dto.response.MessageResponse;
import com.moldy.moldymarket.chat.entity.Message;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MessageMapper {
    Message toMessage(SendMessageRequest request);
    @Mapping(target = "conversationId", source = "conversation.id")
    @Mapping(target = "attachment", ignore = true)
    MessageResponse toMessageResponse(Message message);
}
```

## 2. ConversationRepository.java

File đích: src/main/java/com/moldy/moldymarket/chat/repository/ConversationRepository.java

Thêm findForMessage với row lock. Các tin gửi đồng thời trong cùng hội thoại lần lượt cập nhật lastMessageAt, tránh ghi đè bằng thời gian cũ.

```java
package com.moldy.moldymarket.chat.repository;

import com.moldy.moldymarket.chat.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> findForMessage(@Param("id") UUID id);


    //finds if a chat already existed between two people, regardless of who started it
    @Query("select c from Conversation c where (c.participant1Id = :user1Id and c.participant2Id = :user2Id) or (c.participant1Id = :user2Id and c.participant2Id = :user1Id)")
    Optional<Conversation> findByParticipant1IdAndParticipant2Id(@Param("user1Id") UUID user1Id, @Param("user2Id") UUID user2Id);

    //gets the user's inbox, sorted by the most recent messages
    @Query("select c from Conversation c where c.participant1Id = :userId or c.participant2Id = :userId order by c.lastMessageAt desc")
    List<Conversation> findAllByUserId(@Param("userId") UUID userId);

    @Query("""
        select count(c) > 0 from Conversation c
        where c.id = :conversationId
          and (c.participant1Id = :userId or c.participant2Id = :userId)
        """)
    boolean existsByIdAndParticipant(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);
}
```

## 3. MessageService.java

File đích: src/main/java/com/moldy/moldymarket/chat/service/MessageService.java

Kiểm tra participant, trạng thái ACTIVE và TEXT không rỗng. @Transactional lưu message và cập nhật lastMessageAt cùng một transaction; lỗi thì rollback cả hai.

```java
package com.moldy.moldymarket.chat.service;

import com.moldy.moldymarket.chat.dto.request.SendMessageRequest;
import com.moldy.moldymarket.chat.dto.response.MessageResponse;
import com.moldy.moldymarket.chat.entity.Conversation;
import com.moldy.moldymarket.chat.entity.Message;
import com.moldy.moldymarket.chat.enums.ConversationStatus;
import com.moldy.moldymarket.chat.enums.MessageType;
import com.moldy.moldymarket.chat.mapper.MessageMapper;
import com.moldy.moldymarket.chat.repository.ConversationRepository;
import com.moldy.moldymarket.chat.repository.MessageRepository;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MessageService {
    MessageRepository messageRepository;
    ConversationRepository conversationRepository;
    UserRepository userRepository;
    MessageMapper messageMapper;


    @Transactional
    public MessageResponse saveMessage(UUID conversationId, UUID senderId, SendMessageRequest request) {
        // Serialize sends in the same conversation so lastMessageAt cannot move backwards.
        Conversation conversation = conversationRepository.findForMessage(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_EXISTED));
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        boolean isUserParticipant = sender.getId().equals(conversation.getParticipant1Id()) || sender.getId().equals(conversation.getParticipant2Id());

        if(!isUserParticipant) throw new AppException(ErrorCode.SENDER_NOT_IN_CONVERSATION);

        if (conversation.getStatus() != ConversationStatus.ACTIVE) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Conversation is closed");
        }
        if (request == null || request.type() == null ||
                (request.type() == MessageType.TEXT &&
                        (request.content() == null || request.content().isBlank()))) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Message content is required");
        }

        Message message = messageMapper.toMessage(request);
        message.setConversation(conversation);
        message.setSenderId(sender.getId());

        Message saved = messageRepository.save(message);
        conversation.setLastMessageAt(saved.getSentAt());
        return messageMapper.toMessageResponse(saved);
    }

    public UUID getRecipientId(UUID conversationId, UUID senderId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_EXISTED));

        if(senderId.equals(conversation.getParticipant1Id())){
            return conversation.getParticipant2Id();
        }
        else if(senderId.equals(conversation.getParticipant2Id())){
            return conversation.getParticipant1Id();
        }
        else throw new AppException(ErrorCode.SENDER_NOT_IN_CONVERSATION);

    }
}
```

## 4. ChatController.java

File đích: src/main/java/com/moldy/moldymarket/chat/controller/ChatController.java

Chỉ lấy senderId từ Principal. Sau khi service commit thành công mới gửi echo cho hai tài khoản. Lỗi gửi tới queue/errors của đúng session; lỗi nội bộ dùng thông báo chung.

```java
package com.moldy.moldymarket.chat.controller;

import com.moldy.moldymarket.chat.dto.request.SendMessageRequest;
import com.moldy.moldymarket.chat.dto.response.ChatErrorResponse;
import com.moldy.moldymarket.chat.dto.response.MessageResponse;
import com.moldy.moldymarket.chat.service.MessageService;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

@Tag(name = "Tin nhắn (Chat Messages)", description = "Gửi nhận tin nhắn qua WebSocket STOMP")
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/chat-messages")
public class ChatController {
    private final MessageService messageService;
    private final SimpMessagingTemplate simpMessagingTemplate;

    @MessageMapping("/conversations/{conversationId}/send")
    public void sendMessage(@DestinationVariable UUID conversationId,
                            @Payload @Valid SendMessageRequest request,
                            Principal principal) {
        if (principal == null) throw new AppException(ErrorCode.UNAUTHORIZED);
        UUID senderId = UUID.fromString(principal.getName());
        MessageResponse savedMessage = messageService.saveMessage(conversationId, senderId, request);
        UUID recipientId = messageService.getRecipientId(conversationId, senderId);

        simpMessagingTemplate.convertAndSendToUser(recipientId.toString(), "/queue/messages", savedMessage);
        simpMessagingTemplate.convertAndSendToUser(senderId.toString(), "/queue/messages", savedMessage);
    }

    @MessageExceptionHandler(AppException.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    public ChatErrorResponse handle(AppException exception) {
        return new ChatErrorResponse(exception.getErrorCode().getCode(), exception.getMessage(), Instant.now());
    }

    @MessageExceptionHandler({MethodArgumentNotValidException.class, MessageConversionException.class})
    @SendToUser(value = "/queue/errors", broadcast = false)
    public ChatErrorResponse handleInvalidMessage(Exception exception) {
        return error(ErrorCode.INVALID_REQUEST);
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    public ChatErrorResponse handleUnexpected(Exception exception) {
        log.error("Chat message processing failed: {}", exception.getClass().getName());
        return error(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ChatErrorResponse error(ErrorCode code) {
        return new ChatErrorResponse(code.getCode(), code.getMessage(), Instant.now());
    }
}
```

## 5. StompAuthInterceptor.java

File đích: src/main/java/com/moldy/moldymarket/chat/config/StompAuthInterceptor.java

CONNECT xác thực JWT và lưu token trong session server. SEND/SUBSCRIBE kiểm tra lại token và trạng thái tài khoản. Chỉ cho gửi tới application handler và nghe queue riêng; topic cũ yêu cầu participant.

```java
package com.moldy.moldymarket.chat.config;

import com.moldy.moldymarket.chat.service.ConversationService;
import com.moldy.moldymarket.security.jwt.JwtService;
import com.moldy.moldymarket.security.user.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {
    private static final String SESSION_TOKEN = StompAuthInterceptor.class.getName() + ".token";
    private static final String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final Pattern SEND_DESTINATION = Pattern.compile("^/app/conversations/" + UUID_PATTERN + "/send$");
    private static final Pattern CONVERSATION_TOPIC = Pattern.compile("^/topic/conversations/(" + UUID_PATTERN + ")$");

    private final JwtService jwtService;
    private final CustomUserDetailsService customUserDetailsService;
    private final ConversationService conversationService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (accessor.getCommand() == StompCommand.CONNECT) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new AccessDeniedException("Missing bearer token");
            }
            String token = header.substring(7);
            UUID userId = validateToken(token);
            Map<String, Object> attributes = accessor.getSessionAttributes();
            if (attributes == null) throw new AccessDeniedException("Missing WebSocket session");
            attributes.put(SESSION_TOKEN, token);
            accessor.setUser(new StompPrincipal(userId.toString()));
        } else if (accessor.getCommand() == StompCommand.SEND || accessor.getCommand() == StompCommand.SUBSCRIBE) {
            UUID userId = requireAuthenticatedSession(accessor);
            String destination = accessor.getDestination();
            if (accessor.getCommand() == StompCommand.SEND) {
                if (destination == null || !SEND_DESTINATION.matcher(destination).matches()) {
                    throw new AccessDeniedException("Client may send only to the chat application");
                }
            } else {
                authorizeSubscribe(destination, userId);
            }
        }
        return message;
    }

    private UUID validateToken(String token) {
        try {
            if (!jwtService.isTokenValid(token)) throw new AccessDeniedException("Token is invalid or expired");
            UUID userId = jwtService.extractUserId(token);
            UserDetails user = customUserDetailsService.loadUserById(userId);
            if (!user.isEnabled() || !user.isAccountNonLocked()) {
                throw new AccessDeniedException("Account is disabled or locked");
            }
            return userId;
        } catch (RuntimeException exception) {
            throw new AccessDeniedException("WebSocket authentication failed");
        }
    }

    private UUID requireAuthenticatedSession(StompHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        Object storedToken = attributes == null ? null : attributes.get(SESSION_TOKEN);
        if (accessor.getUser() == null || !(storedToken instanceof String token)) {
            throw new AccessDeniedException("Not authenticated");
        }
        UUID userId = validateToken(token);
        if (!userId.toString().equals(accessor.getUser().getName())) {
            throw new AccessDeniedException("Session identity mismatch");
        }
        return userId;
    }

    private void authorizeSubscribe(String destination, UUID userId) {
        if ("/user/queue/messages".equals(destination) || "/user/queue/errors".equals(destination)) return;

        var topic = CONVERSATION_TOPIC.matcher(destination == null ? "" : destination);
        if (topic.matches() && conversationService.isParticipant(UUID.fromString(topic.group(1)), userId)) return;
        throw new AccessDeniedException("Subscription is not allowed");
    }
}
```

## Bằng chứng kiểm tra

Trước sửa, 6 regression tests có 5 failures: response thiếu conversationId, không cập nhật lastMessageAt, cho gửi TEXT rỗng/hội thoại CLOSED, và không chặn broker destinations. Sau sửa: 4 message tests, 5 STOMP authorization tests và 3 real transport tests pass. Full verify/package trên bản review pass 300 tests (299 pass, 1 disabled có sẵn).

Real transport kiểm tra hai tài khoản nhận cùng message ID, đúng conversationId/senderId/content/sentAt, lưu DB và cập nhật inbox timestamp; non-participant/blank/CLOSED trả queue error mà không lưu; lỗi mapper sau insert trả lỗi chung và rollback message lẫn timestamp. Unit authorization kiểm tra authenticated session được dùng queue riêng/application route, raw queue và queue người khác bị chặn, topic cần membership, token hết hạn bị chặn trên kết nối đã mở.

Backend chính đã được đối chiếu và verify sau khi Chu chép. Tests đã cài trong src/test; bản tests trong docs là tài liệu tham khảo. UI thật vẫn chờ Chu kiểm tra. Không commit/push.

Tham khảo chính thức: [Spring token authentication](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication-token-based.html), [Spring WebSocket security](https://docs.spring.io/spring-security/reference/servlet/integrations/websocket.html), [Spring STOMP client](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/client.html).
