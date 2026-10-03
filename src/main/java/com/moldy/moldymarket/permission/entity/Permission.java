package com.moldy.moldymarket.permission.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(
        name = "permissions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_permissions_code",
                        columnNames = "code"
                )
        }
)
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "code", nullable = false, length = 100)
    String code;

    @Column(name = "description", length = 255)
    String description;

    protected Permission(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public Permission() {

    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
