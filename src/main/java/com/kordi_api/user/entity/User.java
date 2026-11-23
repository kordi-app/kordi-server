package com.kordi_api.user.entity;

import com.kordi_api.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "users",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"oauth_provider", "oauth_id"})})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String email;

  @Column(nullable = false, length = 100, unique = true)
  private String nickname;

  @Column(length = 500)
  private String profileImageUrl;

  // text, not a MySQL ENUM: a new role must not need a schema change (see data.sql)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "varchar(20)")
  private UserRole role;

  // text, not a MySQL ENUM: adding a sign-in provider must not break sign-ups (see data.sql)
  @Enumerated(EnumType.STRING)
  @Column(name = "oauth_provider", nullable = false, columnDefinition = "varchar(20)")
  private OAuthProvider oauthProvider;

  @Column(name = "oauth_id", nullable = false)
  private String oauthId;

  @Builder
  public User(
      String email,
      String nickname,
      String profileImageUrl,
      OAuthProvider oauthProvider,
      String oauthId) {
    this.email = email;
    this.nickname = nickname;
    this.profileImageUrl = profileImageUrl;
    this.role = UserRole.USER;
    this.oauthId = oauthId;
    this.oauthProvider = oauthProvider;
  }

  public void updateNickname(String nickname) {
    this.nickname = nickname;
  }
}
