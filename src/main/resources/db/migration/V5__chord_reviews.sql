-- Per-chord quiz stats and a review schedule (Leitner boxes): one row per user and chord,
-- updated from server-scored quiz results and from the review mode. "Today's review" reads the
-- rows whose due_at has passed, weakest box first (idx_chord_reviews_user_due).

CREATE TABLE chord_reviews (
  id bigint NOT NULL AUTO_INCREMENT,
  created_at datetime(6) DEFAULT NULL,
  updated_at datetime(6) DEFAULT NULL,
  correct_count int NOT NULL,
  wrong_count int NOT NULL,
  leitner_box int NOT NULL,
  due_at datetime(6) NOT NULL,
  last_seen_at datetime(6) NOT NULL,
  user_id bigint NOT NULL,
  quiz_chord_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_chord_reviews_user_chord (user_id, quiz_chord_id),
  KEY idx_chord_reviews_user_due (user_id, due_at),
  KEY fk_chord_reviews_chord (quiz_chord_id),
  CONSTRAINT fk_chord_reviews_chord FOREIGN KEY (quiz_chord_id) REFERENCES quiz_chords (id),
  CONSTRAINT fk_chord_reviews_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;
