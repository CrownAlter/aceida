package com.adewunmi.acedia.repository;

import com.adewunmi.acedia.model.entity.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PageRepository extends JpaRepository<Page, Long> {

    List<Page> findByChapterId(UUID chapterId);

    void deleteByChapterId(UUID chapterId);
}
