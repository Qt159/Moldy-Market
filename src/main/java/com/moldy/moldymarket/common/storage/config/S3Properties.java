package com.moldy.moldymarket.common.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "aws.s3")
public class S3Properties {

    private String bucket;
    private String region;
    private long uploadExpirationMinutes = 5;
    private long downloadExpirationMinutes = 5;
}