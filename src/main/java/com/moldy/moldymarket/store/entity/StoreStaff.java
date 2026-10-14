package com.moldy.moldymarket.store.entity;

import com.moldy.moldymarket.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "store_staff",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_store_staff_user",
                        columnNames = {"store_id", "user_id"}
                )
        }
)
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreStaff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @ElementCollection(fetch =  FetchType.EAGER)
    @CollectionTable(
            name = "store_staff_permissions",
            joinColumns = @JoinColumn(name = "staff_id")
    )
    @Column(name = "permission_code", nullable = false, length = 100)
    Set<String> permissions = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();

    public StoreStaff(Store store, User user, Set<String> permissions) {
        this.store = store;
        this.user = user;
        if (permissions != null) {
            this.permissions.addAll(permissions);
        }
    }
}
