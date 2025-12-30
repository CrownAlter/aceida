package com.adewunmi.acedia.cli;

import com.adewunmi.acedia.model.entity.Novel;
import com.adewunmi.acedia.service.NovelProcessor;
import com.adewunmi.acedia.service.NovelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.net.URI;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

@Command(name = "benny-scraper", mixinStandardHelpOptions = true, version = "1.1.21", description = "Web scraper for light novels and manga")
@RequiredArgsConstructor
@Slf4j
public class BennyScraperCommand implements Runnable {

    private final NovelService novelService;
    private final NovelProcessor novelProcessor;

    @Parameters(index = "0", description = "Novel URL to scrape", arity = "0..1")
    private String novelUrl;

    @Option(names = { "-l", "--list" }, description = "List all novels in database")
    private boolean list;

    @Option(names = { "-S", "--search" }, description = "Search novels by title")
    private String search;

    @Option(names = { "-U", "--update-all" }, description = "Update all non-completed novels")
    private boolean updateAll;

    @Option(names = { "-i", "--novel-info-by-id" }, description = "Get novel info by ID")
    private String novelInfoId;

    @Option(names = { "-d", "--delete-novel-by-id" }, description = "Delete novel by ID")
    private String deleteNovelId;

    @Option(names = { "--clear-database" }, description = "Clear all novels from database")
    private boolean clearDatabase;

    @Override
    public void run() {
        try {
            if (list) {
                listNovels();
            } else if (search != null) {
                searchNovels(search);
            } else if (updateAll) {
                updateAllNovels();
            } else if (novelInfoId != null) {
                showNovelInfo(UUID.fromString(novelInfoId));
            } else if (deleteNovelId != null) {
                deleteNovel(UUID.fromString(deleteNovelId));
            } else if (clearDatabase) {
                clearAllNovels();
            } else if (novelUrl != null) {
                processNovelUrl(novelUrl);
            } else {
                log.info("No command specified. Use --help for usage information.");
            }
        } catch (Exception e) {
            log.error("Error executing command", e);
        }
    }

    private void runInteractive() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("\n╔════════════════════════════════════════╗");
        System.out.println("║   Welcome to Benny Scraper (Java)     ║");
        System.out.println("╚════════════════════════════════════════╝\n");
        System.out.println("Supported websites:");
        System.out.println("  - lightnovelworld.com");
        System.out.println("  - novelfull.com");
        System.out.println("  - noveldrama.com");
        System.out.println("  - mangakatana.com");
        System.out.println("  - mangakakalot.to");
        System.out.println("  - mangareader.to");
        System.out.println();

        while (true) {
            System.out.print("Enter novel URL (or 'exit' to quit): ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                break;
            }

            if (input.isEmpty()) {
                System.out.println("Please enter a valid URL");
                continue;
            }

            try {
                URI uri = URI.create(input);
                processNovelUrl(input);
            } catch (Exception e) {
                log.error("Error processing novel", e);
                System.out.println("Error: " + e.getMessage());
            }
        }

        scanner.close();
    }

    private void processNovelUrl(String url) throws Exception {
        log.info("Processing novel from URL: {}", url);
        URI uri = URI.create(url);
        novelProcessor.processNovel(uri);
        System.out.println("✓ Novel processed successfully!");
    }

    private void listNovels() {
        List<Novel> novels = novelService.getAll();

        if (novels.isEmpty()) {
            System.out.println("No novels found in database.");
            return;
        }

        System.out.println("\n╔════════════════════════════════════════════════════════════════════════════╗");
        System.out.printf("║ %-36s ║ %-35s ║%n", "ID", "Title");
        System.out.println("╠════════════════════════════════════════════════════════════════════════════╣");

        for (Novel novel : novels) {
            String title = novel.getTitle();
            if (title.length() > 35) {
                title = title.substring(0, 32) + "...";
            }
            System.out.printf("║ %-36s ║ %-35s ║%n", novel.getId(), title);
        }

        System.out.println("╚════════════════════════════════════════════════════════════════════════════╝");
        System.out.printf("\nTotal novels: %d\n", novels.size());
    }

    private void searchNovels(String keyword) {
        List<Novel> novels = novelService.searchByTitle(keyword);

        if (novels.isEmpty()) {
            System.out.printf("No novels found matching '%s'\n", keyword);
            return;
        }

        System.out.printf("\nFound %d novel(s) matching '%s':\n\n", novels.size(), keyword);
        for (Novel novel : novels) {
            System.out.printf("ID: %s\nTitle: %s\nAuthor: %s\n\n",
                    novel.getId(), novel.getTitle(), novel.getAuthor());
        }
    }

    private void updateAllNovels() {
        System.out.println("Updating all non-completed novels...");
        List<Novel> novels = novelService.getAll().stream()
                .filter(n -> !n.isLastChapter())
                .toList();

        int updated = 0;
        int failed = 0;

        for (Novel novel : novels) {
            try {
                URI uri = URI.create(novel.getUrl());
                novelProcessor.processNovel(uri);
                updated++;
                System.out.printf("✓ Updated: %s\n", novel.getTitle());
            } catch (Exception e) {
                failed++;
                log.error("Failed to update: {}", novel.getTitle(), e);
                System.out.printf("✗ Failed: %s\n", novel.getTitle());
            }
        }

        System.out.printf("\n✓ Updated: %d  ✗ Failed: %d\n", updated, failed);
    }

    private void showNovelInfo(UUID id) {
        novelService.getById(id).ifPresentOrElse(novel -> {
            System.out.println("\n╔══════════════════════════════════════════════════════════╗");
            System.out.println("║                    NOVEL INFORMATION                     ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");
            System.out.printf("ID:              %s\n", novel.getId());
            System.out.printf("Title:           %s\n", novel.getTitle());
            System.out.printf("Author:          %s\n", novel.getAuthor());
            System.out.printf("Status:          %s\n", novel.getStatus());
            System.out.printf("Total Chapters:  %d\n", novel.getTotalChapters());
            System.out.printf("Current Chapter: %s\n", novel.getCurrentChapter());
            System.out.printf("Site:            %s\n", novel.getSiteName());
            System.out.printf("Save Location:   %s\n", novel.getSaveLocation());
            System.out.printf("File Type:       %s\n", novel.getFileType());
            System.out.println();
        }, () -> System.out.printf("Novel with ID %s not found.\n", id));
    }

    private void deleteNovel(UUID id) {
        System.out.printf("Are you sure you want to delete novel %s? (y/n): ", id);
        Scanner scanner = new Scanner(System.in);
        String confirm = scanner.nextLine().trim();

        if (confirm.equalsIgnoreCase("y")) {
            novelService.deleteById(id);
            System.out.println("✓ Novel deleted successfully");
        } else {
            System.out.println("Deletion cancelled");
        }
    }

    private void clearAllNovels() {
        System.out.print("Are you sure you want to clear ALL novels from the database? (y/n): ");
        Scanner scanner = new Scanner(System.in);
        String confirm = scanner.nextLine().trim();

        if (confirm.equalsIgnoreCase("y")) {
            novelService.deleteAll();
            System.out.println("✓ Database cleared successfully");
        } else {
            System.out.println("Operation cancelled");
        }
    }
}
