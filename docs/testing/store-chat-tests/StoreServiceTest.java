package com.moldy.moldymarket.store.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.role.entity.Role;
import com.moldy.moldymarket.role.repository.RoleRepository;
import com.moldy.moldymarket.security.RoleConstants;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import com.moldy.moldymarket.store.dto.request.CreateStoreRequest;
import com.moldy.moldymarket.store.dto.request.UpdateStoreRequest;
import com.moldy.moldymarket.store.dto.request.UpdateStoreStatusRequest;
import com.moldy.moldymarket.store.dto.response.PublicStoreResponse;
import com.moldy.moldymarket.store.dto.response.StoreResponse;
import com.moldy.moldymarket.store.entity.Store;
import com.moldy.moldymarket.store.entity.StoreStatus;
import com.moldy.moldymarket.store.mapper.StoreMapper;
import com.moldy.moldymarket.store.repository.StoreRepository;
import com.moldy.moldymarket.user.entity.User;
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
import org.mapstruct.factory.Mappers;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private StoreMapper storeMapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @InjectMocks
    private StoreService storeService;

    private UUID ownerId;
    private UUID storeId;
    private Store sampleStore;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        storeId = UUID.randomUUID();

        sampleStore = new Store(ownerId, "Tech Store", "Great electronics", "123 Main St");
        ReflectionTestUtils.setField(sampleStore, "id", storeId);
    }

    private Authentication mockAuth(UUID uid) {
        Authentication auth = mock(Authentication.class);
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUserId()).thenReturn(uid);
        when(auth.getPrincipal()).thenReturn(userDetails);
        return auth;
    }

    private StoreResponse createStoreResponse(UUID sId, UUID oId, String name, String desc, String addr, StoreStatus status) {
        StoreResponse res = new StoreResponse();
        res.setId(sId);
        res.setOwnerId(oId);
        res.setName(name);
        res.setDescription(desc);
        res.setPickupAddress(addr);
        res.setStatus(status);
        res.setCreatedAt(Instant.now());
        return res;
    }

    @Test
    @DisplayName("should register store and assign OWNER role with storeId")
    void should_registerStore_when_valid() {
        // Arrange
        Authentication auth = mockAuth(ownerId);
        CreateStoreRequest request = new CreateStoreRequest("Tech Store", "Great electronics", "123 Main St");

        when(storeRepository.existsByOwnerId(ownerId)).thenReturn(false);
        when(storeRepository.save(any(Store.class))).thenReturn(sampleStore);

        User owner = new User();
        owner.setId(ownerId);
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));

        Role ownerRole = new Role(RoleConstants.OWNER);
        when(roleRepository.findByName(RoleConstants.OWNER)).thenReturn(Optional.of(ownerRole));

        StoreResponse expectedResponse = createStoreResponse(storeId, ownerId, "Tech Store", "Great electronics", "123 Main St", StoreStatus.ACTIVE);
        when(storeMapper.toResponse(sampleStore)).thenReturn(expectedResponse);

        // Act
        StoreResponse response = storeService.registerStore(auth, request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(storeId);
        verify(userRoleRepository).save(any(UserRole.class));
    }

    @Test
    @DisplayName("should throw STORE_ALREADY_EXISTS when owner already has a store")
    void should_throwStoreAlreadyExists_when_userAlreadyOwnsStore() {
        // Arrange
        Authentication auth = mockAuth(ownerId);
        CreateStoreRequest request = new CreateStoreRequest("Another Store", "desc", "addr");
        when(storeRepository.existsByOwnerId(ownerId)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> storeService.registerStore(auth, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STORE_ALREADY_EXISTS);

        verify(storeRepository, never()).save(any());
    }

    @Test
    @DisplayName("should update store successfully when name is not taken")
    void should_updateMyStore_when_valid() {
        // Arrange
        Authentication auth = mockAuth(ownerId);
        UpdateStoreRequest request = new UpdateStoreRequest("Updated Store", "New description", "456 New St");

        when(storeRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(sampleStore));
        when(storeRepository.existsByNameIgnoreCase("Updated Store")).thenReturn(false);

        StoreResponse expectedResponse = createStoreResponse(storeId, ownerId, "Updated Store", "New description", "456 New St", StoreStatus.ACTIVE);
        when(storeMapper.toResponse(sampleStore)).thenReturn(expectedResponse);

        // Act
        StoreResponse response = storeService.updateMyStore(auth, request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(sampleStore.getName()).isEqualTo("Updated Store");
    }

    @Test
    @DisplayName("should throw STORE_BLOCKED when updating blocked store")
    void should_throwStoreBlocked_when_updatingBlockedStore() {
        // Arrange
        Authentication auth = mockAuth(ownerId);
        sampleStore.changeStatus(StoreStatus.BLOCKED);
        when(storeRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(sampleStore));

        // Act & Assert
        assertThatThrownBy(() -> storeService.updateMyStore(auth, new UpdateStoreRequest("Name", "Desc", "Addr")))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STORE_BLOCKED);
    }

    @Test
    @DisplayName("should throw STORE_NAME_TAKEN when updating to duplicate store name")
    void should_throwStoreNameTaken_when_nameExists() {
        // Arrange
        Authentication auth = mockAuth(ownerId);
        when(storeRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(sampleStore));
        when(storeRepository.existsByNameIgnoreCase("Taken Name")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> storeService.updateMyStore(auth, new UpdateStoreRequest("Taken Name", "Desc", "Addr")))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STORE_NAME_TAKEN);
    }

    @Test
    @DisplayName("should return public store when store is not blocked")
    void should_returnPublicStore_when_active() {
        // Arrange
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(sampleStore));
        PublicStoreResponse expectedResponse = Mappers.getMapper(StoreMapper.class).toPublicResponse(sampleStore);
        when(storeMapper.toPublicResponse(sampleStore)).thenReturn(expectedResponse);

        // Act
        PublicStoreResponse response = storeService.getPublicStore(storeId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(storeId);
        assertThat(response.name()).isEqualTo(sampleStore.getName());
    }

    @Test
    @DisplayName("should throw STORE_BLOCKED when viewing public store that is blocked")
    void should_throwStoreBlocked_when_publicStoreBlocked() {
        // Arrange
        sampleStore.changeStatus(StoreStatus.BLOCKED);
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(sampleStore));

        // Act & Assert
        assertThatThrownBy(() -> storeService.getPublicStore(storeId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STORE_BLOCKED);
    }

    @Test
    @DisplayName("should update store status by admin")
    void should_updateStoreStatus_when_adminInvokes() {
        // Arrange
        UpdateStoreStatusRequest request = new UpdateStoreStatusRequest(StoreStatus.BLOCKED);
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(sampleStore));
        when(storeMapper.toResponse(sampleStore)).thenReturn(mock(StoreResponse.class));

        // Act
        storeService.updateStoreStatus(storeId, request);

        // Assert
        assertThat(sampleStore.getStatus()).isEqualTo(StoreStatus.BLOCKED);
    }
}
