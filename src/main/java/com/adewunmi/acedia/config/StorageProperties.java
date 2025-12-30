package com.adewunmi.acedia.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "storage")
@Data
public class StorageProperties {

    private String type = "local"; // local or s3

    private S3Properties s3 = new S3Properties();

    @Data
    public static class S3Properties {
        private String endpoint;
        private String region = "us-east-1";
        private String bucketName;
        private String accessKey;
        private String secretKey;
        private boolean pathStyleAccess = false;
    }
}
