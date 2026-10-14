package com.moldy.moldymarket.store.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.response.PagedResponse;
import com.moldy.moldymarket.role.entity.Role;
import com.moldy.moldymarket.role.repository.RoleRepository;
import com.moldy.moldymarket.security.RoleConstants;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import com.moldy.moldymarket.store.dto.request.CreateStoreRequest;
import com.moldy.moldymarket.store.dto.request.UpdateStoreRequest;
import com.moldy.moldymarket.store.dto.request.UpdateStoreStatusRequest;
import com.moldy.moldymarket.store.dto.response.StoreResponse;
import com.moldy.moldymarket.store.entity.Store;
import com.moldy.moldymarket.store.entity.StoreStatus;
import com.moldy.moldymarket.store.mapper.StoreMapper;
import com.moldy.moldymarket.store.repository.StoreRepository;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.repository.UserRepository;
import com.moldy.moldymarket.userrole.entity.UserRole;
import com.moldy.moldymarket.userrole.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

    private final StoreRepository storeRepository;
    private final StoreMapper storeMapper;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    // Mở gian hàng
    @Transactional
    public StoreResponse registerStore(Authentication authentication, CreateStoreRequest request) {
        UUID userId = extractUserId(authentication);

        if (storeRepository.existsByOwnerId(userId)) {
            throw new AppException(ErrorCode.STORE_ALREADY_EXISTS);
        }

        Store store = new Store(userId, request.name(),
                request.description(), request.pickupAddress());

        Store savedStore = storeRepository.save(store);

        // Cấp role OWNER và gắn kèm storeId cho user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Role ownerRole = roleRepository.findByName(RoleConstants.OWNER)
                .orElseThrow(() -> new IllegalStateException("OWNER role not found after seeding"));

        userRoleRepository.save(new UserRole(user, ownerRole, savedStore.getId()));

        return storeMapper.toResponse(savedStore);
    }

    // Cập nhật shop
    @Transactional
    public StoreResponse updateMyStore(Authentication authentication, UpdateStoreRequest request) {
        UUID userId = extractUserId(authentication);

        Store store = storeRepository.findByOwnerId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.STORE_NOT_FOUND));

        if (store.getStatus() == StoreStatus.BLOCKED) {
            throw new AppException(ErrorCode.STORE_BLOCKED);
        }

        if (!store.getName().equalsIgnoreCase(request.name())
            && storeRepository.existsByNameIgnoreCase(request.name())) {
            throw new AppException(ErrorCode.STORE_NAME_TAKEN);
        }

        store.update(request.name(), request.description(), request.pickupAddress());

        return storeMapper.toResponse(store);
    }

    // Xem shop của mình
    public StoreResponse getMyStore(Authentication authentication) {
        UUID userId = extractUserId(authentication);

        Store store = storeRepository.findByOwnerId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.STORE_NOT_FOUND));

        return storeMapper.toResponse(store);
    }

    // Xem shop
    public StoreResponse getStoreById(UUID storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.STORE_NOT_FOUND));

        return storeMapper.toResponse(store);
    }

    // Admin: Xem danh sách tất cả shop
    public PagedResponse<StoreResponse> getAllStores(Pageable pageable) {
        Slice<Store> slice = storeRepository.findAllBy(pageable);

        List<StoreResponse> content = slice.stream()
                .map(storeMapper::toResponse)
                .toList();

        return  PagedResponse.of(slice, content, pageable);
    }

    // Admin khóa, mở shop
    @Transactional
    public StoreResponse updateStoreStatus(UUID storeId, UpdateStoreStatusRequest request) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.STORE_NOT_FOUND));

        store.changeStatus(request.status());

        return storeMapper.toResponse(store);
    }


    // Helper --------------------------------------
    private UUID extractUserId(Authentication authentication) {
        return ((CustomUserDetails) authentication.getPrincipal()).getUserId();
    }
}
