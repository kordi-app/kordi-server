package com.kordi_api.adventure.service;

import com.kordi_api.adventure.dto.AdventureRankingResponse;
import com.kordi_api.adventure.dto.AdventureRecordRequest;
import com.kordi_api.adventure.dto.AdventureResult;
import com.kordi_api.adventure.entity.AdventureRecord;
import com.kordi_api.adventure.repository.AdventureRecordRepository;
import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 코드 모험 역대 랭킹 (물리친 몬스터 수, 동률이면 먼저 낸 사람이 위). */
@Service
@RequiredArgsConstructor
public class AdventureRecordService {

  static final int RANKING_LIMIT = 10;

  private final AdventureRecordRepository adventureRecordRepository;
  private final UserRepository userRepository;

  /**
   * 한 판 결과 반영. 첫 기록이거나 더 많이 물리쳤으면 저장한다.
   *
   * <p>같은 유저의 동시 첫 제출은 유니크 제약 위반(DataIntegrityViolationException)으로 끝날 수 있으니 호출 측에서 한 번 다시 부른다 (그때는
   * 행이 있어 잠금 경로로 간다).
   */
  @Transactional
  public AdventureResult submit(Long userId, AdventureRecordRequest request) {
    request.validate();
    int defeated = request.defeated();
    LocalDateTime now = LocalDateTime.now();
    long rank = adventureRecordRepository.countAhead(userId, defeated, now) + 1;

    var existing = adventureRecordRepository.findForUpdate(userId);
    if (existing.isEmpty()) {
      var user =
          userRepository
              .findById(userId)
              .orElseThrow(() -> new CoreException(CoreExceptionCode.USER_NOT_FOUND));
      adventureRecordRepository.saveAndFlush(
          AdventureRecord.first(user, defeated, request.durationMs(), now));
      return new AdventureResult(defeated, defeated, rank, true);
    }
    AdventureRecord record = existing.get();
    boolean improved = record.improve(defeated, request.durationMs(), now);
    return new AdventureResult(defeated, record.getDefeated(), rank, improved);
  }

  @Transactional(readOnly = true)
  public List<AdventureRankingResponse> ranking() {
    var records =
        adventureRecordRepository.findAllByOrderByDefeatedDescAchievedAtAsc(
            Limit.of(RANKING_LIMIT));
    List<AdventureRankingResponse> out = new ArrayList<>(records.size());
    for (int i = 0; i < records.size(); i++) {
      AdventureRecord r = records.get(i);
      out.add(
          new AdventureRankingResponse(
              i + 1, r.getUser().getNickname(), r.getUser().getProfileImageUrl(), r.getDefeated()));
    }
    return out;
  }
}
