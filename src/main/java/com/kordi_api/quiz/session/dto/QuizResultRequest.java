package com.kordi_api.quiz.session.dto;

import com.kordi_api.quiz.QuizRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param answers 문제마다 하나씩, 문제 순서대로
 */
public record QuizResultRequest(
    @NotNull @Size(max = QuizRules.QUESTIONS_PER_QUIZ)
        List<@NotNull @Valid QuizAnswerRequest> answers) {}
