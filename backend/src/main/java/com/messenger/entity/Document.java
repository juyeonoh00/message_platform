package com.messenger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents", indexes = {
    @Index(name = "idx_category_id", columnList = "category_id"),
    @Index(name = "idx_workspace_id", columnList = "workspace_id"),
    @Index(name = "idx_uploader_id", columnList = "uploader_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 500)
    private String fileUrl;

    @Column(nullable = false)
    private Long fileSize; // bytes

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private Long workspaceId;

    @Column(nullable = false)
    private Long uploaderId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt;
}
