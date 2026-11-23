package com.kordi_api.auth.dto;

import jakarta.annotation.Nullable;

/** 1회용 로그인 코드 교환 요청. 값이 비어 있으면 교환 실패(2002)로 처리하므로 둘 다 nullable. */
public record LoginCodeExchangeRequest(@Nullable String code, @Nullable String nonce) {}
