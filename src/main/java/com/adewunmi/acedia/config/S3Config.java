package com.adewunmi.acedia.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.net.URI;

@Configuration
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
@RequiredArgsConstructor
@Slf4j
public class S3Config {

    private final StorageProperties storageProperties;

    @Bean
    public S3Client s3Client() {
        StorageProperties.S3Properties s3Props = storageProperties.getS3();

        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                s3Props.getAccessKey(),
                s3Props.getSecretKey());

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(s3Props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials));

        // Configure for LocalStack
        if (s3Props.getEndpoint() != null && !s3Props.getEndpoint().isEmpty()) {
            builder.endpointOverride(URI.create(s3Props.getEndpoint()));
            log.info("Using S3 endpoint: {}", s3Props.getEndpoint());
        }

        if (s3Props.isPathStyleAccess()) {
            builder.forcePathStyle(true);
            log.info("Using S3 path-style access");
        }

        S3Client client = builder.build();

        log.info("S3 Client configured for region: {} with bucket: {}",
                s3Props.getRegion(), s3Props.getBucketName());

        return client;
    }

    /**
     * Initialize S3 bucket on-demand when first needed, not during startup.
     * This prevents blocking the application startup with network calls.
     */
    public void ensureBucketExists() {
        try {
            S3Client s3Client = s3Client();
            String bucketName = storageProperties.getS3().getBucketName();

            // Check if bucket exists
            try {
                s3Client.headBucket(HeadBucketRequest.builder()
                        .bucket(bucketName)
                        .build());
                log.info("S3 bucket '{}' already exists", bucketName);
            } catch (NoSuchBucketException e) {
                // Create bucket if it doesn't exist
                s3Client.createBucket(CreateBucketRequest.builder()
                        .bucket(bucketName)
                        .build());
                log.info("Created S3 bucket: {}", bucketName);
            }
        } catch (Exception e) {
            log.warn("Could not initialize S3 bucket (may need manual creation): {}", e.getMessage());
        }
    }
}
