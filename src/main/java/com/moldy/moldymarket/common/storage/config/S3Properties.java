package com.moldy.moldymarket.common.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import software.amazon.awssdk.regions.Region;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "aws.s3")
public class S3Properties {

    @NotBlank
    @Size(min = 3, max = 63)
    @Pattern(regexp = "^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])$")
    private String bucket;

    @NotBlank
    @Pattern(regexp = "^[a-z]{2}(?:-[a-z0-9]+)+-\\d+$", message = "Must be an AWS region identifier")
    private String region;

    @Min(1)
    @Max(10080) // AWS Signature Version 4 presigned URL limit: seven days.
    private long uploadExpirationMinutes = 5;

    @Min(1)
    @Max(10080)
    private long downloadExpirationMinutes = 5;

    @AssertTrue(message = "Bucket name must not contain consecutive dots or look like an IP address")
    public boolean isBucketNameValid() {
        if (bucket == null || bucket.isBlank()) return true;
        return !bucket.contains("..")
                && !bucket.matches("\\d{1,3}(?:\\.\\d{1,3}){3}")
                && !bucket.startsWith("xn--")
                && !bucket.endsWith("-s3alias")
                && !bucket.endsWith("--ol-s3")
                && !bucket.endsWith(".mrap")
                && !bucket.endsWith("--x-s3")
                && !bucket.endsWith("--table-s3")
                && !bucket.matches(".*\\.[.-].*")
                && !bucket.matches(".*[.-]\\..*");
    }

    @AssertTrue(message = "Region must be supported by the AWS SDK")
    public boolean isRegionSupported() {
        if (region == null || region.isBlank()) return true;
        String configuredRegion = region.trim();
        return Region.regions().stream().anyMatch(known -> known.id().equals(configuredRegion));
    }

}
