package com.moldy.moldymarket.user.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.ratelimit.RateLimitPolicy;
import com.moldy.moldymarket.common.ratelimit.RateLimiter;
import com.moldy.moldymarket.user.entity.EmailVerificationOtp;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.entity.UserStatus;
import com.moldy.moldymarket.user.repository.EmailVerificationOtpRepository;
import com.moldy.moldymarket.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

@Service
public class EmailVerificationService {
    private final UserRepository userRepository;
    private final EmailVerificationOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final RateLimiter rateLimiter;
    private final String mailFrom;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(
            UserRepository userRepository,
            EmailVerificationOtpRepository otpRepository,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender,
            RateLimiter rateLimiter,
            @Value("${app.mail.from}") String mailFrom
    ) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.rateLimiter = rateLimiter;
        this.mailFrom = mailFrom;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void sendInitialCode(User user) {
        rateLimiter.check(RateLimitPolicy.OTP_SEND_PER_EMAIL, normalizeEmail(user.getEmail()));

        replaceAndSendCode(user);
    }

    @Transactional
    public void resend(String email, String clientIp) {
        String normalizedEmail = normalizeEmail(email);

        rateLimiter.check(RateLimitPolicy.OTP_SEND_PER_IP, clientIp);
        rateLimiter.check(RateLimitPolicy.OTP_SEND_PER_EMAIL, normalizedEmail);

        User user = userRepository.findByEmailForUpdate(normalizedEmail)
                .orElse(null);

        if (user == null || user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            return;
        }

        replaceAndSendCode(user);
    }

    @Transactional(noRollbackFor = AppException.class)
    public void verify(String email, String code, String password, String clientIp) {
        String normalizedEmail = normalizeEmail(email);

        rateLimiter.check(RateLimitPolicy.OTP_VERIFY_PER_IP, clientIp);
        rateLimiter.check(RateLimitPolicy.OTP_VERIFY_PER_EMAIL, normalizedEmail);

        User user = userRepository.findByEmailForUpdate(normalizedEmail)
                .orElseThrow(() -> new AppException(ErrorCode.OTP_INVALID));

        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
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
        user.setPasswordHash(passwordEncoder.encode(password));

        user.setStatus(UserStatus.ACTIVE);
        otpRepository.delete(otp);
    }

    //------------------------ Helpers ------------------

    private void replaceAndSendCode(User user) {
        Instant now = Instant.now();
        EmailVerificationOtp otp = otpRepository.findById(user.getId())
                .orElseGet(()-> new EmailVerificationOtp(user.getId()));

        if (otp.getSentAt() != null && now.isBefore(otp.getSentAt().plusSeconds((60)))) {
            throw new AppException(ErrorCode.OTP_RESEND_TOO_SOON);
        }

        String code = String.format(
                Locale.ROOT,
                "%06d",
                random.nextInt(1_000_000)
        );

        otp.replaceCode(passwordEncoder.encode(code), now);
        otpRepository.save(otp);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(user.getEmail());
        message.setSubject("Moldy Market - Xác nhận email");
        message.setText(
                "Mã xác nhận email Moldy Market của bạn là: " + code
                        + "\nMã có hiệu lực trong 5 phút."
                        + "\nNếu bạn không yêu cầu mã này, hãy bỏ qua email."
        );

        try {
            mailSender.send(message);
        } catch (MailException exception) {
            throw new AppException(ErrorCode.EMAIL_DELIVERY_FAILED);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
