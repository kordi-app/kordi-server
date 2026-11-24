package com.kordi_api.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kordi_api.course.entity.Course;
import com.kordi_api.course.entity.CourseRecord;
import com.kordi_api.course.repository.CourseRecordRepository;
import com.kordi_api.friendship.entity.Friendship;
import com.kordi_api.friendship.repository.FriendshipRepository;
import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.score.entity.QuizBestScore;
import com.kordi_api.score.repository.QuizBestScoreRepository;
import com.kordi_api.support.IntegrationTest;
import com.kordi_api.user.entity.OAuthProvider;
import com.kordi_api.user.entity.User;
import com.kordi_api.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 랭킹의 친구 범위: 나와 수락된 친구만 (대기·거절된 요청, 모르는 사람은 빠진다). 전체 범위는 그대로 공개. */
@IntegrationTest
class FriendRankingFlowTest {

  @Autowired WebApplicationContext context;
  @Autowired UserRepository userRepository;
  @Autowired FriendshipRepository friendshipRepository;
  @Autowired QuizBestScoreRepository bestRepository;
  @Autowired CourseRecordRepository courseRecordRepository;
  @Autowired JsonMapper json;

  private MockMvc mvc;
  private User me;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void 친구_범위는_나와_수락된_친구의_기록만_보여준다() throws Exception {
    String tag = Long.toString(System.nanoTime(), 36);
    me = newUser("me-" + tag);
    User sentFriend = newUser("fs-" + tag); // I asked, they accepted
    User receivedFriend = newUser("fr-" + tag); // they asked, I accepted
    User pending = newUser("p-" + tag);
    User stranger = newUser("s-" + tag);

    Friendship sent = Friendship.request(me, sentFriend);
    sent.accept(sentFriend.getId());
    Friendship received = Friendship.request(receivedFriend, me);
    received.accept(me.getId());
    friendshipRepository.saveAll(List.of(sent, received, Friendship.request(me, pending)));

    Instant at = Instant.parse("2026-11-24T00:00:00Z");
    int i = 0;
    for (User u : List.of(me, sentFriend, receivedFriend, pending, stranger)) {
      // the stranger has the best score / time, then pending, ... me last
      bestRepository.save(QuizBestScore.first(u, Difficulty.HARD, 100 + i * 100, 1, 10, at));
      courseRecordRepository.save(CourseRecord.first(u, Course.RUINS, 90_000 - i * 1_000, at));
      i++;
    }

    JsonNode quiz =
        body(get("/api/rankings").param("difficulty", "HARD").param("scope", "FRIENDS"), me);
    assertThat(quiz.findValuesAsString("nickname"))
        .containsExactly(receivedFriend.getNickname(), sentFriend.getNickname(), me.getNickname());

    JsonNode course =
        body(get("/api/course-rankings").param("course", "ruins").param("scope", "FRIENDS"), me);
    assertThat(course.findValuesAsString("nickname"))
        .containsExactly(receivedFriend.getNickname(), sentFriend.getNickname(), me.getNickname());
    assertThat(course.get(0).get("rank").asInt()).isEqualTo(1);

    // a stranger's friends view holds only themselves
    assertThat(
            body(
                    get("/api/rankings").param("difficulty", "HARD").param("scope", "FRIENDS"),
                    stranger)
                .findValuesAsString("nickname"))
        .containsExactly(stranger.getNickname());

    // the default scope is everyone, and needs no login
    JsonNode all = body(get("/api/rankings").param("difficulty", "HARD"), null);
    assertThat(all.findValuesAsString("nickname"))
        .contains(stranger.getNickname(), pending.getNickname(), me.getNickname());
  }

  @Test
  void 친구_범위는_로그인이_필요하다() throws Exception {
    mvc.perform(get("/api/rankings").param("difficulty", "EASY").param("scope", "FRIENDS"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/course-rankings").param("course", "clouds").param("scope", "FRIENDS"))
        .andExpect(status().isUnauthorized());
  }

  private JsonNode body(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, User user)
      throws Exception {
    if (user != null) {
      request.with(as(user));
    }
    var res = mvc.perform(request).andExpect(status().isOk()).andReturn();
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
