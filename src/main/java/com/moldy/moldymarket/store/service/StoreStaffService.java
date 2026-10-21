package com.moldy.moldymarket.store.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.role.entity.Role;
import com.moldy.moldymarket.role.repository.RoleRepository;
import com.moldy.moldymarket.security.PermissionConstants;
import com.moldy.moldymarket.security.RoleConstants;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import com.moldy.moldymarket.store.dto.request.AddStaffRequest;
import com.moldy.moldymarket.store.dto.request.UpdateStaffPermissionsRequest;
import com.moldy.moldymarket.store.dto.response.StaffResponse;
import com.moldy.moldymarket.store.entity.Store;
import com.moldy.moldymarket.store.entity.StoreStaff;
import com.moldy.moldymarket.store.entity.StoreStatus;
import com.moldy.moldymarket.store.mapper.StoreStaffMapper;
import com.moldy.moldymarket.store.repository.StoreRepository;
import com.moldy.moldymarket.store.repository.StoreStaffRepository;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.entity.UserStatus;
import com.moldy.moldymarket.user.repository.UserRepository;
import com.moldy.moldymarket.userrole.entity.UserRole;
import com.moldy.moldymarket.userrole.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.mapstruct.control.MappingControl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreStaffService {

    private final StoreRepository storeRepository;
    private final StoreStaffRepository storeStaffRepository;
    private final StoreStaffMapper storeStaffMapper;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private static final int MAX_STAFF_PER_STORE = 10;


    // Danh sách các quyền hợp lệ mà Owner có thể ủy quyền cho Staff
    private static final Set<String> ALLOWED_STAFF_PERMISSION = Set.of(
            PermissionConstants.PRODUCT_CREATE,
            PermissionConstants.APPRAISAL_REQUEST_CREATE,
            PermissionConstants.ORDER_PROCESS,
            PermissionConstants.ORDER_CANCEL,
            PermissionConstants.OFFER_HANDLE,
            PermissionConstants.CHAT_SEND
    );

    // --- Thêm nhân viên vào shop -----------------------------
    // ─── 1. Thêm nhân viên vào shop ───────────────────────────────────────────
    @Transactional
    public StaffResponse addStaff(Authentication authentication, AddStaffRequest request) {
        Store store = getOwnedStore(authentication);

        // Quy tắc: Giới hạn tối đa 10 nhân viên cho mỗi shop
        if (storeStaffRepository.countByStoreId(store.getId()) >= MAX_STAFF_PER_STORE) {
            throw new AppException(ErrorCode.STORE_STAFF_LIMIT_REACHED);
        }

        User staffUser = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Quy tắc: Chủ shop không được tự thêm chính mình
        if (staffUser.getId().equals(store.getOwnerId())) {
            throw new AppException(ErrorCode.CANNOT_ADD_SELF_AS_STAFF);
        }

        // Quy tắc: Không cho phép thêm người đã là Chủ gian hàng (Store Owner)
        if (storeRepository.existsByOwnerId(staffUser.getId())) {
            throw new AppException(ErrorCode.CANNOT_ADD_STORE_OWNER_AS_STAFF);
        }

        // Quy tắc: Tài khoản nhân viên phải ACTIVE (đã verify email và không bị khóa)
        if (staffUser.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.STAFF_ACCOUNT_NOT_ACTIVE);
        }

        // Quy tắc: Không cho phép thêm ADMIN làm nhân viên shop
        if (userRoleRepository.existsByUserIdAndRole_Name(staffUser.getId(), RoleConstants.ADMIN)) {
            throw new AppException(ErrorCode.CANNOT_ADD_ADMIN_AS_STAFF);
        }

        // Quy tắc: Không cho phép thêm APPRAISER làm nhân viên shop (tránh xung đột thẩm định)
        if (userRoleRepository.existsByUserIdAndRole_Name(staffUser.getId(), RoleConstants.APPRAISER)) {
            throw new AppException(ErrorCode.CANNOT_ADD_APPRAISER_AS_STAFF);
        }

        // Quy tắc: Người này đã làm nhân viên ở shop khác rồi
        if (storeStaffRepository.findByUserId(staffUser.getId()).isPresent()) {
            throw new AppException(ErrorCode.STAFF_ALREADY_EMPLOYED);
        }

        // Kiểm tra xem đã là nhân viên của chính shop này chưa
        if (storeStaffRepository.existsByStoreIdAndUserId(store.getId(), staffUser.getId())) {
            throw new AppException(ErrorCode.STAFF_ALREADY_EXISTS);
        }

        // Kiểm tra tính hợp lệ của các quyền được truyền lên
        Set<String> requestedPermissions = request.permissions() != null ? request.permissions() : Set.of();
        validatePermissions(requestedPermissions);

        // Tạo tập quyền (luôn kèm STORE_WORKSPACE_ACCESS)
        Set<String> finalPermissions = new HashSet<>(requestedPermissions);
        finalPermissions.add(PermissionConstants.STORE_WORKSPACE_ACCESS);

        StoreStaff staff = new StoreStaff(store, staffUser, finalPermissions);
        StoreStaff savedStaff = storeStaffRepository.save(staff);

        // Gán Role STAFF kèm storeId vào bảng user_roles
        Role staffRole = roleRepository.findByName(RoleConstants.STAFF)
                .orElseThrow(() -> new IllegalStateException("STAFF role not found after seeding"));

        userRoleRepository.save(new UserRole(staffUser, staffRole, store.getId()));

        return storeStaffMapper.toResponse(savedStaff);
    }

    // --- 2. Xem danh sách nhân viên của shop ---------------------------
    public List<StaffResponse> getStoreStaffList(Authentication authentication) {
        Store store = getOwnedStore(authentication);

        return storeStaffRepository.findAllByStoreId(store.getId())
                .stream()
                .map(storeStaffMapper::toResponse)
                .toList();
    }

    // 3. Sửa quyền của nhân viên ------------------------
    @Transactional
    public StaffResponse updateStaffPermissions(
            Authentication authentication,
            UUID staffId,
            UpdateStaffPermissionsRequest request
    ) {
        Store store = getOwnedStore(authentication);

        StoreStaff staff = storeStaffRepository.findByIdAndStoreId(staffId, store.getId())
                .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));

        validatePermissions(request.permissions());

        Set<String> finalPermissions = new HashSet<>(request.permissions());
        finalPermissions.add(PermissionConstants.STORE_WORKSPACE_ACCESS);

        staff.setPermissions(finalPermissions);

        return storeStaffMapper.toResponse(staff);
    }

    // ---4. Xóa nhân viên khỏi shop -------------------------------------
    @Transactional
    public void removeStaff(Authentication authentication, UUID staffId) {
        Store store = getOwnedStore(authentication);

        StoreStaff staff = storeStaffRepository.findByIdAndStoreId(staffId, store.getId())
                .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));

        UUID staffUserId = staff.getUser().getId();

        storeStaffRepository.delete(staff);

        // Thu hồi ROLE STAFF của người này tại shop này
        userRoleRepository.deleteByUserIdAndRole_NameAndStoreId(
                staffUserId,
                RoleConstants.STAFF,
                store.getId()
        );
    }

    // --- 5. Nhân viên xem hồ sơ của mình -------------------------
    public StaffResponse getMyStaffProfile(Authentication authentication) {
        UUID currentUserId = extractUserId(authentication);

        StoreStaff staff = storeStaffRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));

        return storeStaffMapper.toResponse(staff);
    }


    // --- Helper Methods -------------------------------------------
    private Store getOwnedStore(Authentication authentication) {
        UUID ownerId = extractUserId(authentication);
        Store store = storeRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.STORE_NOT_FOUND));

        if (store.getStatus() == StoreStatus.BLOCKED) {
            throw new AppException(ErrorCode.STORE_BLOCKED);
        }

        return store;
    }

    private void validatePermissions(Set<String> permissions) {
        for (String permission : permissions) {
            if (!ALLOWED_STAFF_PERMISSION.contains(permission)) {
                throw new AppException(ErrorCode.INVALID_STAFF_PERMISSION);
            }
        }
    }

    private UUID extractUserId(Authentication authentication) {
        return ((CustomUserDetails) authentication.getPrincipal()).getUserId();
    }
}
