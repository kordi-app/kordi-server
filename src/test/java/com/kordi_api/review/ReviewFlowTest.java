package com.kordi_api.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kordi_api.quiz.QuizRules;
import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.quiz.entity.QuizChord;
import com.kordi_api.quiz.repository.QuizChordRepository;
import com.kordi_api.support.IntegrationTest;
import com.kordi_api.support.MutableClock;
import com.kordi_api.user.entity.OAuthProvider;
import com.kordi_api.user.entity.User;
import com.kordi_api.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 퀴즈 결과 → 코드별 복습 기록 → 오늘의 복습 카드, 복습 결과 반영, 퀴즈 요청 속도 제한까지 실제 DB로. */
@IntegrationTest
class ReviewFlowTest {

  @Autowired WebApplicationContext context;
  @Autowired MutableClock clock;
  @Autowired UserRepository userRepository;
  @Autowired QuizChordRepository quizChordRepository;
  @Autowired JsonMapper json;

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void 퀴즈에서_틀린_코드가_오늘의_복습에_나온다() throws Exception {
    User user = newUser("review-quiz");
    JsonNode empty = deck(user, 10);
    assertThat(empty.get("dueCount").asLong()).isZero();
    assertThat(empty.get("cards").size()).isZero();

    JsonNode session = startQuiz(user, 200);
    JsonNode questions = session.get("questions");
    List<Map<String, Object>> answers = new ArrayList<>();
    for (int i = 0; i < questions.size(); i++) {
      answers.add(Map.of("notes", List.of(), "elapsedMs", QuizRules.TIME_PER_QUESTION_MS));
    }
    clock.set(
        clock.millis() + QuizRules.COUNTDOWN_MS + 10L * QuizRules.TIME_PER_QUESTION_MS + 1_000);
    perform(
            post("/api/quiz/sessions/" + session.get("sessionId").asString() + "/result")
                .with(as(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("answers", answers))))
        .andExpect(status().isOk());

    // every question timed out: all of them are due, and the deck's chords are the quiz's
    List<Long> asked = new ArrayList<>();
    questions.forEach(q -> asked.add(q.get("id").asLong()));
    JsonNode deck = deck(user, 30);
    assertThat(deck.get("dueCount").asLong()).isEqualTo(asked.stream().distinct().count());
    List<Long> dealt = new ArrayList<>();
    deck.get("cards")
        .forEach(
            card -> {
              dealt.add(card.get("chord").get("id").asLong());
              assertThat(card.get("wrongCount").asInt()).isGreaterThanOrEqualTo(1);
              assertThat(card.get("box").asInt()).isEqualTo(1);
              assertThat(card.get("due").asBoolean()).isTrue();
            });
    assertThat(dealt).containsExactlyInAnyOrderElementsOf(asked.stream().distinct().toList());
  }

  @Test
  void 약하고_밀린_카드부터_내고_모자라면_자주_틀린_카드로_채운다() throws Exception {
    User user = newUser("review-order");
    List<QuizChord> chords = quizChordRepository.findByDifficulty(Difficulty.MEDIUM);
    long a = chords.get(0).getId();
    long b = chords.get(1).getId();
    long c = chords.get(2).getId();
    long d = chords.get(3).getId();
    long e = chords.get(4).getId();
    results(
        user,
        List.of(
            outcome(a, false),
            outcome(b, false),
            outcome(b, false),
            outcome(c, true), // known from the start: box 3, not missed
            outcome(d, false),
            outcome(d, true), // box 2, due in a day
            outcome(e, false),
            outcome(e, true),
            outcome(e, true)), // box 3
        200);

    clock.set(clock.millis() + Duration.ofHours(1).toMillis());
    JsonNode deck = deck(user, 10);
    assertThat(deck.get("dueCount").asLong()).isEqualTo(2);
    assertThat(chordIds(deck)).containsExactly(b, a, d, e); // c was never missed
    assertThat(deck.get("cards").get(2).get("due").asBoolean()).isFalse();

    clock.set(clock.millis() + Duration.ofDays(2).toMillis());
    deck = deck(user, 10);
    assertThat(deck.get("dueCount").asLong()).isEqualTo(3);
    assertThat(chordIds(deck)).containsExactly(b, a, d, e);
    assertThat(chordIds(deck(user, 1))).containsExactly(b);

    // practising b right takes it out of today's review
    JsonNode res = results(user, List.of(outcome(b, true)), 200);
    assertThat(res.get("recorded").asInt()).isEqualTo(1);
    assertThat(res.get("dueCount").asLong()).isEqualTo(2);
    assertThat(chordIds(deck(user, 2))).containsExactly(a, d);
  }

  @Test
  void 없는_코드는_거부하고_아무것도_반영하지_않는다() throws Exception {
    User user = newUser("review-bad");
    long real = quizChordRepository.findByDifficulty(Difficulty.EASY).get(0).getId();
    results(user, List.of(outcome(real, false), outcome(9_999_999L, false)), 400);
    results(user, List.of(), 400);
    assertThat(deck(user, 10).get("cards").size()).isZero();
  }

  @Test
  void 비회원은_복습을_쓸_수_없다() throws Exception {
    perform(get("/api/review/deck")).andExpect(status().isUnauthorized());
  }

  @Test
  void 퀴즈_판은_유저마다_너무_자주_열_수_없다() throws Exception {
    User user = newUser("review-rate");
    for (int i = 0; i < 12; i++) {
      startQuiz(user, 200);
    }
    startQuiz(user, 429);
    // someone else is not affected
    startQuiz(newUser("review-rate-other"), 200);
    // a token comes back after the refill period
    clock.set(clock.millis() + 10_000);
    startQuiz(user, 200);
  }

  private JsonNode startQuiz(User user, int expected) throws Exception {
    MvcResult res =
        perform(
                post("/api/quiz/sessions")
                    .with(as(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"difficulty\":\"EASY\"}"))
            .andExpect(status().is(expected))
            .andReturn();
    return expected == 200 ? body(res) : null;
  }

  private JsonNode deck(User user, int limit) throws Exception {
    return body(
        perform(get("/api/review/deck").param("limit", String.valueOf(limit)).with(as(user)))
            .andExpect(status().isOk())
            .andReturn());
  }

  private JsonNode results(User user, List<Map<String, Object>> results, int expected)
      throws Exception {
    MvcResult res =
        perform(
                post("/api/review/results")
                    .with(as(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("results", results))))
            .andExpect(status().is(expected))
            .andReturn();
    return expected == 200 ? body(res) : null;
  }

  private static Map<String, Object> outcome(long chordId, boolean correct) {
    return Map.of("chordId", chordId, "correct", correct);
  }

  private static List<Long> chordIds(JsonNode deck) {
    List<Long> ids = new ArrayList<>();
    deck.get("cards").forEach(card -> ids.add(card.get("chord").get("id").asLong()));
    return ids;
  }

  private org.springframework.test.web.servlet.ResultActions perform(
      org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
    return mvc.perform(request);
  }

  private JsonNode body(MvcResult res) throws Exception {
    return json.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
  }

  private User newUser(String nickname) {
    return userRepository.saveAndFlush(
        User.builder()
            .email(nickname + "@example.com")
            .nickname(nickname)
            .oauthProvider(OAuthProvider.GOOGLE)
            .oauthId(nickname)
            .build());
  }

  private static RequestPostProcessor as(User user) {
    return authentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
  }
}
