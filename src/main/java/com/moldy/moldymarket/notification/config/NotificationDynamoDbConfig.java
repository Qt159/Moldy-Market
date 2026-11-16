package com.moldy.moldymarket.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
class NotificationDynamoDbConfig {

    @Bean
    DynamoDbClient notificationDynamoDbClient(@Value("${aws.region}") String region) {
        return DynamoDbClient.builder().region(Region.of(region)).build();
    }
}
