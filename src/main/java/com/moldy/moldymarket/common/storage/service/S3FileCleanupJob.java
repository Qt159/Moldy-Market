package com.moldy.moldymarket.common.storage.service;

import com.moldy.moldymarket.common.storage.config.S3Properties;
import com.moldy.moldymarket.common.storage.entity.StoredFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@ConditionalOnProperty(name = "storage.cleanup.enabled", havingValue = "true")
@Component
public class S3FileCleanupJob {

    private final StoredFileStateService stateService;
    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final Duration pendingExpiration;
    private final int batchSize;

    public S3FileCleanupJob(
            StoredFileStateService stateService,
            S3Client s3Client,
            S3Properties s3Properties,
            @Value("${storage.cleanup.pending-expiration:24h}") Duration pendingExpiration,
            @Value("${storage.cleanup.batch-size:100}") int batchSize) {
        if (pendingExpiration.isNegative() || pendingExpiration.isZero()
                || batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException("Invalid storage cleanup configuration");
        }
        this.stateService = stateService;
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
        this.pendingExpiration = pendingExpiration;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${storage.cleanup.interval:1h}")
    public void cleanExpiredUploadsAndRetryDeletes() {
        LocalDateTime cutoff = LocalDateTime.now().minus(pendingExpiration);
        StoredFileStateService.DeletionBatch batch = stateService.claimDeletionBatch(cutoff, batchSize);
        for (StoredFile candidate : batch.files()) {
            deleteFromS3(candidate, batch.claimToken());
        }
    }

    private void deleteFromS3(StoredFile file, java.util.UUID claimToken) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(file.getObjectKey())
                    .build());
            stateService.deletionSucceeded(file.getId(), claimToken);
            log.info("Stored file deletion completed. fileId={}, purpose={}",
                    file.getId(), file.getPurpose());
        } catch (SdkException exception) {
            if (isNotFound(exception)) {
                stateService.deletionSucceeded(file.getId(), claimToken);
                return;
            }
            stateService.deletionFailed(file.getId(), claimToken);
            int status = awsStatus(exception) == null ? 0 : awsStatus(exception);
            if (status >= 400 && status < 500 && status != 408 && status != 429) {
                log.error("Stored file deletion failed with a non-retryable service response. fileId={}, awsStatus={}",
                        file.getId(), status);
            } else {
                log.warn("Stored file deletion will be retried. fileId={}, awsStatus={}",
                        file.getId(), awsStatus(exception));
            }
        }
    }

    private boolean isNotFound(SdkException exception) {
        return exception instanceof AwsServiceException aws
                && aws.statusCode() == 404;
    }

    private Integer awsStatus(SdkException exception) {
        return exception instanceof AwsServiceException aws ? aws.statusCode() : null;
    }
}
