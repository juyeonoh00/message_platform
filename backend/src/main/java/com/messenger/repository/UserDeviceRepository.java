package com.messenger.repository;

import com.messenger.entity.DeviceType;
import com.messenger.entity.User;
import com.messenger.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    // 사용자의 모든 디바이스 조회
    List<UserDevice> findByUser(User user);

    // 사용자의 활성 디바이스 조회
    List<UserDevice> findByUserAndIsActiveTrue(User user);

    // 사용자의 특정 타입 활성 디바이스 조회
    List<UserDevice> findByUserAndDeviceTypeAndIsActiveTrue(User user, DeviceType deviceType);

    // 디바이스 토큰으로 조회
    Optional<UserDevice> findByDeviceToken(String deviceToken);

    // 사용자와 디바이스 토큰으로 조회
    Optional<UserDevice> findByUserAndDeviceToken(User user, String deviceToken);

    // 비활성 디바이스 삭제 (일정 시간 이상 활동 없는 경우)
    @Modifying
    @Query("DELETE FROM UserDevice d WHERE d.isActive = false AND d.lastActiveAt < :threshold")
    void deleteInactiveDevices(@Param("threshold") LocalDateTime threshold);

    // 사용자의 모든 디바이스 비활성화
    @Modifying
    @Query("UPDATE UserDevice d SET d.isActive = false WHERE d.user = :user")
    void deactivateAllUserDevices(@Param("user") User user);
}
