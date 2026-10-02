package com.moldy.moldymarket.config;

import com.moldy.moldymarket.role.entity.Role;
import com.moldy.moldymarket.role.repository.RoleRepository;
import com.moldy.moldymarket.security.AdminProperties;
import com.moldy.moldymarket.security.RoleConstants;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.entity.UserStatus;
import com.moldy.moldymarket.user.repository.UserRepository;
import com.moldy.moldymarket.userrole.entity.UserRole;
import com.moldy.moldymarket.userrole.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoleInitializer {

    private static final List<String> SYSTEM_ROLES = List.of(
            RoleConstants.USER,
            RoleConstants.APPRAISER,
            RoleConstants.STAFF,
            RoleConstants.ADMIN,
            RoleConstants.OWNER
    );

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    @Order(1)
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void init() {
        initRoles();
        initAdmin();
    }

    public void initRoles() {
        for (String roleName: SYSTEM_ROLES) {
            if (!roleRepository.existsByName(roleName)) {
                roleRepository.save(new Role(roleName));
                log.info("Role seeded: {}", roleName);
            }
        }
    }

    private void initAdmin() {
        String email = adminProperties.email();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            return; // Admin đã tồn tại, không làm gì
        }

        User admin = new User();
        admin.setEmail(email);
        admin.setFullName(adminProperties.fullName());
        admin.setPasswordHash(passwordEncoder.encode(adminProperties.password()));
        admin.setStatus(UserStatus.ACTIVE);
        User savedAdmin = userRepository.save(admin);

        Role adminRole = roleRepository.findByName(RoleConstants.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role not found after seeding"));

        userRoleRepository.save(new UserRole(savedAdmin, adminRole, null));

        log.info("System admin created: {}", email);
    }
}
