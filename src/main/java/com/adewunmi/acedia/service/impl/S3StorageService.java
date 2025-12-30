package com.adewunmi.acedia.service.impl;

import com.adewunmi.acedia.config.S3Config;
import com.adewunmi.acedia.config.StorageProperties;
import com.adewunmi.acedia.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
@RequiredArgsConstructor
@Slf4j
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final StorageProperties storageProperties;
    private final S3Config s3Config;
    private final AtomicBoolean bucketInitialized = new AtomicBoolean(false);

    /**
     * Ensure bucket exists before any S3 operation.
     * Only runs once using lazy initialization.
     */
    private void ensureBucketInitialized() {
        if (!bucketInitialized.get()) {
            synchronized (this) {
                if (!bucketInitialized.get()) {
                    s3Config.ensureBucketExists();
                    bucketInitialized.set(true);
                }
            }
        }
    }

    @Override
    public String store(Path localFilePath, String storageKey) throws Exception {
        ensureBucketInitialized();
        String bucketName = storageProperties.getS3().getBucketName();

        log.info("Uploading file to S3: {} -> s3://{}/{}", localFilePath, bucketName, storageKey);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(storageKey)
                .build();

        s3Client.putObject(putRequest, RequestBody.fromFile(localFilePath));

        String url = getUrl(storageKey);
        log.info("Successfully uploaded to S3: {}", url);

        return url;
    }

    @Override
    public String store(InputStream inputStream, String storageKey, long contentLength) throws Exception {
        ensureBucketInitialized();
        String bucketName = storageProperties.getS3().getBucketName();

        log.info("Uploading stream to S3: s3://{}/{}", bucketName, storageKey);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(storageKey)
                .contentLength(contentLength)
                .build();

        s3Client.putObject(putRequest, RequestBody.fromInputStream(inputStream, contentLength));

        String url = getUrl(storageKey);
        log.info("Successfully uploaded to S3: {}", url);

        return url;
    }

    @Override
    public InputStream retrieve(String storageKey) throws Exception {
        String bucketName = storageProperties.getS3().getBucketName();

        log.info("Retrieving file from S3: s3://{}/{}", bucketName, storageKey);

        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(storageKey)
                .build();

        return s3Client.getObject(getRequest);
    }

    @Override
    public void delete(String storageKey) throws Exception {
        String bucketName = storageProperties.getS3().getBucketName();

        log.info("Deleting file from S3: s3://{}/{}", bucketName, storageKey);

        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(storageKey)
                .build();

        s3Client.deleteObject(deleteRequest);
        log.info("Successfully deleted from S3: {}", storageKey);
    }

    @Override
    public boolean exists(String storageKey) throws Exception {
        String bucketName = storageProperties.getS3().getBucketName();

        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .build();

            s3Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public String getUrl(String storageKey) {
        String bucketName = storageProperties.getS3().getBucketName();
        String endpoint = storageProperties.getS3().getEndpoint();

        if (endpoint != null && !endpoint.isEmpty()) {
            // LocalStack or custom endpoint
            return String.format("%s/%s/%s", endpoint, bucketName, storageKey);
        } else {
            // Standard S3 URL
            String region = storageProperties.getS3().getRegion();
            return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, storageKey);
        }
    }
}
