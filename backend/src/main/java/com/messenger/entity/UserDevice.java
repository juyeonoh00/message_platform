package com.messenger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_devices", indexes = {
    @Index(name = "idx_user_device", columnList = "user_id, device_type"),
    @Index(name = "idx_device_token", columnList = "device_token"),
    @Index(name = "idx_user_active", columnList = "user_id, is_active")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_token", nullable = false, length = 500)
    private String deviceToken; // FCM 토큰

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 20)
    private DeviceType deviceType; // WEB, DESKTOP_APP, MOBILE_APP

    @Column(name = "platform", length = 50)
    private String platform; // WINDOWS, MAC, LINUX, CHROME, FIREFOX, etc.

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 마지막 활동 시간 업데이트
    public void updateLastActive() {
        this.lastActiveAt = LocalDateTime.now();
        this.isActive = true;
    }

    // 비활성화
    public void deactivate() {
        this.isActive = false;
    }
}
