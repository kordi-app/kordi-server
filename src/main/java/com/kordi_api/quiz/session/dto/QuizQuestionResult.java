package com.kordi_api.quiz.session.dto;

/**
 * 서버가 채점한 한 문제.
 *
 * @param correct 정답으로 인정됐는지
 * @param score 이 문제의 점수
 */
public record QuizQuestionResult(boolean correct, int score) {}
