package com.moldy.moldymarket.security.user;

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

import java.util.List;
import java.util.UUID;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public CustomUserDetailsService(UserRepository userRepository, UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
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

        List<SimpleGrantedAuthority> authorities =
                userRoles.stream()
                        .map(UserRole::getRole)
                        .map(Role::getName)
                        .map(roleName -> new SimpleGrantedAuthority("ROLE_" + roleName))
                        .toList();
        return new CustomUserDetails(
                user,
                authorities
        );
    }


}
