package com.moldy.moldymarket.user.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.ratelimit.RateLimitPolicy;
import com.moldy.moldymarket.common.ratelimit.RateLimiter;
import com.moldy.moldymarket.security.refreshToken.service.RefreshTokenService;
import com.moldy.moldymarket.user.entity.EmailVerificationOtp;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.entity.UserStatus;
import com.moldy.moldymarket.user.repository.EmailVerificationOtpRepository;
import com.moldy.moldymarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.endpoints.internal.Value;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final EmailVerificationOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final RateLimiter rateLimiter;
    private final EmailVerificationService emailVerificationService;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public void requestReset(String email, String clientIp) {
        String normalizedEmail = normalizedEmail(email);

        rateLimiter.check(RateLimitPolicy.OTP_SEND_PER_IP, clientIp);
        rateLimiter.check(RateLimitPolicy.OTP_SEND_PER_EMAIL, normalizedEmail);

        User user = userRepository.findByEmailForUpdate(normalizedEmail).orElse(null);
        // Do not reveal whether the email exists or is active.
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return;
        }

        Instant now = Instant.now();
        EmailVerificationOtp otp = otpRepository.findById(user.getId())
                .orElseGet(() -> new EmailVerificationOtp(user.getId()));

        if (otp.getSentAt() != null && now.isBefore(otp.getExpiresAt().plusSeconds(60))) {
            throw new AppException(ErrorCode.OTP_RESEND_TOO_SOON);
        }

        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        otp.replaceCode(passwordEncoder.encode(code), now);
        otpRepository.save(otp);

        String subject = "Moldy Market - Đặt lại mật khẩu";
        String body =  "Mã xác nhận đặt lại mật khẩu Moldy Market của bạn là: " + code
                + "\nMã có hiệu lực trong 5 phút."
                + "\nNếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.";

        emailVerificationService.deliverEmail(user.getEmail(), subject, body);
    }

    @Transactional(noRollbackFor = AppException.class)
    public void resetPassword(String email, String code, String newPassword, String clientIp) {
        String normalizedEmail = normalizedEmail(email);

        rateLimiter.check(RateLimitPolicy.OTP_VERIFY_PER_IP, clientIp);
        rateLimiter.check(RateLimitPolicy.OTP_VERIFY_PER_EMAIL, normalizedEmail);

        User user = userRepository.findByEmailForUpdate(normalizedEmail)
                .orElseThrow(() -> new AppException(ErrorCode.OTP_INVALID));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.OTP_INVALID);
        }

        EmailVerificationOtp otp = otpRepository.findById(user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.OTP_EXPIRED));

        if (!Instant.now().isBefore(otp.getExpiresAt())) {
            throw new AppException(ErrorCode.OTP_EXPIRED);
        }

        if (otp.getFailedAttempts() >= 5) {
            throw new AppException(ErrorCode.OTP_ATTEMPTS_EXCEEDED);
        }

        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            otp.recordFailedAttempt();
            throw new AppException(
                    otp.getFailedAttempts() >= 5
                    ? ErrorCode.OTP_ATTEMPTS_EXCEEDED
                            : ErrorCode.OTP_INVALID
            );
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        otpRepository.delete(otp);
        refreshTokenService.revokeAllForUser(user);
    }


    private String normalizedEmail (String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
