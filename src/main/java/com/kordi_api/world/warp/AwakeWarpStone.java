package com.kordi_api.world.warp;

import com.kordi_api.global.common.BaseTimeEntity;
import com.kordi_api.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 유저가 깨운 섬의 워프석. 유저·워프석당 한 줄 (광장 워프석은 모두에게 처음부터 깨어 있어 저장하지 않는다). */
@Entity
@Table(
    name = "world_warp_stones",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_world_warp_stones_user_stone",
            columnNames = {"user_id", "stone"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AwakeWarpStone extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "user_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_world_warp_stones_user"))
  private User user;

  @Column(nullable = false, length = 20)
  private String stone;

  public static AwakeWarpStone of(User user, String stone) {
    AwakeWarpStone awake = new AwakeWarpStone();
    awake.user = user;
    awake.stone = stone;
    return awake;
  }
}
