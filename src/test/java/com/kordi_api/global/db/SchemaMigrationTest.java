package com.kordi_api.global.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.score.dto.ScoreResponse;
import com.kordi_api.support.MySqlContainerConfig;
import com.kordi_api.user.entity.OAuthProvider;
import com.kordi_api.user.entity.User;
import com.kordi_api.user.repository.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Flyway가 만든 스키마가 엔티티와 맞는지 본다. validate는 컬럼 존재·타입만 보므로, 같은 엔티티로 Hibernate가 직접 만들 DDL을 빈 DB에 올려
 * 테이블마다 SHOW CREATE TABLE(인덱스·유니크·FK·이름까지)을 비교한다. 시간 컬럼이 UTC로 저장되고 JSON에 Z가 붙는지도 함께 본다.
 */
@SpringBootTest(
    properties = {
      "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "spring.security.oauth2.client.registration.google.client-id=test",
      "spring.security.oauth2.client.registration.google.client-secret=test",
      // Hibernate writes the DDL it would create from the entities (no database action)
      "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create",
      "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target="
          + SchemaMigrationTest.HIBERNATE_DDL,
      "spring.jpa.properties.hibernate.hbm2ddl.delimiter=;",
      "spring.jpa.properties.hibernate.format_sql=false"
    })
@Import(MySqlContainerConfig.class)
class SchemaMigrationTest {

  static final String HIBERNATE_DDL = "build/hibernate-create.sql";
  private static final String REFERENCE_DB = "hibernate_reference";

  static {
    // Hibernate appends to the script target: start from an empty file on every run
    try {
      Files.deleteIfExists(Path.of(HIBERNATE_DDL));
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired MySQLContainer mysql;
  @Autowired UserRepository userRepository;
  @Autowired JsonMapper jsonMapper;

  @Test
  void migrationsRunOnEmptyDatabase() {
    List<String> applied =
        jdbc.queryForList(
            "select version from flyway_schema_history where success = 1 order by installed_rank",
            String.class);
    assertThat(applied).startsWith("1", "2", "3");
    assertThat(jdbc.queryForObject("select count(*) from quiz_chords", Long.class)).isEqualTo(576L);
  }

  @Test
  void flywaySchemaEqualsWhatHibernateWouldCreate() throws Exception {
    try (Connection root = rootConnection();
        Statement st = root.createStatement()) {
      st.execute("drop database if exists " + REFERENCE_DB);
      st.execute("create database " + REFERENCE_DB);
      st.execute("use " + REFERENCE_DB);
      for (String sql : Files.readString(Path.of(HIBERNATE_DDL)).split(";")) {
        if (!sql.isBlank()) {
          st.execute(sql);
        }
      }
      Map<String, String> expected = showCreateTables(st, REFERENCE_DB);
      Map<String, String> actual = showCreateTables(st, mysql.getDatabaseName());
      actual.remove("flyway_schema_history");
      assertThat(actual).isEqualTo(expected);
    }
  }

  /**
   * 운영처럼 테이블은 있고 flyway_schema_history는 없는 DB: V1은 건너뛰고(baseline 1) V2부터 돈다. 운영 스키마는 V1 그대로이므로 V1
   * 파일로 만든다. V3가 시도 기록(scores)에서 유저·난이도별 최고 기록을 옮기는지도 본다.
   */
  @Test
  void existingDatabaseIsBaselinedAtVersionOne() throws Exception {
    String legacy = "legacy_prod_like";
    try (Connection root = rootConnection();
        Statement st = root.createStatement()) {
      st.execute("drop database if exists " + legacy);
      st.execute("create database " + legacy);
      st.execute("use " + legacy);
      String v1 =
          Files.readString(Path.of("src/main/resources/db/migration/V1__init_schema.sql"))
              .replaceAll("(?m)^--.*$", "");
      for (String sql : v1.split(";")) {
        if (!sql.isBlank()) {
          st.execute(sql);
        }
      }
      st.execute(
          "insert into quiz_chords (chord_key, chord_type, name, notes, difficulty)"
              + " values ('C', 'MAJOR', 'C', 'C,E,G', 'EASY')");
      st.execute(
          "insert into users (id, email, nickname, oauth_id, oauth_provider, role) values"
              + " (1, 'a@example.com', 'a', 'a', 'GOOGLE', 'USER'),"
              + " (2, 'b@example.com', 'b', 'b', 'GOOGLE', 'USER')");
      st.execute(
          "insert into scores"
              + " (id, created_at, user_id, difficulty, total_score, correct_count, total_count)"
              + " values"
              // user 1, EASY: best 300 reached twice -> the earlier one (id 2) counts
              + " (1, '2026-01-01 00:00:01', 1, 'EASY', 100, 1, 10),"
              + " (2, '2026-01-01 00:00:02', 1, 'EASY', 300, 2, 10),"
              + " (3, '2026-01-01 00:00:03', 1, 'EASY', 300, 3, 10),"
              // user 1, MEDIUM: only empty games -> not on the board
              + " (4, '2026-01-01 00:00:04', 1, 'MEDIUM', 0, 0, 10),"
              // user 2, EASY and HARD
              + " (5, '2026-01-01 00:00:05', 2, 'EASY', 250, 2, 10),"
              + " (6, '2026-01-01 00:00:06', 2, 'HARD', 50, 1, 10),"
              + " (7, '2026-01-01 00:00:07', 2, 'HARD', 180, 1, 10)");
    }
    String url = "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(3306) + "/" + legacy;
    Flyway.configure()
        .dataSource(url, "root", mysql.getPassword())
        .baselineOnMigrate(true)
        .baselineVersion("1")
        .load()
        .migrate();
    try (Connection root = rootConnection();
        Statement st = root.createStatement();
        ResultSet rs =
            st.executeQuery(
                "select version, type from "
                    + legacy
                    + ".flyway_schema_history order by installed_rank")) {
      List<String> history = new ArrayList<>();
      while (rs.next()) {
        history.add(rs.getString(1) + ":" + rs.getString(2));
      }
      assertThat(history).startsWith("1:BASELINE", "2:SQL", "3:SQL");
    }
    try (Connection root = rootConnection();
        Statement st = root.createStatement();
        ResultSet rs = st.executeQuery("select count(*) from " + legacy + ".quiz_chords")) {
      rs.next();
      assertThat(rs.getLong(1)).isEqualTo(576L); // the existing row was kept, not duplicated
    }
    try (Connection root = rootConnection();
        Statement st = root.createStatement();
        ResultSet rs =
            st.executeQuery(
                "select user_id, difficulty, total_score, correct_count, total_count, achieved_at"
                    + " from "
                    + legacy
                    + ".quiz_best_scores order by difficulty, total_score desc, achieved_at")) {
      List<String> best = new ArrayList<>();
      while (rs.next()) {
        best.add(
            rs.getLong(1)
                + " "
                + rs.getString(2)
                + " "
                + rs.getInt(3)
                + " "
                + rs.getInt(4)
                + "/"
                + rs.getInt(5)
                + " "
                + rs.getObject(6, LocalDateTime.class));
      }
      assertThat(best)
          .containsExactly(
              "1 EASY 300 2/10 2026-01-01T00:00:02",
              "2 EASY 250 2/10 2026-01-01T00:00:05",
              "2 HARD 180 1/10 2026-01-01T00:00:07");
    }
    try (Connection root = rootConnection();
        Statement st = root.createStatement();
        ResultSet rs = st.executeQuery("select count(*) from " + legacy + ".scores")) {
      rs.next();
      assertThat(rs.getLong(1)).isEqualTo(7L); // the attempt history stays as it was
    }
  }

  @Test
  void timestampsAreStoredAsUtcAndSerializedWithZ() {
    User user =
        userRepository.saveAndFlush(
            User.builder()
                .email("utc@example.com")
                .nickname("utc-check")
                .oauthProvider(OAuthProvider.GOOGLE)
                .oauthId("utc-check")
                .build());
    Instant createdAt = user.getCreatedAt();
    LocalDateTime stored =
        jdbc.queryForObject(
            "select created_at from users where id = ?", LocalDateTime.class, user.getId());
    // the column holds the UTC wall-clock, whatever the JVM's zone is. MySQL rounds the
    // fraction to microseconds (it doesn't truncate), so allow one microsecond either way —
    // a wrong zone would be off by hours, not micros
    assertThat(stored)
        .isCloseTo(
            LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC), within(1, ChronoUnit.MICROS));
    assertThat(userRepository.findById(user.getId()).orElseThrow().getCreatedAt())
        .isCloseTo(createdAt, within(1, ChronoUnit.MICROS));

    String json =
        jsonMapper.writeValueAsString(
            new ScoreResponse(
                1L, Difficulty.EASY, 0, 0, 0, Instant.parse("2026-11-23T03:04:05.123456Z")));
    assertThat(json).contains("\"createdAt\":\"2026-11-23T03:04:05.123456Z\"");
  }

  private Connection rootConnection() throws Exception {
    String url = "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(3306) + "/";
    return DriverManager.getConnection(url, "root", mysql.getPassword());
  }

  private static Map<String, String> showCreateTables(Statement st, String db) throws Exception {
    List<String> tables = new ArrayList<>();
    try (ResultSet rs = st.executeQuery("show tables from " + db)) {
      while (rs.next()) {
        tables.add(rs.getString(1));
      }
    }
    Map<String, String> out = new TreeMap<>();
    for (String table : tables) {
      try (ResultSet rs = st.executeQuery("show create table " + db + "." + table)) {
        rs.next();
        out.put(table, normalize(rs.getString(2)));
      }
    }
    return out;
  }

  /**
   * Column order is not compared: Hibernate's create script orders columns differently from the
   * update-built tables production has, and the order changes nothing. Every column, key and
   * constraint definition (types, nullability, names) still has to match.
   */
  private static String normalize(String createTable) {
    String[] lines = createTable.replaceAll(" AUTO_INCREMENT=\\d+", "").split("\n");
    List<String> body = new ArrayList<>();
    for (int i = 1; i < lines.length - 1; i++) {
      body.add(lines[i].trim().replaceAll(",$", ""));
    }
    body.sort(null);
    return lines[0] + "\n" + String.join("\n", body) + "\n" + lines[lines.length - 1];
  }
}
