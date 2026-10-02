package com.moldy.moldymarket.config;

import com.moldy.moldymarket.permission.entity.GrantType;
import com.moldy.moldymarket.permission.entity.Permission;
import com.moldy.moldymarket.permission.entity.RolePermission;
import com.moldy.moldymarket.permission.repository.PermissionRepository;
import com.moldy.moldymarket.permission.repository.RolePermissionRepository;
import com.moldy.moldymarket.role.entity.Role;
import com.moldy.moldymarket.role.repository.RoleRepository;
import com.moldy.moldymarket.security.RoleConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.moldy.moldymarket.security.PermissionConstants.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionInitializer {

    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RoleRepository roleRepository;

    // Toàn bộ permission codes cần seed vào DB
    private static final List<String> ALL_PERMISSIONS = List.of(
            // M2
            PROFILE_MANAGE, BANK_ACCOUNT_MANAGE, WORKSPACE_SWITCH,
            // M3
            PRODUCT_CREATE, PRODUCT_MANAGE_OWN, APPRAISAL_REQUEST_CREATE, VOUCHER_SELLER_MANAGE,
            // M4
            APPRAISER_APPLY, APPRAISAL_QUEUE_VIEW, APPRAISAL_TASK_ACCEPT, APPRAISAL_SUBMIT,
            // M5
            OFFER_CREATE, OFFER_HANDLE, OFFER_OVERRIDE_FLOOR,
            // M6
            ORDER_PAY, ORDER_TRACK, ORDER_CONFIRM_RECEIVED, ORDER_PROCESS, ORDER_CANCEL,
            ORDER_RETURN_REQUEST, ORDER_RETURN_HANDLE, DISPUTE_CREATE, DISPUTE_RESPOND,
            REVIEW_CREATE, CHAT_SEND,
            // M7
            WALLET_VIEW, WALLET_WITHDRAW, STORE_STATS_VIEW,
            // M8
            STORE_REGISTER, STORE_STAFF_MANAGE, STORE_WORKSPACE_ACCESS,
            // M9
            ADMIN_PORTAL_ACCESS, CATEGORY_MANAGE, USER_STORE_MANAGE, APPRAISER_APPROVE,
            VOUCHER_SYSTEM_MANAGE, DISPUTE_ADJUDICATE, LEGIT_POINTS_MANAGE, ANALYTICS_SYSTEM_VIEW
            );

    private static final Map<String, List<String>> ROLE_FULL_PERMISSIONS = Map.of(
            RoleConstants.USER, List.of(
                    PROFILE_MANAGE, BANK_ACCOUNT_MANAGE,
                    PRODUCT_MANAGE_OWN, APPRAISAL_REQUEST_CREATE, VOUCHER_SELLER_MANAGE,
                    OFFER_HANDLE,
                    ORDER_PAY, ORDER_TRACK, ORDER_CONFIRM_RECEIVED, ORDER_PROCESS, ORDER_CANCEL,
                    ORDER_RETURN_REQUEST, ORDER_RETURN_HANDLE,
                    DISPUTE_CREATE, DISPUTE_RESPOND, REVIEW_CREATE, CHAT_SEND,
                    WALLET_VIEW, WALLET_WITHDRAW
            ),

            RoleConstants.APPRAISER, List.of(
                    PROFILE_MANAGE, BANK_ACCOUNT_MANAGE, WORKSPACE_SWITCH,
                    APPRAISAL_QUEUE_VIEW,
                    WALLET_VIEW, WALLET_WITHDRAW
            ),

            RoleConstants.OWNER, List.of(
                    PROFILE_MANAGE, BANK_ACCOUNT_MANAGE, WORKSPACE_SWITCH,
                    PRODUCT_CREATE, PRODUCT_MANAGE_OWN, APPRAISAL_REQUEST_CREATE,
                    VOUCHER_SELLER_MANAGE,
                    OFFER_HANDLE, OFFER_OVERRIDE_FLOOR,
                    ORDER_PROCESS, ORDER_CANCEL, ORDER_RETURN_HANDLE,
                    DISPUTE_RESPOND, CHAT_SEND,
                    WALLET_VIEW, WALLET_WITHDRAW, STORE_STATS_VIEW, STORE_STAFF_MANAGE
            ),

            RoleConstants.STAFF, List.of(
                    STORE_WORKSPACE_ACCESS
            ),

            RoleConstants.ADMIN, List.of(
                    ADMIN_PORTAL_ACCESS, CATEGORY_MANAGE, USER_STORE_MANAGE,
                    APPRAISER_APPROVE, VOUCHER_SYSTEM_MANAGE, DISPUTE_ADJUDICATE,
                    LEGIT_POINTS_MANAGE, ANALYTICS_SYSTEM_VIEW
            )
    );

    @Order(2)
    @EventListener(ApplicationEvent.class)
    @Transactional
    public void init() {
        seedPermissions();
        seedRolePermissions();
    }

    // Seed tất cả permission codes vào DB --------------
    private void seedPermissions() {
        for (String code: ALL_PERMISSIONS) {
            if (!permissionRepository.existsByCode(code)) {
                Permission p = new Permission();
                p.setCode(code);
                p.setDescription("");
                permissionRepository.save(p);
                log.info("Permission seeded: {}", code);
            }
        }
    }

    // Seed role -> permission mapping (FULL only)
    private void seedRolePermissions() {
        // Load tất cả permissions vào map tránh n + 1
        Map<String, Permission> permissionMap = permissionRepository
                .findAllByCodeIn(ALL_PERMISSIONS)
                .stream()
                .collect(Collectors.toMap(Permission::getCode, p -> p));

        // Load tất cả roles vào map
        Map<String, Role> roleMap = roleRepository.findAll()
                .stream()
                .collect(Collectors.toMap(Role:: getName, r -> r));

        ROLE_FULL_PERMISSIONS.forEach((roleName, permissionCodes) -> {
            Role role = roleMap.get(roleName);
            if (role == null) {
                log.warn("Role not found during permission seeding: {}", roleName);
                return;
            }
            for (String code : permissionCodes) {
                Permission permission = permissionMap.get(code);
                if (permission == null) continue;
                if (!rolePermissionRepository.existsByRoleAndPermission(role, permission)) {
                    rolePermissionRepository.save(new RolePermission(role, permission, GrantType.FULL));
                }
            }
        });
        log.info("Role-permission mapping seeded");
    }
}
