package com.kordi_api.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 닉네임: 2~20자, 한글·영문·숫자·밑줄만 (앞뒤 공백·보이지 않는 문자로 남을 흉내 내지 못하게). */
public record UserUpdateRequest(
    @NotBlank @Size(min = 2, max = 20) @Pattern(regexp = "^[0-9A-Za-z가-힣_]+$") String nickname) {}
