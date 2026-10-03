package com.moldy.moldymarket.security.user;

import com.moldy.moldymarket.permission.entity.GrantType;
import com.moldy.moldymarket.permission.repository.RolePermissionRepository;
import com.moldy.moldymarket.role.entity.Role;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.repository.UserRepository;
import com.moldy.moldymarket.userrole.entity.UserRole;
import com.moldy.moldymarket.userrole.repository.UserRoleRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public CustomUserDetailsService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            RolePermissionRepository rolePermissionRepository
    ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email)
        throws UsernameNotFoundException {
        User user = userRepository
                .findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return buildUserDetails(user);
    }

    @Transactional(readOnly = true)
    public CustomUserDetails loadUserById(UUID userId) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return buildUserDetails(user);
    }

    private CustomUserDetails buildUserDetails(User user) {
        List<UserRole> userRoles = userRoleRepository.findAllByUserId(user.getId());

        // 1. Chuyển các Role thành authority: ROLE_USER, ROLE_ADMIN,...
        List<SimpleGrantedAuthority> authorities = new ArrayList<>(
                userRoles.stream()
                        .map(UserRole::getRole)
                        .map(Role::getName)
                        .map(roleName -> new SimpleGrantedAuthority("ROLE_" + roleName))
                        .toList()
        );

        // 2. Lấy danh sách ID của các role mà user đang nắm giữ
        List<UUID> roleIds = userRoles.stream()
                .map(ur -> ur.getRole().getId())
                .toList();

        // 3. Nạp tất cả Permission có grantType = FULL của các role đó
        if (!roleIds.isEmpty()) {
            List<SimpleGrantedAuthority> permissionAuthorities = rolePermissionRepository
                    .findPermissionCodesByRoleIdsAndGrantType(roleIds, GrantType.FULL)
                    .stream()
                    .distinct()
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            authorities.addAll(permissionAuthorities);
        }

        return new CustomUserDetails(user, authorities);

    }


}
