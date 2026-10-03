package com.moldy.moldymarket.userrole.entity;

import com.moldy.moldymarket.role.entity.Role;
import com.moldy.moldymarket.user.entity.User;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(
        name = "user_roles",
        indexes = {
                @Index(
                        name = "idx_user_roles_user_id",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_user_roles_store_id",
                        columnList = "store_id"
                )
        }
)
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    Role role;

    @Column(name = "store_id")
    UUID storeId;

    protected UserRole() {}

    public UserRole(User user, Role role, UUID storeId) {
        this.user = user;
        this.role = role;
        this.storeId = storeId;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Role getRole() {
        return role;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public void setStoreId(UUID storeId) {
        this.storeId = storeId;
    }
}
