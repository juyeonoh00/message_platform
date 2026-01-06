package com.messenger.repository;

import com.messenger.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByCategory_Id(Long categoryId);
    List<Document> findByWorkspace_Id(Long workspaceId);
}
