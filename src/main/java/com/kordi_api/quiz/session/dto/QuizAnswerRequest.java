package com.kordi_api.quiz.session.dto;

import com.kordi_api.quiz.QuizRules;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 한 문제에 대한 플레이어의 답. 정답 여부와 점수는 서버가 정한다.
 *
 * @param notes 판정 순간 누르고 있던 건반의 MIDI 번호 (시간 초과면 빈 배열)
 * @param elapsedMs 문제가 나온 뒤 답하기까지 걸린 시간 (시간 초과면 제한 시간)
 */
public record QuizAnswerRequest(
    @NotNull @Size(max = QuizRules.MAX_NOTES_PER_ANSWER)
        List<@NotNull @Min(0) @Max(127) Integer> notes,
    @Min(0) @Max(QuizRules.TIME_PER_QUESTION_MS) int elapsedMs) {}
