-- Pets: a new appearance slot, and items earned by something other than a course finish
-- (the moon-whale, from the cat's hidden dream sea). Course reward hats are still worked out
-- from course_records; character_unlocks holds only what can't be.
ALTER TABLE character_appearances ADD COLUMN pet varchar(20) DEFAULT NULL;

CREATE TABLE character_unlocks (
  id bigint NOT NULL AUTO_INCREMENT,
  created_at datetime(6) DEFAULT NULL,
  updated_at datetime(6) DEFAULT NULL,
  user_id bigint NOT NULL,
  item varchar(20) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_character_unlocks_user_item (user_id, item),
  CONSTRAINT FKia71aerhltbfh66xn55yx391i FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;
