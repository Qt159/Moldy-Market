package com.moldy.moldymarket.store.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.store.entity.Store;
import com.moldy.moldymarket.store.entity.StoreStaff;
import com.moldy.moldymarket.store.entity.StoreStatus;
import com.moldy.moldymarket.store.repository.StoreRepository;
import com.moldy.moldymarket.store.repository.StoreStaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StoreAccessGuard {

    private final StoreRepository storeRepository;
    private final StoreStaffRepository storeStaffRepository;

    /** Owner của chính shop, hoặc Staff (của đúng shop này) được cấp permissionCode. */
    public Store requireStoreAccess(UUID storeId, UUID userId, String permissionCode) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.STORE_NOT_FOUND));

        if (store.getStatus() == StoreStatus.BLOCKED) {
            throw new AppException(ErrorCode.STORE_BLOCKED);
        }

        if (store.getOwnerId().equals(userId)) {
            return store;
        }

        boolean authorized = storeStaffRepository.findByUserId(userId)
                .filter(staff -> staff.getStore().getId().equals(storeId))
                .map(staff -> staff.getPermissions().contains(permissionCode))
                .orElse(false);

        if (!authorized) {
            throw new AppException(ErrorCode.STORE_ACCESS_DENIED);
        }

        return store;
    }

    /** True nếu user là Owner hoặc Staff của đúng shop (active) và có permissionCode. */
    public boolean hasStorePermission(UUID storeId, UUID userId, String permissionCode) {
        if (storeId == null || userId == null) {
            return false;
        }
        Store store = storeRepository.findById(storeId).orElse(null);
        if (store == null || store.getStatus() == StoreStatus.BLOCKED) {
            return false;
        }
        if (store.getOwnerId().equals(userId)) {
            return true;
        }
        return storeStaffRepository.findByUserId(userId)
                .filter(staff -> staff.getStore().getId().equals(storeId))
                .map(staff -> staff.getPermissions().contains(permissionCode))
                .orElse(false);
    }

    /** Trả về storeId đang hoạt động mà user có quyền permissionCode (Owner hoặc delegated Staff). */
    public Optional<UUID> findAccessibleStoreId(UUID userId, String permissionCode) {
        if (userId == null) {
            return Optional.empty();
        }
        Optional<UUID> ownedStoreId = storeRepository.findByOwnerId(userId)
                .filter(store -> store.getStatus() != StoreStatus.BLOCKED)
                .map(Store::getId);
        if (ownedStoreId.isPresent()) {
            return ownedStoreId;
        }
        return storeStaffRepository.findByUserId(userId)
                .filter(staff -> staff.getStore() != null
                        && staff.getStore().getStatus() != StoreStatus.BLOCKED
                        && staff.getPermissions().contains(permissionCode))
                .map(staff -> staff.getStore().getId());
    }

    /** True nếu user là Owner hoặc Staff của shop (không xét permission cụ thể). */
    public boolean isStoreMember(UUID storeId, UUID userId) {
        boolean isOwner = storeRepository.findById(storeId)
                .map(store -> store.getOwnerId().equals(userId))
                .orElse(false);
        if (isOwner) {
            return true;
        }

        return storeStaffRepository.findByUserId(userId)
                .map(staff -> staff.getStore().getId().equals(storeId))
                .orElse(false);
    }

}
