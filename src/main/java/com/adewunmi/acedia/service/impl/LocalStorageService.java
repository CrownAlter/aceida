package com.adewunmi.acedia.service.impl;

import com.adewunmi.acedia.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
@Slf4j
public class LocalStorageService implements StorageService {

    @Override
    public String store(Path localFilePath, String storageKey) throws Exception {
        // For local storage, the file is already in place
        log.info("Local storage: file already at {}", localFilePath);
        return localFilePath.toString();
    }

    @Override
    public String store(InputStream inputStream, String storageKey, long contentLength) throws Exception {
        Path targetPath = Path.of(storageKey);
        Files.createDirectories(targetPath.getParent());
        Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("Stored file locally: {}", targetPath);
        return targetPath.toString();
    }

    @Override
    public InputStream retrieve(String storageKey) throws Exception {
        Path path = Path.of(storageKey);
        return Files.newInputStream(path);
    }

    @Override
    public void delete(String storageKey) throws Exception {
        Path path = Path.of(storageKey);
        Files.deleteIfExists(path);
        log.info("Deleted local file: {}", path);
    }

    @Override
    public boolean exists(String storageKey) throws Exception {
        return Files.exists(Path.of(storageKey));
    }

    @Override
    public String getUrl(String storageKey) {
        return "file://" + storageKey;
    }
}
