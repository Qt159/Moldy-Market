package com.moldy.moldymarket.config;

import com.moldy.moldymarket.role.entity.Role;
import com.moldy.moldymarket.role.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoleInitializer {

    private static final List<String> SYSTEM_ROLES = List.of(
            "USER", "APPRAISER", "STAFF", "ADMIN", "OWNER"
    );

    private final RoleRepository roleRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initRoles() {
        for (String roleName: SYSTEM_ROLES) {
            if (!roleRepository.existsByName(roleName)) {
                roleRepository.save(new Role(roleName));
                log.info("Role seeded: {}", roleName);
            }
        }
    }
}
