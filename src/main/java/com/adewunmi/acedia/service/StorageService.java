package com.adewunmi.acedia.service;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * Interface for storage operations - supports local file system and S3
 */
public interface StorageService {

    /**
     * Store a file from local path
     * 
     * @param localFilePath the local file path
     * @param storageKey    the key/path in storage (e.g.,
     *                      "novels/novel-id/novel.epub")
     * @return the storage URL or path
     */
    String store(Path localFilePath, String storageKey) throws Exception;

    /**
     * Store a file from input stream
     * 
     * @param inputStream   the input stream
     * @param storageKey    the key/path in storage
     * @param contentLength the content length
     * @return the storage URL or path
     */
    String store(InputStream inputStream, String storageKey, long contentLength) throws Exception;

    /**
     * Retrieve a file as input stream
     * 
     * @param storageKey the key/path in storage
     * @return input stream of the file
     */
    InputStream retrieve(String storageKey) throws Exception;

    /**
     * Delete a file
     * 
     * @param storageKey the key/path in storage
     */
    void delete(String storageKey) throws Exception;

    /**
     * Check if file exists
     * 
     * @param storageKey the key/path in storage
     * @return true if exists
     */
    boolean exists(String storageKey) throws Exception;

    /**
     * Get public URL for a file (if applicable)
     * 
     * @param storageKey the key/path in storage
     * @return the URL to access the file
     */
    String getUrl(String storageKey);
}
