package com.kordi_api.quiz.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kordi_api.quiz.QuizRules;
import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.score.repository.QuizBestScoreRepository;
import com.kordi_api.score.repository.ScoreRepository;
import com.kordi_api.support.MutableClock;
import com.kordi_api.support.MySqlContainerConfig;
import com.kordi_api.user.entity.OAuthProvider;
import com.kordi_api.user.entity.User;
import com.kordi_api.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Limit;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 퀴즈 판 열기 → 결과 내기 → 랭킹까지 실제 DB로. 서버 시계는 테스트가 옮긴다. */
@SpringBootTest(
    properties = {
      "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "spring.security.oauth2.client.registration.google.client-id=test",
      "spring.security.oauth2.client.registration.google.client-secret=test"
    })
@Import({MySqlContainerConfig.class, QuizSessionFlowTest.ClockConfig.class})
class QuizSessionFlowTest {

  @TestConfiguration(proxyBeanMethods = false)
  static class ClockConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      MutableClock clock = new MutableClock();
      clock.set(System.currentTimeMillis());
      return clock;
    }
  }

  @Autowired WebApplicationContext context;
  @Autowired MutableClock clock;
  @Autowired Clock injectedClock;
  @Autowired UserRepository userRepository;
  @Autowired ScoreRepository scoreRepository;
  @Autowired QuizBestScoreRepository bestRepository;
  @Autowired JsonMapper json;

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void 서버가_채점한_점수만_최고_기록과_랭킹에_들어간다() throws Exception {
    assertThat(injectedClock).isSameAs(clock);
    User user = newUser("flow-a");

    JsonNode session = start(user, Difficulty.EASY);
    assertThat(session.get("timePerQuestionMs").asInt()).isEqualTo(QuizRules.TIME_PER_QUESTION_MS);
    assertThat(session.get("countdownMs").asInt()).isEqualTo(QuizRules.COUNTDOWN_MS);
    JsonNode questions = session.get("questions");
    assertThat(questions.size()).isEqualTo(QuizRules.QUESTIONS_PER_QUIZ);

    // first answer right (root-position triad from the question's notes), the rest time out
    List<Map<String, Object>> answers = new ArrayList<>();
    answers.add(
        Map.of("notes", triad(questions.get(0).get("notes").asString()), "elapsedMs", 2_000));
    for (int i = 1; i < questions.size(); i++) {
      answers.add(Map.of("notes", List.of(), "elapsedMs", QuizRules.TIME_PER_QUESTION_MS));
    }
    clock.set(
        clock.millis() + QuizRules.COUNTDOWN_MS + 2_000 + 9L * QuizRules.TIME_PER_QUESTION_MS);

    String sessionId = session.get("sessionId").asString();
    JsonNode result = submit(user, sessionId, answers, 200);
    assertThat(result.get("correctCount").asInt()).isEqualTo(1);
    assertThat(result.get("totalScore").asInt()).isEqualTo(180);
    assertThat(result.get("results").get(0).get("score").asInt()).isEqualTo(180);
    assertThat(result.get("results").get(1).get("correct").asBoolean()).isFalse();
    assertThat(result.get("best").asInt()).isEqualTo(180);
    assertThat(result.get("improved").asBoolean()).isTrue();

    // a retried submit (response lost) gets the same result, and nothing is recorded twice
    assertThat(submit(user, sessionId, answers, 200)).isEqualTo(result);
    assertThat(scoreRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), Limit.of(10)))
        .hasSize(1);
    assertThat(bestRepository.findByUserIdAndDifficulty(user.getId(), Difficulty.EASY))
        .get()
        .extracting(b -> b.getTotalScore())
        .isEqualTo(180);

    JsonNode ranking =
        body(mvc.perform(get("/api/rankings").param("difficulty", "EASY")).andReturn());
    assertThat(ranking.findValuesAsString("nickname")).contains("flow-a");
  }

  @Test
  void 규칙에_어긋난_결과는_거부되고_판도_사라진다() throws Exception {
    User user = newUser("flow-b");
    JsonNode session = start(user, Difficulty.EASY);
    String sessionId = session.get("sessionId").asString();
    List<Map<String, Object>> fast = new ArrayList<>();
    for (int i = 0; i < QuizRules.QUESTIONS_PER_QUIZ; i++) {
      fast.add(Map.of("notes", List.of(), "elapsedMs", 10_000));
    }
    // submitted right away: 100 s of answers in 0 s of server time
    submit(user, sessionId, fast, 400);
    // the session is burned: even a now-plausible submit is refused
    clock.set(clock.millis() + 200_000);
    submit(user, sessionId, fast, 400);

    // someone else's session can't be submitted
    User other = newUser("flow-c");
    String otherSession = start(other, Difficulty.EASY).get("sessionId").asString();
    submit(user, otherSession, fast, 400);
    // wrong answer count
    submit(other, otherSession, fast.subList(0, 3), 400);

    assertThat(bestRepository.findByUserIdAndDifficulty(user.getId(), Difficulty.EASY)).isEmpty();
  }

  private JsonNode start(User user, Difficulty difficulty) throws Exception {
    return body(
        mvc.perform(
                post("/api/quiz/sessions")
                    .with(as(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"difficulty\":\"" + difficulty + "\"}"))
            .andExpect(status().isOk())
            .andReturn());
  }

  private JsonNode submit(
      User user, String sessionId, List<Map<String, Object>> answers, int status) throws Exception {
    var res =
        mvc.perform(
                post("/api/quiz/sessions/" + sessionId + "/result")
                    .with(as(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("answers", answers))))
            .andExpect(status().is(status))
            .andReturn();
    return status == 200 ? body(res) : null;
  }

  private JsonNode body(org.springframework.test.web.servlet.MvcResult res) throws Exception {
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

  /** "C,E,G" → MIDI notes from middle C up */
  private static List<Integer> triad(String notes) {
    List<String> names = List.of("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B");
    List<Integer> out = new ArrayList<>();
    int prev = 59;
    for (String n : notes.split(",")) {
      int pc = names.indexOf(n.trim());
      int note = prev + 1 + Math.floorMod(pc - (prev + 1), 12);
      out.add(note);
      prev = note;
    }
    return out;
  }
}
