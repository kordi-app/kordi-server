-- Chord Adventure ranks by score (judgement × combo × boss) instead of monsters beaten.
-- Records from before have score 0 and drop off the board until their owner plays again
-- (kept, not deleted: defeated and duration stay as they were).

-- one statement: MySQL DDL doesn't roll back, so a failure part-way through must not
-- leave the column added but the ranking index gone
ALTER TABLE adventure_records
  ADD COLUMN score int NOT NULL DEFAULT 0,
  DROP INDEX idx_adventure_records_rank,
  ADD INDEX idx_adventure_records_rank (score DESC, achieved_at);
-- the default only fills existing rows; new rows always get a score from the entity
ALTER TABLE adventure_records ALTER COLUMN score DROP DEFAULT;
