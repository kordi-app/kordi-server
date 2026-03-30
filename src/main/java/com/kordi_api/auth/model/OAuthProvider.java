package com.kordi_api.auth.model;

import java.util.Locale;

public enum OAuthProvider {
    GOOGLE, NAVER, KAKAO;

    public static OAuthProvider from(String registrationId) {
        return valueOf(registrationId.toUpperCase(Locale.ROOT));
    }
}

