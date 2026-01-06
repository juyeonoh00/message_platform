package com.messenger.repository;

import com.messenger.entity.DocumentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentCategoryRepository extends JpaRepository<DocumentCategory, Long> {
    List<DocumentCategory> findByWorkspace_Id(Long workspaceId);
}
