package com.adewunmi.acedia.service.impl;

import com.adewunmi.acedia.model.entity.Chapter;
import com.adewunmi.acedia.repository.ChapterRepository;
import com.adewunmi.acedia.service.ChapterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChapterServiceImpl implements ChapterService {

    private final ChapterRepository chapterRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Chapter> getLastSavedChapterByNovelId(UUID novelId) {
        return chapterRepository.findLastSavedChapterByNovelId(novelId);
    }
}
