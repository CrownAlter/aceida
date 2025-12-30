package com.adewunmi.acedia.service;

import com.adewunmi.acedia.*;
import com.adewunmi.acedia.model.entity.Chapter;

import java.util.Optional;
import java.util.UUID;

public interface ChapterService {

    Optional<Chapter> getLastSavedChapterByNovelId(UUID novelId);
}
