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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class EmailVerificationService {
    private static final String BREVO_SEND_URL = "https://api.brevo.com/v3/smtp/email";

    private final UserRepository userRepository;
    private final EmailVerificationOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final RateLimiter rateLimiter;
    private final String mailFrom;
    private final String brevoApiKey;
    private final String brevoSenderName;
    private final RestClient restClient;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(
            UserRepository userRepository,
            EmailVerificationOtpRepository otpRepository,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender,
            RateLimiter rateLimiter,
            String mailFrom
    ) {
        this(userRepository, otpRepository, passwordEncoder, mailSender, rateLimiter, mailFrom, "", "Moldy Market");
    }

    @Autowired
    public EmailVerificationService(
            UserRepository userRepository,
            EmailVerificationOtpRepository otpRepository,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender,
            RateLimiter rateLimiter,
            @Value("${app.mail.from}") String mailFrom,
            @Value("${app.mail.brevo.api-key:}") String brevoApiKey,
            @Value("${app.mail.brevo.sender-name:Moldy Market}") String brevoSenderName
    ) {
        this(userRepository, otpRepository, passwordEncoder, mailSender, rateLimiter,
                mailFrom, brevoApiKey, brevoSenderName, createBrevoRestClient());
    }

    EmailVerificationService(
            UserRepository userRepository,
            EmailVerificationOtpRepository otpRepository,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender,
            RateLimiter rateLimiter,
            String mailFrom,
            String brevoApiKey,
            String brevoSenderName,
            RestClient restClient
    ) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.rateLimiter = rateLimiter;
        this.mailFrom = mailFrom;
        this.brevoApiKey = brevoApiKey == null ? "" : brevoApiKey.trim();
        this.brevoSenderName = brevoSenderName == null || brevoSenderName.isBlank()
                ? "Moldy Market" : brevoSenderName.trim();
        this.restClient = restClient;
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
        if (password != null && !password.isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(password));
        }

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

        String subject = "Moldy Market - Xác nhận email";
        String body = "Mã xác nhận email Moldy Market của bạn là: " + code
                + "\nMã có hiệu lực trong 5 phút."
                + "\nNếu bạn không yêu cầu mã này, hãy bỏ qua email.";

        deliverEmail(user.getEmail(), subject, body);
    }

    private void deliverEmail(String toEmail, String subject, String body) {
        if (!brevoApiKey.isEmpty()) {
            try {
                Map<String, Object> payload = Map.of(
                        "sender", Map.of("name", brevoSenderName, "email", mailFrom),
                        "to", List.of(Map.of("email", toEmail)),
                        "subject", subject,
                        "textContent", body
                );
                restClient.post()
                        .uri(BREVO_SEND_URL)
                        .header("api-key", brevoApiKey)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (Exception exception) {
                log.error("Failed to send verification email via Brevo transactional API");
                throw new AppException(ErrorCode.EMAIL_DELIVERY_FAILED);
            }
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
        } catch (MailException exception) {
            log.error("Failed to send verification email via SMTP");
            throw new AppException(ErrorCode.EMAIL_DELIVERY_FAILED);
        }
    }

    private static RestClient createBrevoRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
