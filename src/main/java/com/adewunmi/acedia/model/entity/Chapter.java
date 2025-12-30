package com.adewunmi.acedia.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "chapters")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Chapter {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "novel_id", nullable = false)
    private Novel novel;

    // Transient getter for backward compatibility
    @Transient
    public UUID getNovelId() {
        return novel != null ? novel.getId() : null;
    }

    @Transient
    public void setNovelId(UUID novelId) {
        // This is now handled through the novel relationship
        // Keeping method for backward compatibility but it's a no-op
    }

    @Size(max = 255)
    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "url")
    private String url;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "number")
    private float number;

    @Column(name = "date_created")
    private LocalDateTime dateCreated;

    @Column(name = "date_last_modified")
    private LocalDateTime dateLastModified;

    @OneToMany(mappedBy = "chapter", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Page> pages = new ArrayList<>();

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
