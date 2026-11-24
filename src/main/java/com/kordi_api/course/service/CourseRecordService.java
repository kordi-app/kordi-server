package com.kordi_api.course.service;

import com.kordi_api.character.service.CharacterService;
import com.kordi_api.course.dto.CourseRankingResponse;
import com.kordi_api.course.dto.CourseResult;
import com.kordi_api.course.entity.Course;
import com.kordi_api.course.entity.CourseRecord;
import com.kordi_api.course.repository.CourseRankingQueryRepository;
import com.kordi_api.course.repository.CourseRecordRepository;
import com.kordi_api.global.common.RecordWriter;
import com.kordi_api.user.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 점프 코스 역대 랭킹. 기록은 클라이언트가 보내는 값이 아니라 서버가 월드 소켓의 위치로 잰 값({@link com.kordi_api.world.CourseTimer})만
 * 받는다.
 */
@Service
@RequiredArgsConstructor
public class CourseRecordService {

  static final int RANKING_LIMIT = 10;

  private final CourseRecordRepository courseRecordRepository;
  private final CourseRankingQueryRepository courseRankingQueryRepository;
  private final UserRepository userRepository;
  private final CharacterService characterService;
  private final RecordWriter recordWriter;

  /** 완주 기록 반영. userId가 null(비회원)이면 저장 없이 순위만 알려준다. 동시 첫 완주 경합은 {@link RecordWriter}가 처리한다. */
  public CourseResult finish(Long userId, Course course, long timeMs) {
    return recordWriter.write(() -> record(userId, course, timeMs));
  }

  private CourseResult record(Long userId, Course course, long timeMs) {
    long rank =
        (userId == null
                ? courseRecordRepository.countByCourseAndTimeMsLessThanEqual(course, timeMs)
                : courseRecordRepository.countByCourseAndTimeMsLessThanEqualAndUserIdNot(
                    course, timeMs, userId))
            + 1;
    if (userId == null) {
      return new CourseResult(timeMs, null, rank, false, null);
    }
    var user = userRepository.findById(userId).orElse(null);
    if (user == null) {
      return new CourseResult(timeMs, null, rank, false, null);
    }
    Instant now = Instant.now();
    var existing = courseRecordRepository.findForUpdate(userId, course);
    if (existing.isEmpty()) {
      courseRecordRepository.save(CourseRecord.first(user, course, timeMs, now));
      // 이 코스 첫 완주: 보상이 열리고 바로 씌워진다
      String reward = characterService.grantReward(user, course);
      return new CourseResult(timeMs, timeMs, rank, true, reward);
    }
    CourseRecord record = existing.get();
    boolean improved = record.improve(timeMs, now);
    return new CourseResult(timeMs, record.getTimeMs(), rank, improved, null);
  }

  /**
   * @param friendsOf 이 유저와 수락된 친구만 (null이면 전체)
   */
  @Transactional(readOnly = true)
  public List<CourseRankingResponse> ranking(Course course, Long friendsOf) {
    var records = courseRankingQueryRepository.findRanking(course, friendsOf, RANKING_LIMIT);
    List<CourseRankingResponse> out = new ArrayList<>(records.size());
    for (int i = 0; i < records.size(); i++) {
      CourseRecord r = records.get(i);
      out.add(
          new CourseRankingResponse(
              i + 1, r.getUser().getNickname(), r.getUser().getProfileImageUrl(), r.getTimeMs()));
    }
    return out;
  }
}
