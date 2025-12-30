package com.adewunmi.acedia.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "configuration")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Configuration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "name")
    private String name;

    @Column(name = "auto_update")
    private boolean autoUpdate;

    @Column(name = "concurrency_limit")
    private int concurrencyLimit = 2; // Default to 2 concurrent requests

    @Column(name = "save_location")
    private String saveLocation;

    @Column(name = "novel_save_location")
    private String novelSaveLocation;

    @Column(name = "manga_save_location")
    private String mangaSaveLocation;

    @Column(name = "log_location")
    private String logLocation;

    @Column(name = "database_location")
    private String databaseLocation;

    @Column(name = "database_file_name")
    private String databaseFileName;

    @Column(name = "save_as_single_file")
    private boolean saveAsSingleFile;

    @Column(name = "font_type")
    private String fontType;

    @Column(name = "font_size")
    private int fontSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_manga_file_extension")
    private FileExtension defaultMangaFileExtension;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_log_level")
    private LogLevel defaultLogLevel;

    public String determineSaveLocation(boolean isManga) {
        if (!isManga && novelSaveLocation != null && !novelSaveLocation.isEmpty()) {
            return novelSaveLocation;
        }
        if (isManga && mangaSaveLocation != null && !mangaSaveLocation.isEmpty()) {
            return mangaSaveLocation;
        }
        return saveLocation != null ? saveLocation : "";
    }
}
