package com.kordi_api.quiz.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "quiz_chords")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizChord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 10)
  private String chordkey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ChordType chordType;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(nullable = false)
  private String notes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Difficulty difficulty;

  @Builder
  public QuizChord(
      String chordKey, ChordType chordType, String name, String notes, Difficulty difficulty) {
    this.chordkey = chordKey;
    this.name = name;
    this.notes = notes;
    this.difficulty = difficulty;
  }
}
