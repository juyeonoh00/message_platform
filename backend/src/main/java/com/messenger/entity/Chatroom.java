package com.messenger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "chatroom", indexes = {
    @Index(name = "idx_workspace_id", columnList = "workspace_id"),
    @Index(name = "idx_created_by", columnList = "created_by")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chatroom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long workspaceId;

    @Column(length = 200, nullable = false)
    private String name; // 참가자들의 username이 들어감

    @Column(nullable = false)
    private Long createdBy;

    @Column(length = 500)
    private String hiddenBy; // 쉼표로 구분된 사용자 ID 목록 (채팅방을 숨긴 사용자들)

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
