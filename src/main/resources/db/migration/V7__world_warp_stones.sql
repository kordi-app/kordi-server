-- The island's warp stones a member has woken, kept with the account so they follow them to any
-- device (guests still keep theirs in the browser). One row per user and stone; the plaza's stone
-- is awake for everyone and never stored.
CREATE TABLE world_warp_stones (
  id bigint NOT NULL AUTO_INCREMENT,
  created_at datetime(6) DEFAULT NULL,
  updated_at datetime(6) DEFAULT NULL,
  user_id bigint NOT NULL,
  stone varchar(20) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_world_warp_stones_user_stone (user_id, stone),
  CONSTRAINT fk_world_warp_stones_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;
