package com.kordi_api.global.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * 인증된 유저의 id({@code Long})를 컨트롤러 파라미터로 주입한다.
 *
 * <pre>{@code
 * public ApiResponse<UserResponse> me(@LoginUser Long userId) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal
public @interface LoginUser {}
