package com.moldy.moldymarket.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class AwsConfig {

    private static final Region REGION = Region.AP_SOUTHEAST_1;

    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder()
                .region(REGION)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .region(REGION)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
