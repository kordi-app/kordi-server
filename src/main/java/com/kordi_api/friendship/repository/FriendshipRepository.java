package com.kordi_api.friendship.repository;

import com.kordi_api.friendship.entity.Friendship;
import com.kordi_api.friendship.entity.FriendshipStatus;
import com.kordi_api.user.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

  @Query(
      "SELECT COUNT(f) > 0 FROM Friendship f"
          + " WHERE (f.sender = :u1 AND f.receiver = :u2)"
          + " OR (f.sender = :u2 AND f.receiver = :u1)")
  boolean existsBetween(@Param("u1") User u1, @Param("u2") User u2);

  List<Friendship> findByReceiverAndStatus(User receiver, FriendshipStatus status);

  List<Friendship> findBySenderAndStatus(User sender, FriendshipStatus status);
}
