package com.moldy.moldymarket.user.repository;

import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.entity.UserStatus;
import com.moldy.moldymarket.userrole.entity.UserRole;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public final class UserSpecifications {
    private UserSpecifications(){}

    //find users by email, name, or phone (ignore case)
    public static Specification<User> keyword(String keyword){
        return (root, query, cb) -> {
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(cb.lower(root.get("fullName")), pattern),
                    cb.like(root.get("phone"), "%" + keyword.trim() + "%")
            );
        };
    }
    public static Specification<User> hasStatus(UserStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    //use subquery to prevent duplicate when users have many roles
    public static Specification<User> hasRole(String roleName) {
        return (root, query, cb) -> {
            Subquery<Integer> sub = query.subquery(Integer.class);
            var ur = sub.from(UserRole.class);
            sub.select(cb.literal(1)).where(
                    cb.equal(ur.get("user").get("id"), root.get("id")),
                    cb.equal(ur.get("role").get("name"), roleName.trim().toUpperCase(Locale.ROOT))
            );
            return cb.exists(sub);
        };
    }
}
