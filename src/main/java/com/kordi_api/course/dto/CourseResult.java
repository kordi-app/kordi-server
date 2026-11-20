package com.kordi_api.course.dto;

/**
 * 서버가 잰 완주 결과.
 *
 * @param timeMs 이번 기록
 * @param bestMs 저장된 최고 기록 (비회원은 null)
 * @param rank 이번 기록이 역대 랭킹에서 차지할 순위 (비회원은 "올라갔다면" 순위)
 * @param saved 랭킹에 저장됐는지 (회원이고 최고 기록을 갱신했을 때)
 */
public record CourseResult(long timeMs, Long bestMs, long rank, boolean saved) {}
