package com.moldy.moldymarket.common.storage.controller;

import com.moldy.moldymarket.common.storage.dto.request.PresignedUrlRequest;
import com.moldy.moldymarket.common.storage.dto.response.DownloadUrlResponse;
import com.moldy.moldymarket.common.storage.dto.response.FileResponse;
import com.moldy.moldymarket.common.storage.dto.response.PresignedUrlResponse;
import com.moldy.moldymarket.common.storage.service.FileStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping("/presigned-url")
    public ResponseEntity<PresignedUrlResponse> createUploadUrl(
            @Valid @RequestBody PresignedUrlRequest request,
            Authentication authentication) {
        UUID ownerId = currentUserId(authentication);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fileStorageService.createUploadUrl(ownerId, request));
    }

    @PostMapping("/{fileId}/confirm")
    public ResponseEntity<FileResponse> confirmUpload(
            @PathVariable UUID fileId,
            Authentication authentication) {
        UUID ownerId = currentUserId(authentication);

        return ResponseEntity.ok(
                fileStorageService.confirmUpload(fileId, ownerId));
    }

    @GetMapping("/{fileId}/download-url")
    public ResponseEntity<DownloadUrlResponse> generateDownloadUrl(
            @PathVariable UUID fileId,
            Authentication authentication) {
        UUID requesterId = currentUserId(authentication);

        return ResponseEntity.ok(
                fileStorageService.generateDownloadUrl(fileId, requesterId));
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable UUID fileId,
            Authentication authentication) {
        UUID requesterId = currentUserId(authentication);

        fileStorageService.deleteFile(fileId, requesterId);

        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Authenticated user ID must be a UUID");
        }
    }
}