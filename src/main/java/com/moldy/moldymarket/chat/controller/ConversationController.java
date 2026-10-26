package com.moldy.moldymarket.chat.controller;

import com.moldy.moldymarket.chat.dto.request.StartConversationRequest;
import com.moldy.moldymarket.chat.dto.response.ConversationResponse;
import com.moldy.moldymarket.chat.dto.response.MessageResponse;
import com.moldy.moldymarket.chat.service.ConversationService;
import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Hội thoại (Conversations)",
        description = "Quản lý hội thoại, tạo cuộc trò chuyện và xem lịch sử tin nhắn")
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/conversations")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConversationController {
    ConversationService conversationService;

    @Operation(
            summary = "Tạo hoặc lấy cuộc hội thoại",
            description = "Tạo cuộc hội thoại với người dùng mục tiêu nếu chưa tồn tại, hoặc trả về cuộc hội thoại hiện có."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<ConversationResponse>> startConversation(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody StartConversationRequest request){
        return ResponseEntity.ok(ApiResponse.success(conversationService.startOrGetConversation(user.getUserId(), request)));
    }

    @Operation(
            summary = "Lấy danh sách hội thoại",
            description = "Lấy danh sách các cuộc hội thoại thuộc về người dùng hiện tại."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> getInbox(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user){
        return ResponseEntity.ok(ApiResponse.success(conversationService.getUserInbox(user.getUserId())));
    }

    @Operation(summary = "Lấy lịch sử tin nhắn",
            description = "Lấy lịch sử tin nhắn của một cuộc hội thoại theo phân trang, sắp xếp từ mới nhất đến cũ nhất.")
    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<Page<MessageResponse>>> getMessage(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user,
            @Parameter(description = "ID cuộc hội thoại") @PathVariable UUID conversationId,
            @Parameter(description = "Chỉ số trang (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số lượng tin nhắn mỗi trang") @RequestParam(defaultValue = "4") int size){
        //load messages from newest to oldest
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("sentAt").descending());

        return ResponseEntity.ok(ApiResponse.success(conversationService.getChatHistory(user.getUserId(), conversationId, pageRequest)));
    }
}
