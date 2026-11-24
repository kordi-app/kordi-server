package com.kordi_api.character.service;

import com.kordi_api.character.dto.AppearanceDto;
import com.kordi_api.character.dto.CharacterResponse;
import com.kordi_api.character.entity.CharacterAppearance;
import com.kordi_api.character.entity.CharacterCatalog;
import com.kordi_api.character.entity.CharacterUnlock;
import com.kordi_api.character.repository.CharacterAppearanceRepository;
import com.kordi_api.character.repository.CharacterUnlockRepository;
import com.kordi_api.course.entity.Course;
import com.kordi_api.course.repository.CourseRecordRepository;
import com.kordi_api.global.common.RecordWriter;
import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.user.entity.User;
import com.kordi_api.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캐릭터 꾸미기. 보상 모자를 가졌는지는 따로 저장하지 않고 코스 기록에서 본다: 그 코스의 기록이 있으면(= 서버가 잰 완주를 한 적이 있으면) 열린 것. 코스와 상관없이
 * 얻는 아이템(펫)만 character_unlocks에 남긴다.
 */
@Service
@RequiredArgsConstructor
public class CharacterService {

  private final CharacterAppearanceRepository appearanceRepository;
  private final CharacterUnlockRepository unlockRepository;
  private final CourseRecordRepository courseRecordRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher events;
  private final RecordWriter recordWriter;

  @Transactional(readOnly = true)
  public CharacterResponse get(Long userId) {
    User user = findUser(userId);
    CharacterAppearance appearance =
        appearanceRepository
            .findByUserId(userId)
            .orElseGet(() -> CharacterAppearance.defaultFor(user));
    return new CharacterResponse(toDto(appearance), unlocked(userId));
  }

  /**
   * Two first saves racing (two tabs) are settled by {@link RecordWriter}: the loser retries as an
   * update.
   */
  public CharacterResponse update(Long userId, AppearanceDto request) {
    return recordWriter.write(() -> applyUpdate(userId, request));
  }

  private CharacterResponse applyUpdate(Long userId, AppearanceDto request) {
    User user = findUser(userId);
    List<String> unlocked = unlocked(userId);
    validate(request, unlocked);
    CharacterAppearance appearance =
        appearanceRepository
            .findByUserId(userId)
            .orElseGet(() -> appearanceRepository.save(CharacterAppearance.defaultFor(user)));
    appearance.change(
        request.body(), request.hat(), request.face(), request.accessory(), request.pet());
    events.publishEvent(new AppearanceChanged(userId, toDto(appearance)));
    return new CharacterResponse(toDto(appearance), unlocked);
  }

  /**
   * 코스를 처음 완주했을 때: 그 보상 모자를 바로 씌운다. 코스 기록이 저장된 같은 트랜잭션 안에서 호출된다.
   *
   * @return 새로 열린 보상 아이템
   */
  @Transactional
  public String grantReward(User user, Course course) {
    String hat = CharacterCatalog.rewardHatOf(course).orElse(null);
    if (hat == null) {
      return null;
    }
    CharacterAppearance appearance =
        appearanceRepository
            .findByUserId(user.getId())
            .orElseGet(() -> appearanceRepository.save(CharacterAppearance.defaultFor(user)));
    appearance.wear(hat);
    events.publishEvent(new AppearanceChanged(user.getId(), toDto(appearance)));
    return hat;
  }

  /**
   * 펫을 처음 얻었을 때: 기록을 남기고 바로 데리고 다니게 한다. 이미 가졌으면 아무것도 하지 않는다. 두 소켓이 동시에 받아도 유니크 제약에 걸린 쪽은 {@link
   * RecordWriter}가 다시 실행해 "이미 가짐"으로 끝난다.
   *
   * @return 이번에 새로 얻었으면 true
   */
  public boolean grantPet(Long userId, String pet) {
    if (!CharacterCatalog.PETS.contains(pet)) {
      throw new IllegalArgumentException("unknown pet: " + pet);
    }
    return recordWriter.write(
        () -> {
          if (unlockRepository.existsByUserIdAndItem(userId, pet)) {
            return false;
          }
          User user = findUser(userId);
          unlockRepository.saveAndFlush(CharacterUnlock.of(user, pet));
          CharacterAppearance appearance =
              appearanceRepository
                  .findByUserId(userId)
                  .orElseGet(() -> appearanceRepository.save(CharacterAppearance.defaultFor(user)));
          appearance.adopt(pet);
          events.publishEvent(new AppearanceChanged(userId, toDto(appearance)));
          return true;
        });
  }

  private List<String> unlocked(Long userId) {
    var finished = Set.copyOf(courseRecordRepository.findCoursesByUserId(userId));
    Stream<String> hats =
        CharacterCatalog.REWARD_HATS.entrySet().stream()
            .filter(e -> finished.contains(e.getValue()))
            .map(Map.Entry::getKey);
    return Stream.concat(hats, unlockRepository.findItemsByUserId(userId).stream())
        .distinct()
        .sorted()
        .toList();
  }

  static void validate(AppearanceDto request, List<String> unlocked) {
    boolean valid =
        CharacterCatalog.BODIES.contains(request.body())
            && CharacterCatalog.FACES.contains(request.face())
            && oneOf(request.hat(), CharacterCatalog.HATS)
            && oneOf(request.accessory(), CharacterCatalog.ACCESSORIES)
            && oneOf(request.pet(), CharacterCatalog.PETS);
    if (!valid) {
      throw new CoreException(CoreExceptionCode.INVALID_REQUEST);
    }
    if (request.hat() != null
        && CharacterCatalog.REWARD_HATS.containsKey(request.hat())
        && !unlocked.contains(request.hat())) {
      throw new CoreException(CoreExceptionCode.FORBIDDEN); // 아직 얻지 못한 보상
    }
    if (request.pet() != null && !unlocked.contains(request.pet())) {
      throw new CoreException(CoreExceptionCode.FORBIDDEN); // 펫은 모두 보상
    }
  }

  private static boolean oneOf(String value, Set<String> allowed) {
    return value == null || allowed.contains(value);
  }

  private User findUser(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CoreException(CoreExceptionCode.USER_NOT_FOUND));
  }

  private static AppearanceDto toDto(CharacterAppearance a) {
    return new AppearanceDto(a.getBody(), a.getHat(), a.getFace(), a.getAccessory(), a.getPet());
  }
}
