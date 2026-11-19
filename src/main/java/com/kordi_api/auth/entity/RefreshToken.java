package com.kordi_api.auth.entity;

import com.kordi_api.global.common.BaseTimeEntity;
import com.kordi_api.user.entity.User;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * 유저당 하나의 리프레시 토큰(단일 세션). 다른 기기에서 로그인하면 기존 토큰은 교체된다.
 *
 * <p>재발급 시 토큰을 회전(rotation)하되, 동시에 들어온 재발급 요청이 서로를 무효화하지 않도록 직전 토큰을 짧은 유예 시간 동안 함께 보관한다.
 */
@Entity
@Table(
    name = "refresh_tokens",
    indexes = {
      @Index(name = "idx_refresh_token_token", columnList = "token"),
      @Index(name = "idx_refresh_token_previous_token", columnList = "previousToken")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  @Comment("리프레시 토큰")
  private String token;

  @Column
  @Comment("직전 리프레시 토큰 (동시 재발급 유예용)")
  private String previousToken;

  @Column private LocalDateTime rotatedAt;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Column(nullable = false)
  private LocalDateTime expiredAt;

  @Builder
  public RefreshToken(String token, User user, LocalDateTime expiredAt) {
    this.token = token;
    this.user = user;
    this.expiredAt = expiredAt;
  }

  /** 새 로그인: 이전 세션의 토큰을 완전히 대체한다 (유예 없음). */
  public void replace(String token, LocalDateTime expiredAt) {
    this.token = token;
    this.expiredAt = expiredAt;
    this.previousToken = null;
    this.rotatedAt = null;
  }

  /** 재발급: 현재 토큰을 직전 토큰으로 보관하고 새 토큰으로 교체한다. */
  public void rotate(String newToken, LocalDateTime expiredAt, LocalDateTime now) {
    this.previousToken = this.token;
    this.rotatedAt = now;
    this.token = newToken;
    this.expiredAt = expiredAt;
  }

  public boolean isExpired(LocalDateTime now) {
    return now.isAfter(expiredAt);
  }

  public boolean isPreviousTokenUsable(LocalDateTime now, Duration gracePeriod) {
    return rotatedAt != null && !now.isAfter(rotatedAt.plus(gracePeriod));
  }
}
