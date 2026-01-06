package com.messenger.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class KakaoUserInfo {
    private Long id;

    @JsonProperty("kakao_account")
    private KakaoAccount kakaoAccount;

    @JsonProperty("properties")
    private Properties properties;

    @Data
    public static class KakaoAccount {
        private String email;

        @JsonProperty("profile")
        private Profile profile;
    }

    @Data
    public static class Profile {
        private String nickname;

        @JsonProperty("profile_image_url")
        private String profileImageUrl;
    }

    @Data
    public static class Properties {
        private String nickname;

        @JsonProperty("profile_image")
        private String profileImage;
    }
}
