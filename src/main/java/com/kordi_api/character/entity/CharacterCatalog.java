package com.kordi_api.character.entity;

import com.kordi_api.course.entity.Course;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 캐릭터 꾸미기 아이템 목록. 클라이언트 entities/character/model/catalog.ts와 짝.
 *
 * <p>대부분은 처음부터 고를 수 있고, 일부 모자는 점프 코스를 완주해야 열린다(보상). 펫은 모두 보상이며 character_unlocks에 저장된다.
 */
public final class CharacterCatalog {

  private CharacterCatalog() {}

  public static final String DEFAULT_BODY = "cream";
  public static final String DEFAULT_FACE = "default";

  public static final Set<String> BODIES =
      Set.of("cream", "sky", "pink", "mint", "lemon", "lilac", "cocoa");
  public static final Set<String> HATS =
      Set.of("crown", "star-crown", "flame-crown", "ribbon", "beret", "headphones", "flower");
  public static final Set<String> FACES = Set.of("default", "smile", "sparkle", "sleepy");
  public static final Set<String> ACCESSORIES = Set.of("scarf", "bowtie", "glasses");

  /** 고양이만 아는 꿈의 바다에서 고래에게 노래를 돌려주면 따라오는 아기 달고래 */
  public static final String MOON_WHALE = "moon-whale";

  public static final Set<String> PETS = Set.of(MOON_WHALE);

  /** 보상 모자: 이 코스를 완주해야 쓸 수 있다 */
  public static final Map<String, Course> REWARD_HATS =
      Map.of("crown", Course.CLOUDS, "star-crown", Course.NIGHT, "flame-crown", Course.RUINS);

  public static Optional<String> rewardHatOf(Course course) {
    return REWARD_HATS.entrySet().stream()
        .filter(e -> e.getValue() == course)
        .map(Map.Entry::getKey)
        .findFirst();
  }
}
