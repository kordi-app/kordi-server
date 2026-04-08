package com.kordi_api.quiz.dto;

import com.kordi_api.quiz.entity.ChordType;
import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.quiz.entity.QuizChord;

public record QuizChordResponse(
    Long id,
    String chordKey,
    ChordType chordType,
    String name,
    String notes,
    Difficulty difficulty) {
  public static QuizChordResponse from(QuizChord quizChord) {
    return new QuizChordResponse(
        quizChord.getId(),
        quizChord.getChordkey(),
        quizChord.getChordType(),
        quizChord.getName(),
        quizChord.getNotes(),
        quizChord.getDifficulty());
  }
}
