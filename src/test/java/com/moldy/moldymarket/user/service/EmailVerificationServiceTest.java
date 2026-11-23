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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {
    @Mock UserRepository users;
    @Mock EmailVerificationOtpRepository otps;
    @Mock PasswordEncoder encoder;
    @Mock JavaMailSender mail;
    @Mock RateLimiter limiter;

    EmailVerificationService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(users, otps, encoder, mail, limiter, "sender@example.test");
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.test");
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.setPasswordHash("old-password-hash");
    }

    EmailVerificationOtp prepareVerification(Instant sentAt) {
        var otp = new EmailVerificationOtp(user.getId());
        otp.replaceCode("hashed-code", sentAt);
        when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(otps.findById(user.getId())).thenReturn(Optional.of(otp));
        return otp;
    }

    @Test
    void activatesPendingUserAndConsumesCodeWithVerificationRateLimits() {
        var otp = prepareVerification(Instant.now());
        when(encoder.matches("123456", "hashed-code")).thenReturn(true);
        when(encoder.encode("final-password")).thenReturn("final-password-hash");

        service.verify(" USER@example.test ", "123456", "final-password", "test-ip");

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getPasswordHash()).isEqualTo("final-password-hash");
        var order = inOrder(encoder);
        order.verify(encoder).matches("123456", "hashed-code");
        order.verify(encoder).encode("final-password");
        verify(otps).delete(otp);
        verify(limiter).check(RateLimitPolicy.OTP_VERIFY_PER_IP, "test-ip");
        verify(limiter).check(RateLimitPolicy.OTP_VERIFY_PER_EMAIL, user.getEmail());
        verify(limiter, never()).check(eq(RateLimitPolicy.OTP_SEND_PER_EMAIL), anyString());
    }

    @Test
    void activatesPendingUserKeepingExistingPasswordWhenPasswordIsOmitted() {
        var otp = prepareVerification(Instant.now());
        when(encoder.matches("123456", "hashed-code")).thenReturn(true);

        service.verify(" USER@example.test ", "123456", null, "test-ip");

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getPasswordHash()).isEqualTo("old-password-hash");
        verify(encoder, never()).encode(anyString());
        verify(otps).delete(otp);
    }

    @Test
    void rejectsExpiredCodeBeforeCheckingItsHash() {
        prepareVerification(Instant.now().minusSeconds(301));

        assertThatThrownBy(() -> service.verify(user.getEmail(), "123456", "final-password", "test-ip"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OTP_EXPIRED);
        verifyNoInteractions(encoder);
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
    }

    @Test
    void countsIncorrectAttemptsAndRejectsEvenTheCorrectCodeAfterFiveFailures() {
        var otp = prepareVerification(Instant.now());

        for (int attempt = 1; attempt <= 5; attempt++) {
            ErrorCode expected = attempt == 5 ? ErrorCode.OTP_ATTEMPTS_EXCEEDED : ErrorCode.OTP_INVALID;
            assertThatThrownBy(() -> service.verify(user.getEmail(), "000000", "final-password", "test-ip"))
                    .hasFieldOrPropertyWithValue("errorCode", expected);
            assertThat(otp.getFailedAttempts()).isEqualTo(attempt);
        }

        assertThatThrownBy(() -> service.verify(user.getEmail(), "123456", "final-password", "test-ip"))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OTP_ATTEMPTS_EXCEEDED);
        verify(encoder, times(5)).matches("000000", "hashed-code");
        verify(encoder, never()).matches("123456", "hashed-code");
        verify(otps, never()).delete(any());
        assertThat(user.getPasswordHash()).isEqualTo("old-password-hash");
        verify(encoder, never()).encode(anyString());
    }

    @Test
    void replacesOldCodeAndSendsOnlyTheNewCodeByEmail() {
        var otp = prepareVerification(Instant.now().minusSeconds(61));
        otp.recordFailedAttempt();
        when(encoder.encode(anyString())).thenReturn("new-hash");

        service.resend(" USER@example.test ", "test-ip");

        var code = ArgumentCaptor.forClass(String.class);
        verify(encoder).encode(code.capture());
        assertThat(code.getValue()).matches("[0-9]{6}");
        assertThat(otp.getCodeHash()).isEqualTo("new-hash");
        assertThat(otp.getFailedAttempts()).isZero();
        assertThat(otp.getExpiresAt()).isEqualTo(otp.getSentAt().plusSeconds(300));
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly(user.getEmail());
        assertThat(message.getValue().getText()).contains(code.getValue());
    }

    @Test
    void blocksResendingWithinSixtySeconds() {
        prepareVerification(Instant.now());
        assertThatThrownBy(() -> service.resend(user.getEmail(), "test-ip"))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OTP_RESEND_TOO_SOON);
        verifyNoInteractions(mail, encoder);
    }

    @Test
    void doesNotRevealMissingAccountsOnResend() {
        when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.empty());
        service.resend(user.getEmail(), "test-ip");
        verifyNoInteractions(mail, otps);
    }

    @Test
    void neverActivatesBlockedAccounts() {
        user.setStatus(UserStatus.LOCKED);
        when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.verify(user.getEmail(), "123456", "final-password", "test-ip"))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OTP_INVALID);
        verifyNoInteractions(otps, encoder);
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
    }

    @Test
    void refusesToResetAnAlreadyActiveAccountThroughVerification() {
        user.setStatus(UserStatus.ACTIVE);
        when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.verify(user.getEmail(), "123456", "new-password", "test-ip"))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OTP_INVALID);

        assertThat(user.getPasswordHash()).isEqualTo("old-password-hash");
        verifyNoInteractions(otps, encoder);
    }

    @Test
    void reportsMailFailureToTheRegistrationTransaction() {
        when(otps.findById(user.getId())).thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenReturn("hashed-code");
        doThrow(new MailSendException("SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> service.sendInitialCode(user))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_DELIVERY_FAILED);
    }

    EmailVerificationService transactionalProxy(PlatformTransactionManager manager) {
        var interceptor = new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource());
        var factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        return (EmailVerificationService) factory.getProxy();
    }

    @Test
    void commitsTheFailedAttemptInsteadOfRollingItBackOnAnInvalidCode() {
        var otp = prepareVerification(Instant.now());
        var manager = mock(PlatformTransactionManager.class);
        var transaction = mock(TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(transaction);
        var proxy = transactionalProxy(manager);

        assertThatThrownBy(() -> proxy.verify(user.getEmail(), "000000", "final-password", "test-ip"))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OTP_INVALID);

        assertThat(otp.getFailedAttempts()).isEqualTo(1);
        verify(manager).commit(transaction);
        verify(manager, never()).rollback(any());
    }

    @Test
    void rollsBackTheNewCodeWhenSendingMailFails() {
        prepareVerification(Instant.now().minusSeconds(61));
        when(encoder.encode(anyString())).thenReturn("new-hash");
        doThrow(new MailSendException("SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));
        var manager = mock(PlatformTransactionManager.class);
        var transaction = mock(TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(transaction);
        var proxy = transactionalProxy(manager);

        assertThatThrownBy(() -> proxy.resend(user.getEmail(), "test-ip"))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_DELIVERY_FAILED);

        verify(manager).rollback(transaction);
        verify(manager, never()).commit(any());
    }
}
