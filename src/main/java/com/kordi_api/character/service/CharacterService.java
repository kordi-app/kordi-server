package com.kordi_api.character.service;

import com.kordi_api.character.dto.AppearanceDto;
import com.kordi_api.character.dto.CharacterResponse;
import com.kordi_api.character.entity.CharacterAppearance;
import com.kordi_api.character.entity.CharacterCatalog;
import com.kordi_api.character.repository.CharacterAppearanceRepository;
import com.kordi_api.course.entity.Course;
import com.kordi_api.course.repository.CourseRecordRepository;
import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.user.entity.User;
import com.kordi_api.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 캐릭터 꾸미기. 보상 아이템을 가졌는지는 따로 저장하지 않고 코스 기록에서 본다: 그 코스의 기록이 있으면(= 서버가 잰 완주를 한 적이 있으면) 열린 것. */
@Service
@RequiredArgsConstructor
public class CharacterService {

  private final CharacterAppearanceRepository appearanceRepository;
  private final CourseRecordRepository courseRecordRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher events;

  @Transactional(readOnly = true)
  public CharacterResponse get(Long userId) {
    User user = findUser(userId);
    CharacterAppearance appearance =
        appearanceRepository
            .findByUserId(userId)
            .orElseGet(() -> CharacterAppearance.defaultFor(user));
    return new CharacterResponse(toDto(appearance), unlocked(userId));
  }

  @Transactional
  public CharacterResponse update(Long userId, AppearanceDto request) {
    User user = findUser(userId);
    List<String> unlocked = unlocked(userId);
    validate(request, unlocked);
    CharacterAppearance appearance =
        appearanceRepository
            .findByUserId(userId)
            .orElseGet(() -> appearanceRepository.save(CharacterAppearance.defaultFor(user)));
    appearance.change(request.body(), request.hat(), request.face(), request.accessory());
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

  private List<String> unlocked(Long userId) {
    var finished = Set.copyOf(courseRecordRepository.findCoursesByUserId(userId));
    return CharacterCatalog.REWARD_HATS.entrySet().stream()
        .filter(e -> finished.contains(e.getValue()))
        .map(Map.Entry::getKey)
        .sorted()
        .toList();
  }

  static void validate(AppearanceDto request, List<String> unlocked) {
    boolean valid =
        CharacterCatalog.BODIES.contains(request.body())
            && CharacterCatalog.FACES.contains(request.face())
            && oneOf(request.hat(), CharacterCatalog.HATS)
            && oneOf(request.accessory(), CharacterCatalog.ACCESSORIES);
    if (!valid) {
      throw new CoreException(CoreExceptionCode.INVALID_REQUEST);
    }
    if (request.hat() != null
        && CharacterCatalog.REWARD_HATS.containsKey(request.hat())
        && !unlocked.contains(request.hat())) {
      throw new CoreException(CoreExceptionCode.FORBIDDEN); // 아직 얻지 못한 보상
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
    return new AppearanceDto(a.getBody(), a.getHat(), a.getFace(), a.getAccessory());
  }
}
