package com.messenger.service;

import com.messenger.dto.auth.*;
import com.messenger.entity.User;
import com.messenger.repository.UserRepository;
import com.messenger.config.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.MediaType;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final WebClient webClient;

    @Value("${kakao.rest-api-key}")
    private String kakaoRestApiKey;

    @Value("${kakao.client-secret:}")
    private String kakaoClientSecret;

    @Value("${kakao.redirect-uri}")
    private String kakaoRedirectUri;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .status("active")
                .build();

        user = userRepository.save(user);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException("Invalid refresh token");
        }

        if (!"refresh".equals(jwtTokenProvider.getTokenType(refreshToken))) {
            throw new RuntimeException("Token is not a refresh token");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .build();
    }

    @Transactional
    public AuthResponse kakaoLogin(KakaoLoginRequest request) {
        // 1. 인가 코드로 액세스 토큰 받기
        KakaoTokenResponse tokenResponse = getKakaoToken(request.getCode());

        // 2. 액세스 토큰으로 사용자 정보 가져오기
        KakaoUserInfo kakaoUserInfo = getUserInfoFromKakao(tokenResponse.getAccessToken());

        String email = kakaoUserInfo.getKakaoAccount().getEmail();
        if (email == null || email.isEmpty()) {
            throw new RuntimeException("Failed to get email from Kakao account");
        }

        // 3. 기존 사용자 찾기 또는 새로 생성
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> createUserFromKakao(kakaoUserInfo));

        // 4. 카카오에서 받은 정보로 프로필 업데이트
        if (kakaoUserInfo.getKakaoAccount().getProfile() != null) {
            user.setAvatarUrl(kakaoUserInfo.getKakaoAccount().getProfile().getProfileImageUrl());
        }
        user = userRepository.save(user);

        // 5. JWT 토큰 생성
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .build();
    }

    private KakaoTokenResponse getKakaoToken(String code) {
        try {
            String body = "grant_type=authorization_code" +
                    "&client_id=" + kakaoRestApiKey +
                    "&redirect_uri=" + kakaoRedirectUri +
                    "&code=" + code;

            // Client Secret이 설정되어 있으면 추가
            if (kakaoClientSecret != null && !kakaoClientSecret.isEmpty()) {
                body += "&client_secret=" + kakaoClientSecret;
            }

            System.out.println("=== Kakao Token Request ===");
            System.out.println("REST API Key: " + kakaoRestApiKey.substring(0, 10) + "...");
            System.out.println("Client Secret: " + (kakaoClientSecret != null && !kakaoClientSecret.isEmpty() ? "설정됨" : "미설정"));
            System.out.println("Redirect URI: " + kakaoRedirectUri);
            System.out.println("Code: " + code);

            return webClient.post()
                    .uri("https://kauth.kakao.com/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> response.bodyToMono(String.class)
                                    .map(errorBody -> {
                                        System.err.println("Kakao API Error: " + errorBody);
                                        return new RuntimeException("Kakao API Error: " + errorBody);
                                    }))
                    .bodyToMono(KakaoTokenResponse.class)
                    .block();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to get Kakao access token: " + e.getMessage());
        }
    }

    private KakaoUserInfo getUserInfoFromKakao(String accessToken) {
        try {
            return webClient.get()
                    .uri("https://kapi.kakao.com/v2/user/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(KakaoUserInfo.class)
                    .block();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get user info from Kakao: " + e.getMessage());
        }
    }

    private User createUserFromKakao(KakaoUserInfo kakaoUserInfo) {
        String nickname = kakaoUserInfo.getKakaoAccount().getProfile() != null
                ? kakaoUserInfo.getKakaoAccount().getProfile().getNickname()
                : kakaoUserInfo.getProperties() != null
                ? kakaoUserInfo.getProperties().getNickname()
                : "Kakao User";

        String profileImageUrl = kakaoUserInfo.getKakaoAccount().getProfile() != null
                ? kakaoUserInfo.getKakaoAccount().getProfile().getProfileImageUrl()
                : kakaoUserInfo.getProperties() != null
                ? kakaoUserInfo.getProperties().getProfileImage()
                : null;

        return User.builder()
                .email(kakaoUserInfo.getKakaoAccount().getEmail())
                .passwordHash(passwordEncoder.encode("KAKAO_" + kakaoUserInfo.getId()))
                .name(nickname)
                .avatarUrl(profileImageUrl)
                .status("active")
                .build();
    }
}
