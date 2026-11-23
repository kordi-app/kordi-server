-- Baseline: the schema Hibernate (ddl-auto=update) had built on MySQL 8 before Flyway, generated
-- from the entities and dumped with SHOW CREATE TABLE. Constraint names are Hibernate's own
-- (deterministic hashes), so a fresh database matches production name for name.
--
-- Production already has these tables: it is baselined at version 1 (spring.flyway
-- baseline-on-migrate) and never runs this file. Only empty databases do.
--
-- Enum-backed columns are plain VARCHAR here. The old data.sql re-ran
-- `ALTER TABLE ... MODIFY <col> VARCHAR(n) NOT NULL` on every start to turn the MySQL ENUM columns
-- Hibernate first created into text (a new Java enum value was stored as '' or failed). That fix
-- is already part of this schema, and production has applied it, so those ALTERs are gone.
--
-- Timestamps are DATETIME(6) holding UTC (hibernate.jdbc.time_zone=UTC, JVM in UTC).
-- No table options beyond the engine: charset/collation come from the database default, as they
-- did when Hibernate created the tables.

CREATE TABLE users (
  id bigint NOT NULL AUTO_INCREMENT,
  created_at datetime(6) DEFAULT NULL,
  updated_at datetime(6) DEFAULT NULL,
  email varchar(255) NOT NULL,
  nickname varchar(100) NOT NULL,
  oauth_id varchar(255) NOT NULL,
  oauth_provider varchar(20) NOT NULL,
  profile_image_url varchar(500) DEFAULT NULL,
  role varchar(20) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UK94v11ixih73new14vukvmfkjl (oauth_provider, oauth_id),
  UNIQUE KEY UK2ty1xmrrgtn89xt7kyxx6ta7h (nickname)
) ENGINE=InnoDB;

CREATE TABLE adventure_records (
  id bigint NOT NULL AUTO_INCREMENT,
  created_at datetime(6) DEFAULT NULL,
  updated_at datetime(6) DEFAULT NULL,
  achieved_at datetime(6) NOT NULL,
  defeated int NOT NULL,
  duration_ms bigint NOT NULL,
  user_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UK99q6bajblnq1sr4rwhiahg02e (user_id),
  KEY idx_adventure_records_rank (defeated DESC, achieved_at),
  CONSTRAINT FKqysy9dtwtd2yoyfs6hj9lppmi FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE character_appearances (
  id bigint NOT NULL AUTO_INCREMENT,
  created_at datetime(6) DEFAULT NULL,
  updated_at datetime(6) DEFAULT NULL,
  accessory varchar(20) DEFAULT NULL,
  body varchar(20) NOT NULL,
  face varchar(20) NOT NULL,
  hat varchar(20) DEFAULT NULL,
  user_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UKqy03rrjwt0126qai3ic17asdu (user_id),
  CONSTRAINT FKa2r9e8b0mqqm6jk8dbcn2d9mc FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE course_records (
  id bigint NOT NULL AUTO_INCREMENT,
  created_at datetime(6) DEFAULT NULL,
  updated_at datetime(6) DEFAULT NULL,
  achieved_at datetime(6) NOT NULL,
  course varchar(20) NOT NULL,
  time_ms bigint NOT NULL,
  user_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UKagp0sgt8xwxjenbpd7akcv2qg (user_id, course),
  KEY idx_course_records_course_time (course, time_ms),
  CONSTRAINT FKbtv9ttqihuy2f53ddc2ss2hq0 FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE friendships (
  id bigint NOT NULL AUTO_INCREMENT,
  created_at datetime(6) DEFAULT NULL,
  updated_at datetime(6) DEFAULT NULL,
  status varchar(20) NOT NULL,
  user_high_id bigint NOT NULL,
  user_low_id bigint NOT NULL,
  version bigint DEFAULT NULL,
  receiver_id bigint NOT NULL,
  sender_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_friendship_pair (user_low_id, user_high_id),
  KEY FKpk7w2cj6m9n224ny2t7fhi47 (receiver_id),
  KEY FKs7n4v837jm41ijdacqgfe9acw (sender_id),
  CONSTRAINT FKpk7w2cj6m9n224ny2t7fhi47 FOREIGN KEY (receiver_id) REFERENCES users (id),
  CONSTRAINT FKs7n4v837jm41ijdacqgfe9acw FOREIGN KEY (sender_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE quiz_chords (
  id bigint NOT NULL AUTO_INCREMENT,
  chord_key varchar(10) NOT NULL,
  chord_type varchar(40) NOT NULL,
  difficulty varchar(10) NOT NULL,
  name varchar(50) NOT NULL,
  notes varchar(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UKt54idne4alv9srgb2vb40iu9v (chord_key, chord_type)
) ENGINE=InnoDB;

CREATE TABLE refresh_tokens (
  id bigint NOT NULL AUTO_INCREMENT,
  created_at datetime(6) DEFAULT NULL,
  updated_at datetime(6) DEFAULT NULL,
  expired_at datetime(6) NOT NULL,
  previous_token varchar(255) DEFAULT NULL COMMENT '직전 리프레시 토큰의 해시 (동시 재발급 유예용)',
  rotated_at datetime(6) DEFAULT NULL,
  token varchar(255) NOT NULL COMMENT '리프레시 토큰의 SHA-256 해시',
  user_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UKghpmfn23vmxfu3spu3lfg4r2d (token),
  UNIQUE KEY UK7tdcd6ab5wsgoudnvj7xf1b7l (user_id),
  KEY idx_refresh_token_token (token),
  KEY idx_refresh_token_previous_token (previous_token),
  CONSTRAINT FK1lih5y2npsf8u5o3vhdb9y0os FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE scores (
  id bigint NOT NULL AUTO_INCREMENT,
  created_at datetime(6) DEFAULT NULL,
  updated_at datetime(6) DEFAULT NULL,
  correct_count int NOT NULL,
  difficulty varchar(10) NOT NULL,
  total_count int NOT NULL,
  total_score int NOT NULL,
  user_id bigint NOT NULL,
  PRIMARY KEY (id),
  KEY idx_scores_difficulty_total (difficulty, total_score),
  KEY idx_scores_user_difficulty_total (user_id, difficulty, total_score),
  CONSTRAINT FKtkgoiahryd4yntgywbqyyw8o8 FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;
