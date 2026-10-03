package com.moldy.moldymarket.permission.entity;

import com.moldy.moldymarket.role.entity.Role;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
@Entity
@Table(
        name = "role_permissions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_role_permissions_role_permission",
                        columnNames = {"role_id", "permission_id"}
                )
        }
)
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    Role role;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id", nullable = false)
    Permission permission;

    @Enumerated(EnumType.STRING)
    @Column(name = "grant_type", nullable = false, length = 20)
    GrantType grantType = GrantType.FULL;


    public RolePermission(Role role, Permission permission, GrantType grantType) {
        this.role= role;
        this.permission = permission;
        this.grantType = grantType;
    }
}
