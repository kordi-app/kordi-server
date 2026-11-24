package com.kordi_api.character.entity;

import com.kordi_api.course.entity.Course;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 캐릭터 꾸미기 아이템 목록. 클라이언트 entities/character/model/catalog.ts와 짝.
 *
 * <p>대부분은 처음부터 고를 수 있고, 일부 모자는 점프 코스를 완주해야 열린다(보상). 펫과 보상 액세서리는 숨겨진 곳에서 얻으며 character_unlocks에
 * 저장된다.
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

  /** 우주의 왕관(밤 코스 보상)을 쓰고 들어가야 보이는, 별이 잠든 동굴 제단의 황금 목걸이 */
  public static final String GOLD_NECKLACE = "gold-necklace";

  public static final Set<String> ACCESSORIES = Set.of("scarf", "bowtie", "glasses", GOLD_NECKLACE);

  /** 얻어야 쓸 수 있는 액세서리 */
  public static final Set<String> REWARD_ACCESSORIES = Set.of(GOLD_NECKLACE);

  /** 별이 잠든 동굴을 밝히는 모자: 밤 코스의 보상인 우주의 왕관 */
  public static final String COSMIC_CROWN = "star-crown";

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
