package com.moldy.moldymarket.notification.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration(proxyBeanMethods = false)
public class NotificationDynamoDbConfig {
    @Bean
    @ConditionalOnMissingBean(DynamoDbClient.class)
    DynamoDbClient notificationDynamoDbClient(@Value("${aws.region:ap-south-1}") String region,
            @Value("${aws.dynamodb.endpoint:}") String endpoint) {
        var builder = DynamoDbClient.builder().region(Region.of(region))
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(8))
                        .apiCallAttemptTimeout(Duration.ofSeconds(3)));
        if (!endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
        return builder.build();
    }
}
