package com.kordi_api.quiz.judge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 코드 정답 판정. 클라이언트 {@code shared/lib/music/chord-validator.ts}의 {@code validateChord}를 그대로 옮긴 것이다 —
 * 퀴즈는 rivals 기본값(모든 코드 타입, 엄격 모드)으로 판정하므로 그 경우만 옮겼다.
 *
 * <p>두 구현이 같은 답을 내는지는 {@code ChordValidatorContractTest}가 클라이언트가 만든 픽스처({@code
 * chord-validator-fixtures.json}, client {@code scripts/gen-chord-fixtures.mjs})로 확인한다. 한쪽 규칙을 바꾸면
 * 픽스처를 다시 만들고 이 클래스도 같이 고친다.
 */
public final class ChordValidator {

  /**
   * 코드 접미사 → 근음 위 음정. 순서는 JS의 {@code Object.keys(CHORD_TEMPLATES)} 순서와 같다 (정수처럼 생긴 키
   * "5","6","7","9", "11","13"이 오름차순으로 먼저, 나머지는 선언 순). 이름 해석이 "긴 접미사 먼저"로 고를 때 같은 길이끼리는 이 순서를 따르므로
   * 맞춰 둔다.
   */
  static final Map<String, int[]> TEMPLATES = new LinkedHashMap<>();

  static {
    // integer-like keys first (JS object key order)
    TEMPLATES.put("5", new int[] {0, 7});
    TEMPLATES.put("6", new int[] {0, 4, 7, 9});
    TEMPLATES.put("7", new int[] {0, 4, 7, 10});
    TEMPLATES.put("9", new int[] {0, 4, 7, 10, 2});
    TEMPLATES.put("11", new int[] {0, 4, 7, 10, 2, 5});
    TEMPLATES.put("13", new int[] {0, 4, 7, 10, 2, 5, 9});
    // triads
    TEMPLATES.put("", new int[] {0, 4, 7});
    TEMPLATES.put("m", new int[] {0, 3, 7});
    TEMPLATES.put("dim", new int[] {0, 3, 6});
    TEMPLATES.put("aug", new int[] {0, 4, 8});
    TEMPLATES.put("sus2", new int[] {0, 2, 7});
    TEMPLATES.put("sus4", new int[] {0, 5, 7});
    // sevenths / sixths / adds
    TEMPLATES.put("maj7", new int[] {0, 4, 7, 11});
    TEMPLATES.put("m7", new int[] {0, 3, 7, 10});
    TEMPLATES.put("mMaj7", new int[] {0, 3, 7, 11});
    TEMPLATES.put("dim7", new int[] {0, 3, 6, 9});
    TEMPLATES.put("dim(maj7)", new int[] {0, 3, 6, 11});
    TEMPLATES.put("m7b5", new int[] {0, 3, 6, 10});
    TEMPLATES.put("+Maj7", new int[] {0, 4, 8, 11});
    TEMPLATES.put("7#5", new int[] {0, 4, 8, 10});
    TEMPLATES.put("7b5", new int[] {0, 4, 6, 10});
    TEMPLATES.put("7sus4", new int[] {0, 5, 7, 10});
    TEMPLATES.put("m6", new int[] {0, 3, 7, 9});
    TEMPLATES.put("add9", new int[] {0, 2, 4, 7});
    TEMPLATES.put("madd9", new int[] {0, 2, 3, 7});
    // ninths / 6/9 / altered 7
    TEMPLATES.put("maj9", new int[] {0, 4, 7, 11, 2});
    TEMPLATES.put("m9", new int[] {0, 3, 7, 10, 2});
    TEMPLATES.put("m(maj9)", new int[] {0, 3, 7, 11, 2});
    TEMPLATES.put("6/9", new int[] {0, 4, 7, 9, 2});
    TEMPLATES.put("m6/9", new int[] {0, 3, 7, 9, 2});
    TEMPLATES.put("9sus4", new int[] {0, 5, 7, 10, 2});
    TEMPLATES.put("9#5", new int[] {0, 4, 8, 10, 2});
    TEMPLATES.put("9b5", new int[] {0, 4, 6, 10, 2});
    TEMPLATES.put("7#9", new int[] {0, 4, 7, 10, 3});
    TEMPLATES.put("7b9", new int[] {0, 4, 7, 10, 1});
    TEMPLATES.put("7#11", new int[] {0, 4, 7, 10, 6});
    TEMPLATES.put("maj7#11", new int[] {0, 4, 7, 11, 6});
    // elevenths / 9#11 / alt
    TEMPLATES.put("maj9#11", new int[] {0, 4, 7, 11, 2, 6});
    TEMPLATES.put("9#11", new int[] {0, 4, 7, 10, 2, 6});
    TEMPLATES.put("m11", new int[] {0, 3, 7, 10, 2, 5});
    TEMPLATES.put("13sus4", new int[] {0, 5, 7, 10, 2, 9});
    TEMPLATES.put("13b9", new int[] {0, 4, 7, 10, 1, 9});
    TEMPLATES.put("13#9", new int[] {0, 4, 7, 10, 3, 9});
    TEMPLATES.put("7alt", new int[] {0, 4, 8, 10, 1, 3});
    // thirteenths
    TEMPLATES.put("maj13#11", new int[] {0, 4, 7, 11, 2, 6, 9});
    TEMPLATES.put("maj13", new int[] {0, 4, 7, 11, 2, 5, 9});
    TEMPLATES.put("13#11", new int[] {0, 4, 7, 10, 2, 6, 9});
    TEMPLATES.put("m13", new int[] {0, 3, 7, 10, 2, 5, 9});
  }

  /** 긴 접미사 먼저 ("m7b5"가 "7"/"m"/""보다 먼저 맞도록). 안정 정렬이라 같은 길이는 위 순서를 지킨다. */
  private static final List<String> TYPES_LONGEST_FIRST;

  static {
    List<String> types = new ArrayList<>(TEMPLATES.keySet());
    types.sort(Comparator.comparingInt(String::length).reversed());
    TYPES_LONGEST_FIRST = List.copyOf(types);
  }

  /** 근음 표기 → 피치 클래스 (샵·플랫 둘 다) */
  private static final Map<String, Integer> NOTE_INDEX = new LinkedHashMap<>();

  static {
    String[] sharps = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    String[] flats = {"C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"};
    for (int i = 0; i < 12; i++) {
      NOTE_INDEX.put(sharps[i], i);
      NOTE_INDEX.put(flats[i], i);
    }
  }

  /** 서버 데이터가 쓰는 다른 표기 ("C69" = C6/9) */
  private static final Pattern SIX_NINE_ALIAS = Pattern.compile("69$");

  /** 7th 코드(혹은 6th)에 더해도 되는 텐션 (근음 위 음정) */
  private static final Map<String, int[]> TENSIONS =
      Map.of(
          "maj7", new int[] {2},
          "7", new int[] {2, 9},
          "m7", new int[] {2, 5},
          "mMaj7", new int[] {2},
          "m7b5", new int[] {5},
          "7sus4", new int[] {2},
          "6", new int[] {2},
          "m6", new int[] {2});

  /** 11th·13th 코드가 생략해도 되는 음 */
  private static final Map<String, int[]> OPTIONAL =
      Map.of(
          "11", new int[] {4, 7, 2},
          "m11", new int[] {7, 2},
          "13", new int[] {7, 2, 5},
          "maj13", new int[] {7, 2, 5},
          "m13", new int[] {7, 2, 5});

  private static final int PERFECT_FIFTH = 7;

  private ChordValidator() {}

  private record Parsed(int root, String type) {}

  private record Rules(String type, int[] template, Set<Integer> required, Set<Integer> allowed) {

    boolean matches(Set<Integer> played) {
      return played.containsAll(required) && allowed.containsAll(played);
    }
  }

  /**
   * 친 음들이 목표 코드인지 (옥타브·자리바꿈 무관). 필수음이 다 있고 코드음·허용 텐션 밖의 음이 없어야 하며, 그 음들이 목표를 확장한 다른 코드(Cmaj7에 대한
   * Cmaj9 등)도 만들면 그 코드로 본다.
   *
   * @param targetChordName 서버 데이터의 코드 이름 ("C#m7", "A#69" …)
   * @param midiNotes 누른 건반의 MIDI 번호
   */
  public static boolean validate(String targetChordName, List<Integer> midiNotes) {
    if (targetChordName == null || midiNotes == null || midiNotes.isEmpty()) {
      return false;
    }
    Parsed parsed = parse(targetChordName);
    if (parsed == null) {
      return false;
    }
    Rules rules = rulesFor(parsed.type());
    if (rules == null) {
      return false;
    }
    Set<Integer> played = intervalsAbove(parsed.root(), midiNotes);
    if (!rules.matches(played)) {
      return false;
    }
    Set<Integer> target = toSet(rules.template());
    for (String type : TEMPLATES.keySet()) {
      if (type.equals(rules.type())) {
        continue;
      }
      int[] template = TEMPLATES.get(type);
      boolean extendsTarget =
          template.length > target.size() && toSet(template).containsAll(target);
      if (!extendsTarget) {
        continue;
      }
      Rules rival = rulesFor(type);
      if (rival != null && rival.matches(played)) {
        return false;
      }
    }
    return true;
  }

  /** 이 이름을 판정할 수 있는지 (시드 데이터 점검용) */
  public static boolean canParse(String chordName) {
    return chordName != null && parse(chordName) != null;
  }

  private static Parsed parse(String name) {
    Parsed direct = parseCanonical(name);
    if (direct != null) {
      return direct;
    }
    if (SIX_NINE_ALIAS.matcher(name).find()) {
      return parseCanonical(SIX_NINE_ALIAS.matcher(name).replaceFirst("6/9"));
    }
    return null;
  }

  private static Parsed parseCanonical(String name) {
    for (String type : TYPES_LONGEST_FIRST) {
      if (!name.endsWith(type)) {
        continue;
      }
      String rootStr = name.substring(0, name.length() - type.length());
      Integer root = NOTE_INDEX.get(rootStr);
      if (root != null) {
        return new Parsed(root, type);
      }
    }
    Integer root = NOTE_INDEX.get(name);
    return root == null ? null : new Parsed(root, "");
  }

  private static Rules rulesFor(String type) {
    int[] template = TEMPLATES.get(type);
    if (template == null) {
      return null;
    }
    // only a chord with a 7th may drop its 5th (C–E–A is Am/C, not C6)
    boolean hasSeventh = contains(template, 10) || contains(template, 11);
    boolean fifthOptional = hasSeventh && contains(template, PERFECT_FIFTH);
    int[] optional = OPTIONAL.getOrDefault(type, new int[0]);
    Set<Integer> required = new LinkedHashSet<>();
    for (int interval : template) {
      if (!(fifthOptional && interval == PERFECT_FIFTH) && !contains(optional, interval)) {
        required.add(interval);
      }
    }
    Set<Integer> allowed = toSet(template);
    for (int tension : TENSIONS.getOrDefault(type, new int[0])) {
      allowed.add(tension);
    }
    return new Rules(type, template, required, allowed);
  }

  /** 친 음들을 근음 위 음정(피치 클래스, 중복 제거)으로. JS의 {@code %}와 같은 부호 규칙. */
  private static Set<Integer> intervalsAbove(int root, List<Integer> midiNotes) {
    Set<Integer> out = new TreeSet<>();
    for (Integer note : midiNotes) {
      int pc = note % 12;
      out.add((pc - root + 12) % 12);
    }
    return out;
  }

  private static Set<Integer> toSet(int[] values) {
    Set<Integer> out = new LinkedHashSet<>();
    for (int v : values) {
      out.add(v);
    }
    return out;
  }

  private static boolean contains(int[] values, int value) {
    for (int v : values) {
      if (v == value) {
        return true;
      }
    }
    return false;
  }
}
