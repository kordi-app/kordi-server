package com.kordi_api.auth.entity;

import com.kordi_api.global.common.BaseTimeEntity;
import com.kordi_api.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Entity
@Table(
    name = "refresh_tokens",
    indexes = {@Index(name = "idx_refresh_token_token", columnList = "token")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  @Comment("리프레시 토큰")
  private String token;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false)
  private LocalDateTime expiredAt;

  @Builder
  public RefreshToken(String token, User user, LocalDateTime expiredAt) {
    this.token = token;
    this.user = user;
    this.expiredAt = expiredAt;
  }

  public void updateToken(String token, LocalDateTime expiredAt) {
    this.token = token;
    this.expiredAt = expiredAt;
  }

  public boolean isExpired() {
    return LocalDateTime.now().isAfter(expiredAt);
  }
}
