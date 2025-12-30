package com.adewunmi.acedia.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "novels")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Novel {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @OneToMany(mappedBy = "novel", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Chapter> chapters = new ArrayList<>();

    @NotNull
    @Column(name = "title")
    private String title;

    @Column(name = "author")
    private String author;

    @Column(name = "site_name", length = 50)
    private String siteName;

    @Column(name = "url")
    private String url;

    @Column(name = "genre")
    private String genre;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "first_chapter")
    private String firstChapter;

    @Column(name = "current_chapter")
    private String currentChapter;

    @Column(name = "current_chapter_url")
    private String currentChapterUrl;

    @Column(name = "total_chapters")
    private Integer totalChapters;

    @Column(name = "date_created")
    private LocalDateTime dateCreated;

    @Column(name = "date_last_modified")
    private LocalDateTime dateLastModified;

    @Column(name = "last_chapter")
    private boolean lastChapter;

    @Column(name = "last_table_of_contents_url")
    private String lastTableOfContentsUrl;

    @Column(name = "status")
    private String status;

    @Column(name = "save_location")
    private String saveLocation;

    @Column(name = "saved_file_is_split")
    private boolean savedFileIsSplit;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type")
    private NovelFileType fileType;

    @PrePersist
    protected void onCreate() {
        dateCreated = LocalDateTime.now();
        dateLastModified = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        dateLastModified = LocalDateTime.now();
    }
}
