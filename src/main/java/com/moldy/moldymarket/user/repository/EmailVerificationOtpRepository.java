package com.moldy.moldymarket.user.repository;

import com.moldy.moldymarket.user.entity.EmailVerificationOtp;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationOtpRepository extends JpaRepository<EmailVerificationOtp, UUID> {
}
