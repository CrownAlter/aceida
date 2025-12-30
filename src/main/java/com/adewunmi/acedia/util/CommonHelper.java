package com.adewunmi.acedia.util;

import com.adewunmi.acedia.model.entity.Chapter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class CommonHelper {

    private static final Pattern INVALID_FILENAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|]");

    /**
     * Sanitizes a filename by removing invalid characters
     */
    public static String sanitizeFileName(String fileName) {
        return sanitizeFileName(fileName, false);
    }

    /**
     * Sanitizes a filename and optionally capitalizes it
     */
    public static String sanitizeFileName(String fileName, boolean capitalize) {
        if (fileName == null || fileName.isEmpty()) {
            return fileName;
        }

        // Remove invalid characters
        String sanitized = INVALID_FILENAME_CHARS.matcher(fileName).replaceAll("");

        // Normalize unicode characters
        sanitized = Normalizer.normalize(sanitized, Normalizer.Form.NFKD);
        sanitized = sanitized.replaceAll("[^\\p{ASCII}]", "");

        if (capitalize) {
            sanitized = capitalizeWords(sanitized);
        }

        return sanitized.trim();
    }

    /**
     * Capitalizes the first letter of each word
     */
    private static String capitalizeWords(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String[] words = input.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
                result.append(" ");
            }
        }

        return result.toString().trim();
    }

    /**
     * Deletes a temporary folder
     */
    public static void deleteTempFolder(String tempPath) {
        if (tempPath == null || tempPath.isEmpty()) {
            return;
        }

        try {
            Path path = Paths.get(tempPath);
            if (Files.exists(path)) {
                if (Files.isDirectory(path)) {
                    Files.walk(path)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                } else {
                    Files.delete(path);
                }
                log.info("Deleted temp folder: {}", tempPath);
            }
        } catch (IOException e) {
            log.error("Failed to delete temp folder: {}. Reason: {}", tempPath, e.getMessage());
        }
    }

    /**
     * Gets the output directory for a title
     */
    public static String getOutputDirectoryForTitle(String title, String outputDirectory) {
        if (outputDirectory != null && !outputDirectory.isEmpty()) {
            return Paths.get(outputDirectory, sanitizeFileName(title, true)).toString();
        }

        String documentsFolder = System.getProperty("user.home") + File.separator + "Documents";
        String safeTitle = sanitizeFileName(title, true);
        return Paths.get(documentsFolder, "BennyScrapedNovels", safeTitle).toString();
    }

    /**
     * Creates a temporary directory
     */
    public static String createTempDirectory() {
        try {
            Path tempDir = Files.createTempDirectory(UUID.randomUUID().toString());
            return tempDir.toString();
        } catch (IOException e) {
            log.error("Failed to create temp directory", e);
            return null;
        }
    }

    /**
     * Sorts chapters by date created
     */
    public static List<Chapter> sortNovelChaptersByDateCreated(List<Chapter> chapters) {
        return chapters.stream()
                .sorted(Comparator.comparing(Chapter::getDateCreated))
                .collect(Collectors.toList());
    }
}
