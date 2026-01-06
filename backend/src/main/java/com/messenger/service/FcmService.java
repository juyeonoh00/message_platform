package com.messenger.service;

import com.google.firebase.messaging.*;
import com.messenger.entity.DeviceType;
import com.messenger.entity.User;
import com.messenger.entity.UserDevice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "fcm.enabled",
    havingValue = "true"
)
public class FcmService {

    private final DeviceService deviceService;

    @Value("${fcm.enabled}")
    private boolean fcmEnabled;

    /**
     * 특정 사용자에게 알림 전송 (우선순위: DESKTOP_APP > WEB)
     */
    public void sendNotificationToUser(User user, String title, String body, Map<String, String> data) {
        if (!fcmEnabled) {
            log.warn("FCM is disabled. Notification not sent to user: {}", user.getEmail());
            return;
        }

        List<UserDevice> activeDevices = deviceService.getActiveDevicesEntities(user);

        if (activeDevices.isEmpty()) {
            log.info("사용자에게 활성 디바이스가 없습니다: userId={}", user.getId());
            return;
        }

        // 우선순위: DESKTOP_APP가 있으면 DESKTOP_APP으로만 전송
        List<UserDevice> desktopDevices = activeDevices.stream()
                .filter(d -> d.getDeviceType() == DeviceType.DESKTOP_APP)
                .collect(Collectors.toList());

        List<UserDevice> targetDevices;
        if (!desktopDevices.isEmpty()) {
            targetDevices = desktopDevices;
            log.info("데스크톱 앱으로 알림 전송: userId={}, deviceCount={}", user.getId(), desktopDevices.size());
        } else {
            targetDevices = activeDevices;
            log.info("모든 활성 디바이스로 알림 전송: userId={}, deviceCount={}", user.getId(), activeDevices.size());
        }

        // 각 디바이스로 알림 전송
        for (UserDevice device : targetDevices) {
            sendNotificationToDevice(device.getDeviceToken(), title, body, data);
        }
    }

    /**
     * 특정 디바이스 토큰으로 알림 전송
     */
    public void sendNotificationToDevice(String deviceToken, String title, String body, Map<String, String> data) {
        if (!fcmEnabled) {
            log.warn("FCM is disabled. Notification not sent.");
            return;
        }

        try {
            // 데이터 맵 생성 (null 체크)
            Map<String, String> notificationData = data != null ? new HashMap<>(data) : new HashMap<>();

            // Notification 객체 생성
            Notification notification = Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build();

            // Message 빌더
            Message.Builder messageBuilder = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(notification)
                    .putAllData(notificationData);

            // Android 설정
            AndroidConfig androidConfig = AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setNotification(AndroidNotification.builder()
                            .setSound("default")
                            .setClickAction("FLUTTER_NOTIFICATION_CLICK")
                            .build())
                    .build();
            messageBuilder.setAndroidConfig(androidConfig);

            // WebPush 설정 (웹 브라우저용)
            WebpushConfig webpushConfig = WebpushConfig.builder()
                    .setNotification(WebpushNotification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .setIcon("/chat_logo.png")
                            .build())
                    .putHeader("Urgency", "high")
                    .build();
            messageBuilder.setWebpushConfig(webpushConfig);

            // APNS 설정 (iOS용)
            ApnsConfig apnsConfig = ApnsConfig.builder()
                    .setAps(Aps.builder()
                            .setSound("default")
                            .build())
                    .build();
            messageBuilder.setApnsConfig(apnsConfig);

            Message message = messageBuilder.build();

            // FCM 전송
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("✅ FCM 알림 전송 성공: token={}, response={}", maskToken(deviceToken), response);

        } catch (FirebaseMessagingException e) {
            log.error("❌ FCM 알림 전송 실패: token={}, error={}", maskToken(deviceToken), e.getMessage());

            // 토큰이 유효하지 않은 경우 처리
            if (e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT ||
                e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                log.warn("유효하지 않은 FCM 토큰, 비활성화 필요: {}", maskToken(deviceToken));
                // TODO: 디바이스 비활성화 처리
            }
        } catch (Exception e) {
            log.error("❌ FCM 알림 전송 중 예외 발생: token={}", maskToken(deviceToken), e);
        }
    }

    /**
     * 멀티캐스트로 여러 디바이스에 알림 전송
     */
    public void sendMulticastNotification(List<String> deviceTokens, String title, String body, Map<String, String> data) {
        if (!fcmEnabled) {
            log.warn("FCM is disabled. Multicast notification not sent.");
            return;
        }

        if (deviceTokens == null || deviceTokens.isEmpty()) {
            log.warn("디바이스 토큰 목록이 비어있습니다.");
            return;
        }

        try {
            Map<String, String> notificationData = data != null ? new HashMap<>(data) : new HashMap<>();

            Notification notification = Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build();

            MulticastMessage message = MulticastMessage.builder()
                    .addAllTokens(deviceTokens)
                    .setNotification(notification)
                    .putAllData(notificationData)
                    .build();

            BatchResponse response = FirebaseMessaging.getInstance().sendMulticast(message);
            log.info("✅ FCM 멀티캐스트 알림 전송: 성공={}, 실패={}", response.getSuccessCount(), response.getFailureCount());

        } catch (FirebaseMessagingException e) {
            log.error("❌ FCM 멀티캐스트 알림 전송 실패", e);
        }
    }

    /**
     * 토큰 마스킹 (로그용)
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 10) {
            return "***";
        }
        return token.substring(0, 10) + "...";
    }
}
