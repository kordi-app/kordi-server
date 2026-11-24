package com.kordi_api.adventure.service;

import com.kordi_api.adventure.dto.AdventureRankingResponse;
import com.kordi_api.adventure.dto.AdventureRecordRequest;
import com.kordi_api.adventure.dto.AdventureResult;
import com.kordi_api.adventure.entity.AdventureRecord;
import com.kordi_api.adventure.repository.AdventureRecordRepository;
import com.kordi_api.global.common.RecordWriter;
import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.user.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 코드 모험 역대 랭킹 (물리친 몬스터 수, 동률이면 먼저 낸 사람이 위). */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdventureRecordService {

  static final int RANKING_LIMIT = 10;

  private final AdventureRecordRepository adventureRecordRepository;
  private final UserRepository userRepository;
  private final AdventureRunService runService;
  private final RecordWriter recordWriter;

  /** 판 시작: 서버가 시작 시각을 기억한다 (결과의 판 길이는 이것으로 잰다). */
  public String startRun(Long userId) {
    return runService.start(userId);
  }

  /**
   * 한 판 결과 반영. 판 길이는 서버가 잰 값. 첫 기록이거나 더 많이 물리쳤으면 저장한다 (0마리는 순위만 알려주고 저장하지 않음). 동시 첫 기록 경합은 {@link
   * RecordWriter}가 처리한다.
   */
  public AdventureResult submit(Long userId, AdventureRecordRequest request) {
    long durationMs =
        runService
            .finish(userId, request.runId())
            .orElseThrow(() -> new CoreException(CoreExceptionCode.INVALID_REQUEST));
    if (request.validate(durationMs) == AdventureRecordRequest.Verdict.SUSPICIOUS) {
      // client-claimed count (see AdventureRecordRequest): kept, but leave a trail to review
      log.warn(
          "[AdventureSuspiciousRecord] userId={} defeated={} durationMs={}",
          userId,
          request.defeated(),
          durationMs);
    }
    return recordWriter.write(() -> save(userId, request.defeated(), durationMs));
  }

  private AdventureResult save(Long userId, int defeated, long durationMs) {
    Instant now = Instant.now();
    long rank = adventureRecordRepository.countAhead(userId, defeated, now) + 1;
    if (defeated == 0) {
      // nothing beaten: tell them where it stands, but don't put an empty run on the board
      int best =
          adventureRecordRepository
              .findByUserId(userId)
              .map(AdventureRecord::getDefeated)
              .orElse(0);
      return new AdventureResult(0, best, rank, false);
    }

    var existing = adventureRecordRepository.findForUpdate(userId);
    if (existing.isEmpty()) {
      var user =
          userRepository
              .findById(userId)
              .orElseThrow(() -> new CoreException(CoreExceptionCode.USER_NOT_FOUND));
      adventureRecordRepository.saveAndFlush(
          AdventureRecord.first(user, defeated, durationMs, now));
      return new AdventureResult(defeated, defeated, rank, true);
    }
    AdventureRecord record = existing.get();
    boolean improved = record.improve(defeated, durationMs, now);
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
