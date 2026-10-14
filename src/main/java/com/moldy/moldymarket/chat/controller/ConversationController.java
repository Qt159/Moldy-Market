package com.moldy.moldymarket.chat.controller;

import com.moldy.moldymarket.chat.dto.request.StartConversationRequest;
import com.moldy.moldymarket.chat.dto.response.ConversationResponse;
import com.moldy.moldymarket.chat.dto.response.MessageResponse;
import com.moldy.moldymarket.chat.service.ConversationService;
import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.security.user.CustomUserDetails;
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

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/conversations")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConversationController {
    ConversationService conversationService;

    @PostMapping
    public ResponseEntity<ApiResponse<ConversationResponse>> startConversation(@AuthenticationPrincipal CustomUserDetails user,
                                                                               @Valid @RequestBody StartConversationRequest request){
        return ResponseEntity.ok(ApiResponse.success(conversationService.startOrGetConversation(user.getUserId(), request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> getInbox(@AuthenticationPrincipal CustomUserDetails user){
        return ResponseEntity.ok(ApiResponse.success(conversationService.getUserInbox(user.getUserId())));
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<Page<MessageResponse>>> getMessage(@AuthenticationPrincipal CustomUserDetails user,
                                                            @PathVariable UUID conversationId,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "4") int size){
        //load messages from newest to oldest
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("sentAt").descending());

        return ResponseEntity.ok(ApiResponse.success(conversationService.getChatHistory(user.getUserId(), conversationId, pageRequest)));
    }


}
