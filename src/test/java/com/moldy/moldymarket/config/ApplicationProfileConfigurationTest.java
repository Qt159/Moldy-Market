package com.moldy.moldymarket.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationProfileConfigurationTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void localProfileProvidesLocalDatabaseSwaggerCorsAndDisabledAwsPublishing() throws IOException {
        ConfigurableEnvironment environment = environment("application-local.yaml", Map.of(
                "JWT_SECRET", "local-test-jwt-secret-with-at-least-32-bytes",
                "ADMIN_PASSWORD", "test-only-admin-password",
                "DB_PASSWORD", "local-db-test-password",
                "SEPAY_WEBHOOK_SECRET_KEY", "test-only-local-webhook-secret"
        ));

        assertThat(environment.getProperty("spring.application.name")).isEqualTo("moldy-market");
        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://localhost:5433/moldy_market");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("moldy_user");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("local-db-test-password");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("update");
        assertThat(environment.getProperty("spring.mail.host")).isEqualTo("localhost");
        assertThat(environment.getProperty("spring.mail.port", Integer.class)).isEqualTo(1025);
        assertThat(environment.getProperty("security.jwt.access-token-expiration", Long.class))
                .isEqualTo(900_000L);
        assertThat(environment.getProperty("aws.region")).isEqualTo("ap-south-1");
        assertThat(environment.getProperty("aws.s3.bucket-name")).isEqualTo("moldy-market-local");
        assertThat(environment.getProperty("aws.s3.region")).isEqualTo("ap-south-1");
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("app.cors.allowed-origins")).isEqualTo("http://localhost:5173");
        assertThat(environment.getProperty("app.rate-limit.store")).isEqualTo("memory");
        assertThat(environment.getProperty("notifications.outbox.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("aws.sqs.notification-queue-url")).isEmpty();
        assertThat(environment.getProperty("sePay.webhook.secret-key"))
                .isEqualTo("test-only-local-webhook-secret");
        assertThat(environment.getProperty("brevo.sender-email"))
                .isEqualTo("no-reply@moldymarket.local");
    }

    @Test
    void productionProfileRequiresSecretsAndDisablesSchemaWritesAndApiDocs() throws IOException {
        Map<String, Object> variables = new HashMap<>();
        variables.put("DB_URL", "jdbc:postgresql://db.example:5432/moldy?sslmode=verify-full");
        variables.put("DB_USERNAME", "test-user");
        variables.put("DB_PASSWORD", "test-password");
        variables.put("JWT_SECRET", "production-test-jwt-secret-with-at-least-32-bytes");
        variables.put("ADMIN_EMAIL", "admin@example.test");
        variables.put("ADMIN_PASSWORD", "test-only-admin-password");
        variables.put("CORS_ALLOWED_ORIGINS", "https://frontend.example.test");
        variables.put("AWS_REGION", "ap-south-1");
        variables.put("AWS_S3_BUCKET_NAME", "moldy-market-test");
        variables.put("AWS_DYNAMODB_NOTIFICATIONS_TABLE", "notifications-test");
        variables.put("BREVO_API_KEY", "test-only-brevo-key");
        variables.put("BREVO_SENDER_EMAIL", "sender@example.test");
        variables.put("SEPAY_BANK_ACCOUNT", "0000000000");
        variables.put("SEPAY_BANK_NAME", "TEST");
        variables.put("SEPAY_WEBHOOK_SECRET_KEY", "test-only-webhook-secret");
        variables.put("SPRING_DATA_REDIS_HOST", "redis.example.test");
        variables.put("SPRING_DATA_REDIS_PASSWORD", "test-only-redis-password");

        ConfigurableEnvironment environment = environment("application-prod.yaml", variables);

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://db.example:5432/moldy?sslmode=verify-full");
        assertThat(environment.getProperty("spring.datasource.hikari.data-source-properties.sslmode"))
                .isEqualTo("verify-full");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("app.cors.allowed-origins"))
                .isEqualTo("https://frontend.example.test");
        assertThat(environment.getProperty("app.rate-limit.store")).isEqualTo("redis");
        assertThat(environment.getProperty("spring.data.redis.host")).isEqualTo("redis.example.test");
        assertThat(environment.getProperty("aws.region")).isEqualTo("ap-south-1");
        assertThat(environment.getProperty("aws.s3.bucket-name")).isEqualTo("moldy-market-test");
        assertThat(environment.getProperty("aws.dynamodb.notifications-table"))
                .isEqualTo("notifications-test");
        assertThat(environment.getProperty("notifications.outbox.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("aws.sqs.notification-queue-url")).isEmpty();
        assertThat(environment.getProperty("sePay.webhook.secret-key"))
                .isEqualTo("test-only-webhook-secret");
        assertThat(environment.getProperty("brevo.sender-email")).isEqualTo("sender@example.test");
    }

    @Test
    void productionCorsAndJwtDoNotHaveFallbackSecrets() throws IOException {
        ConfigurableEnvironment environment = environment("application-prod.yaml", Map.of());

        assertThatThrownBy(() -> environment.getProperty("app.cors.allowed-origins"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> environment.getProperty("security.jwt.secret"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ConfigurableEnvironment environment(String profileResource, Map<String, Object> variables)
            throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("test-environment", variables));

        PropertySource<?> shared = loader.load("application", new ClassPathResource("application.yaml")).get(0);
        PropertySource<?> profile = loader.load("profile", new ClassPathResource(profileResource)).get(0);
        environment.getPropertySources().addLast(shared);
        environment.getPropertySources().addBefore(shared.getName(), profile);
        return environment;
    }
}
