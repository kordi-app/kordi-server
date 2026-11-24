package com.kordi_api.character.entity;

import com.kordi_api.global.common.BaseTimeEntity;
import com.kordi_api.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 유저가 꾸민 캐릭터 모습 (유저당 한 줄). 아직 꾸민 적 없으면 행이 없고 기본 모습으로 본다. */
@Entity
@Table(name = "character_appearances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterAppearance extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Column(nullable = false, length = 20)
  private String body;

  /** 없으면 null */
  @Column(length = 20)
  private String hat;

  @Column(nullable = false, length = 20)
  private String face;

  /** 없으면 null */
  @Column(length = 20)
  private String accessory;

  /** 따라다니는 펫, 없으면 null */
  @Column(length = 20)
  private String pet;

  public static CharacterAppearance defaultFor(User user) {
    CharacterAppearance appearance = new CharacterAppearance();
    appearance.user = user;
    appearance.body = CharacterCatalog.DEFAULT_BODY;
    appearance.face = CharacterCatalog.DEFAULT_FACE;
    return appearance;
  }

  /** 값 검증은 서비스에서 끝난 뒤 호출한다. */
  public void change(String body, String hat, String face, String accessory, String pet) {
    this.body = body;
    this.hat = hat;
    this.face = face;
    this.accessory = accessory;
    this.pet = pet;
  }

  public void wear(String hat) {
    this.hat = hat;
  }

  public void adopt(String pet) {
    this.pet = pet;
  }

  public void wearAccessory(String accessory) {
    this.accessory = accessory;
  }
}
