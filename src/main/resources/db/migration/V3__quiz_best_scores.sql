-- Chord quiz ranking: one "best record" row per user and difficulty, like course_records and
-- adventure_records. The ranking reads only this table; scores stays as the attempt history
-- ("my scores"). Quiz results are now scored by the server (quiz sessions), and the old
-- client-scored POST /api/scores writes history only.

CREATE TABLE quiz_best_scores (
  id bigint NOT NULL AUTO_INCREMENT,
  created_at datetime(6) DEFAULT NULL,
  updated_at datetime(6) DEFAULT NULL,
  achieved_at datetime(6) NOT NULL,
  correct_count int NOT NULL,
  difficulty varchar(10) NOT NULL,
  total_count int NOT NULL,
  total_score int NOT NULL,
  user_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UKt8afafri1vs231eg7k8rn5xmn (user_id, difficulty),
  KEY idx_quiz_best_scores_rank (difficulty, total_score DESC, achieved_at),
  CONSTRAINT FKcul0d9wg5v22hopsl3f3hs1s3 FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

-- Backfill from the attempt history: each user's best per difficulty, and on a tie the earliest
-- attempt (lowest id) — the same row the old ranking query picked. Empty games (0 points) are not
-- put on the board, as with new results.
INSERT INTO quiz_best_scores
  (created_at, updated_at, achieved_at, correct_count, difficulty, total_count, total_score, user_id)
SELECT UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), COALESCE(created_at, UTC_TIMESTAMP(6)),
       correct_count, difficulty, total_count, total_score, user_id
FROM (
  SELECT s.*,
         ROW_NUMBER() OVER (PARTITION BY s.user_id, s.difficulty
                            ORDER BY s.total_score DESC, s.id ASC) AS rn
  FROM scores s
  WHERE s.total_score > 0
) ranked
WHERE rn = 1;

-- Only the old ranking query (best score per difficulty across all attempts) used this index.
-- "My scores" keeps idx_scores_user_difficulty_total.
-- It was added shortly before Flyway; production may not have it yet if that build was never
-- deployed, so drop it only when present (MySQL 8.0 has no DROP INDEX IF EXISTS).
SET @has_idx := (SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'scores'
                   AND index_name = 'idx_scores_difficulty_total');
SET @drop_idx := IF(@has_idx > 0, 'ALTER TABLE scores DROP INDEX idx_scores_difficulty_total', 'DO 0');
PREPARE drop_idx_stmt FROM @drop_idx;
EXECUTE drop_idx_stmt;
DEALLOCATE PREPARE drop_idx_stmt;
