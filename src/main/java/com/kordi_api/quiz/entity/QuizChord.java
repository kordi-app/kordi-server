package com.kordi_api.quiz.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
// one row per chord; text (not ENUM) columns so new chord types never need a schema change
@Table(
    name = "quiz_chords",
    uniqueConstraints = @UniqueConstraint(columnNames = {"chord_key", "chord_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizChord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 10)
  private String chordKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "varchar(40)")
  private ChordType chordType;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(nullable = false)
  private String notes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "varchar(10)")
  private Difficulty difficulty;

  @Builder
  public QuizChord(
      String chordKey, ChordType chordType, String name, String notes, Difficulty difficulty) {
    this.chordKey = chordKey;
    this.chordType = chordType;
    this.name = name;
    this.notes = notes;
    this.difficulty = difficulty;
  }
}
