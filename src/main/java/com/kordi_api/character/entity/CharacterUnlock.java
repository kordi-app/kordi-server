package com.kordi_api.character.entity;

import com.kordi_api.global.common.BaseTimeEntity;
import com.kordi_api.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 코스 완주가 아닌 다른 방법으로 얻은 아이템 (예: 꿈의 바다에서 만난 달고래). 유저·아이템당 한 줄. */
@Entity
@Table(
    name = "character_unlocks",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_character_unlocks_user_item",
            columnNames = {"user_id", "item"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterUnlock extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 20)
  private String item;

  public static CharacterUnlock of(User user, String item) {
    CharacterUnlock unlock = new CharacterUnlock();
    unlock.user = user;
    unlock.item = item;
    return unlock;
  }
}
