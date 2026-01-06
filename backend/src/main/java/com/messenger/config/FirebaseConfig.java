package com.messenger.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "fcm.enabled",
    havingValue = "true"
)
public class FirebaseConfig {

    @Value("${fcm.credentials-path}")
    private Resource credentialsPath;

    @Value("${fcm.enabled}")
    private boolean fcmEnabled;

    @PostConstruct
    public void initialize() {
        if (!fcmEnabled) {
            log.warn("⚠️ FCM is disabled. Set fcm.enabled=true to enable push notifications.");
            return;
        }

        try {
            // Firebase 앱이 이미 초기화되어 있는지 확인
            if (FirebaseApp.getApps().isEmpty()) {
                InputStream serviceAccount = credentialsPath.getInputStream();

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("✅ Firebase initialized successfully");
            } else {
                log.info("✅ Firebase already initialized");
            }
        } catch (IOException e) {
            log.error("❌ Failed to initialize Firebase. FCM notifications will not work.", e);
            log.error("Please check if the firebase-service-account.json file exists at: {}", credentialsPath);
            log.error("You can download this file from Firebase Console -> Project Settings -> Service Accounts");
        }
    }
}
