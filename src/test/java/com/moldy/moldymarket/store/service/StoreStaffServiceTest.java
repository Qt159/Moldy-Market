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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreStaffServiceTest {

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private StoreStaffRepository storeStaffRepository;

    @Mock
    private StoreStaffMapper storeStaffMapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @InjectMocks
    private StoreStaffService storeStaffService;

    private UUID ownerId;
    private UUID storeId;
    private Store store;
    private User staffUser;
    private UUID staffUserId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        storeId = UUID.randomUUID();
        staffUserId = UUID.randomUUID();

        store = new Store(ownerId, "Fashion Hub", "Clothes", "789 High St");
        ReflectionTestUtils.setField(store, "id", storeId);

        staffUser = new User();
        staffUser.setId(staffUserId);
        staffUser.setEmail("staff@example.com");
        staffUser.setStatus(UserStatus.ACTIVE);
    }

    private Authentication mockAuth(UUID uid) {
        Authentication auth = mock(Authentication.class);
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUserId()).thenReturn(uid);
        when(auth.getPrincipal()).thenReturn(userDetails);
        return auth;
    }

    @Test
    @DisplayName("should add staff successfully when all business rules are satisfied")
    void should_addStaff_when_valid() {
        // Arrange
        Authentication auth = mockAuth(ownerId);
        AddStaffRequest request = new AddStaffRequest("staff@example.com", Set.of(PermissionConstants.PRODUCT_CREATE));

        when(storeRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(store));
        when(storeStaffRepository.countByStoreId(storeId)).thenReturn(2L);
        when(userRepository.findByEmailIgnoreCase("staff@example.com")).thenReturn(Optional.of(staffUser));
        when(storeRepository.existsByOwnerId(staffUserId)).thenReturn(false);
        when(userRoleRepository.existsByUserIdAndRole_Name(staffUserId, RoleConstants.ADMIN)).thenReturn(false);
        when(userRoleRepository.existsByUserIdAndRole_Name(staffUserId, RoleConstants.APPRAISER)).thenReturn(false);
        when(storeStaffRepository.findByUserId(staffUserId)).thenReturn(Optional.empty());
        when(storeStaffRepository.existsByStoreIdAndUserId(storeId, staffUserId)).thenReturn(false);

        StoreStaff savedStaff = new StoreStaff(store, staffUser, Set.of(PermissionConstants.PRODUCT_CREATE));
        when(storeStaffRepository.save(any(StoreStaff.class))).thenReturn(savedStaff);

        Role staffRole = new Role(RoleConstants.STAFF);
        when(roleRepository.findByName(RoleConstants.STAFF)).thenReturn(Optional.of(staffRole));

        StaffResponse expectedResponse = mock(StaffResponse.class);
        when(storeStaffMapper.toResponse(savedStaff)).thenReturn(expectedResponse);

        // Act
        StaffResponse response = storeStaffService.addStaff(auth, request);

        // Assert
        assertThat(response).isNotNull();
        verify(userRoleRepository).save(any(UserRole.class));
    }

    @Test
    @DisplayName("should throw STORE_STAFF_LIMIT_REACHED when store reaches 10 staff")
    void should_throwStoreStaffLimitReached_when_limitExceeded() {
        // Arrange
        Authentication auth = mockAuth(ownerId);
        AddStaffRequest request = new AddStaffRequest("staff@example.com", Set.of());

        when(storeRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(store));
        when(storeStaffRepository.countByStoreId(storeId)).thenReturn(10L);

        // Act & Assert
        assertThatThrownBy(() -> storeStaffService.addStaff(auth, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STORE_STAFF_LIMIT_REACHED);

        verify(storeStaffRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw CANNOT_ADD_SELF_AS_STAFF when store owner adds self")
    void should_throwCannotAddSelfAsStaff_when_addingSelf() {
        // Arrange
        Authentication auth = mockAuth(ownerId);
        User ownerUser = new User();
        ownerUser.setId(ownerId);
        ownerUser.setEmail("owner@example.com");

        AddStaffRequest request = new AddStaffRequest("owner@example.com", Set.of());
        when(storeRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(store));
        when(storeStaffRepository.countByStoreId(storeId)).thenReturn(1L);
        when(userRepository.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(ownerUser));

        // Act & Assert
        assertThatThrownBy(() -> storeStaffService.addStaff(auth, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_ADD_SELF_AS_STAFF);
    }

    @Test
    @DisplayName("should throw CANNOT_ADD_STORE_OWNER_AS_STAFF when candidate is already another store owner")
    void should_throwCannotAddStoreOwner_when_candidateOwnsStore() {
        // Arrange
        Authentication auth = mockAuth(ownerId);
        AddStaffRequest request = new AddStaffRequest("staff@example.com", Set.of());

        when(storeRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(store));
        when(storeStaffRepository.countByStoreId(storeId)).thenReturn(1L);
        when(userRepository.findByEmailIgnoreCase("staff@example.com")).thenReturn(Optional.of(staffUser));
        when(storeRepository.existsByOwnerId(staffUserId)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> storeStaffService.addStaff(auth, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_ADD_STORE_OWNER_AS_STAFF);
    }

    @Test
    @DisplayName("should throw STAFF_ACCOUNT_NOT_ACTIVE when candidate account is pending or locked")
    void should_throwStaffAccountNotActive_when_candidateNotActive() {
        // Arrange
        Authentication auth = mockAuth(ownerId);
        staffUser.setStatus(UserStatus.LOCKED);

        AddStaffRequest request = new AddStaffRequest("staff@example.com", Set.of());
        when(storeRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(store));
        when(storeStaffRepository.countByStoreId(storeId)).thenReturn(1L);
        when(userRepository.findByEmailIgnoreCase("staff@example.com")).thenReturn(Optional.of(staffUser));
        when(storeRepository.existsByOwnerId(staffUserId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> storeStaffService.addStaff(auth, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STAFF_ACCOUNT_NOT_ACTIVE);
    }

    @Test
    @DisplayName("should throw CANNOT_ADD_ADMIN_AS_STAFF when candidate is admin")
    void should_throwCannotAddAdmin_when_candidateIsAdmin() {
        // Arrange
        Authentication auth = mockAuth(ownerId);
        AddStaffRequest request = new AddStaffRequest("staff@example.com", Set.of());

        when(storeRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(store));
        when(storeStaffRepository.countByStoreId(storeId)).thenReturn(1L);
        when(userRepository.findByEmailIgnoreCase("staff@example.com")).thenReturn(Optional.of(staffUser));
        when(storeRepository.existsByOwnerId(staffUserId)).thenReturn(false);
        when(userRoleRepository.existsByUserIdAndRole_Name(staffUserId, RoleConstants.ADMIN)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> storeStaffService.addStaff(auth, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_ADD_ADMIN_AS_STAFF);
    }

    @Test
    @DisplayName("should throw INVALID_STAFF_PERMISSION when invalid permission is requested")
    void should_throwInvalidStaffPermission_when_permissionNotAllowed() {
        // Arrange
        Authentication auth = mockAuth(ownerId);
        AddStaffRequest request = new AddStaffRequest("staff@example.com", Set.of("SUPER_ADMIN_PERMISSION"));

        when(storeRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(store));
        when(storeStaffRepository.countByStoreId(storeId)).thenReturn(1L);
        when(userRepository.findByEmailIgnoreCase("staff@example.com")).thenReturn(Optional.of(staffUser));
        when(storeRepository.existsByOwnerId(staffUserId)).thenReturn(false);
        when(userRoleRepository.existsByUserIdAndRole_Name(staffUserId, RoleConstants.ADMIN)).thenReturn(false);
        when(userRoleRepository.existsByUserIdAndRole_Name(staffUserId, RoleConstants.APPRAISER)).thenReturn(false);
        when(storeStaffRepository.findByUserId(staffUserId)).thenReturn(Optional.empty());
        when(storeStaffRepository.existsByStoreIdAndUserId(storeId, staffUserId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> storeStaffService.addStaff(auth, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_STAFF_PERMISSION);
    }

    @Test
    @DisplayName("should remove staff and revoke role from user_roles")
    void should_removeStaff_when_valid() {
        // Arrange
        Authentication auth = mockAuth(ownerId);
        UUID staffId = UUID.randomUUID();

        StoreStaff staff = new StoreStaff(store, staffUser, Set.of());
        when(storeRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(store));
        when(storeStaffRepository.findByIdAndStoreId(staffId, storeId)).thenReturn(Optional.of(staff));

        // Act
        storeStaffService.removeStaff(auth, staffId);

        // Assert
        verify(storeStaffRepository).delete(staff);
        verify(userRoleRepository).deleteByUserIdAndRole_NameAndStoreId(staffUserId, RoleConstants.STAFF, storeId);
    }

    @Test
    @DisplayName("should update staff permissions successfully")
    void should_updateStaffPermissions_when_valid() {
        // Arrange
        Authentication auth = mockAuth(ownerId);
        UUID staffId = UUID.randomUUID();
        StoreStaff staff = new StoreStaff(store, staffUser, Set.of(PermissionConstants.PRODUCT_CREATE));

        when(storeRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(store));
        when(storeStaffRepository.findByIdAndStoreId(staffId, storeId)).thenReturn(Optional.of(staff));
        when(storeStaffMapper.toResponse(staff)).thenReturn(mock(StaffResponse.class));

        UpdateStaffPermissionsRequest request = new UpdateStaffPermissionsRequest(Set.of(PermissionConstants.ORDER_PROCESS, PermissionConstants.PRODUCT_MANAGE_OWN));

        // Act
        StaffResponse response = storeStaffService.updateStaffPermissions(auth, staffId, request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(staff.getPermissions()).contains(PermissionConstants.ORDER_PROCESS, PermissionConstants.PRODUCT_MANAGE_OWN, PermissionConstants.STORE_WORKSPACE_ACCESS);
    }
}
