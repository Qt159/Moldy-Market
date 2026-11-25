package com.moldy.moldymarket.security.user;

import com.moldy.moldymarket.permission.entity.GrantType;
import com.moldy.moldymarket.permission.repository.RolePermissionRepository;
import com.moldy.moldymarket.role.entity.Role;
import com.moldy.moldymarket.store.entity.StoreStaff;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private StoreStaffRepository storeStaffRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    private User sampleUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sampleUser = new User();
        sampleUser.setId(userId);
        sampleUser.setEmail("user@example.com");
        sampleUser.setPasswordHash("encodedHash");
        sampleUser.setStatus(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("should load UserDetails with roles, role permissions and store staff permissions")
    void should_loadUserDetails_when_userExistsWithRolesAndPermissions() {
        // Arrange
        when(userRepository.findByEmailIgnoreCase("USER@EXAMPLE.COM")).thenReturn(Optional.of(sampleUser));

        UUID roleId = UUID.randomUUID();
        Role role = new Role("SELLER");
        ReflectionTestUtils.setField(role, "id", roleId);

        UserRole userRole = new UserRole(sampleUser, role, null);
        when(userRoleRepository.findAllByUserId(userId)).thenReturn(List.of(userRole));

        when(rolePermissionRepository.findPermissionCodesByRoleIdsAndGrantTypeIn(
                List.of(roleId),
                List.of(GrantType.FULL, GrantType.CONDITIONAL)
                )).thenReturn(List.of("PRODUCT:CREATE", "PRODUCT:UPDATE"));

        StoreStaff staff = mock(StoreStaff.class);
        when(staff.getPermissions()).thenReturn(Set.of("STORE:MANAGE_ORDERS"));
        when(storeStaffRepository.findByUserId(userId)).thenReturn(Optional.of(staff));

        // Act
        UserDetails userDetails = customUserDetailsService.loadUserByUsername("  USER@EXAMPLE.COM  ");

        // Assert
        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo("user@example.com");

        List<String> authorityNames = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorityNames).contains(
                "ROLE_SELLER",
                "PRODUCT:CREATE",
                "PRODUCT:UPDATE",
                "STORE:MANAGE_ORDERS"
        );
    }

    @Test
    @DisplayName("should throw UsernameNotFoundException when user does not exist by email")
    void should_throwUsernameNotFound_when_emailNotFound() {
        // Arrange
        when(userRepository.findByEmailIgnoreCase("notfound@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("notfound@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("should load user by UUID when user exists")
    void should_loadUserById_when_userExists() {
        // Arrange
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userRoleRepository.findAllByUserId(userId)).thenReturn(Collections.emptyList());
        when(storeStaffRepository.findByUserId(userId)).thenReturn(Optional.empty());

        // Act
        CustomUserDetails userDetails = customUserDetailsService.loadUserById(userId);

        // Assert
        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUserId()).isEqualTo(userId);
        assertThat(userDetails.getUser()).isEqualTo(sampleUser);
    }

    @Test
    @DisplayName("should throw UsernameNotFoundException when user does not exist by ID")
    void should_throwUsernameNotFound_when_userIdNotFound() {
        // Arrange
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> customUserDetailsService.loadUserById(unknownId))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found");
    }
}
