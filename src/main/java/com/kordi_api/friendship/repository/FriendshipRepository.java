package com.kordi_api.friendship.repository;

import com.kordi_api.friendship.entity.Friendship;
import com.kordi_api.friendship.entity.FriendshipStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

  Optional<Friendship> findByUserLowIdAndUserHighId(Long userLowId, Long userHighId);

  /** 방향과 무관하게 두 유저 사이의 관계를 찾는다. */
  default Optional<Friendship> findBetween(Long userA, Long userB) {
    return findByUserLowIdAndUserHighId(Math.min(userA, userB), Math.max(userA, userB));
  }

  @Query("SELECT f FROM Friendship f JOIN FETCH f.sender JOIN FETCH f.receiver WHERE f.id = :id")
  Optional<Friendship> findWithUsersById(@Param("id") Long id);

  /** 유저가 보냈거나 받은 관계 중 특정 상태인 것 (친구 목록용) */
  @Query(
      "SELECT f FROM Friendship f JOIN FETCH f.sender JOIN FETCH f.receiver"
          + " WHERE (f.sender.id = :userId OR f.receiver.id = :userId) AND f.status = :status"
          + " ORDER BY f.updatedAt DESC")
  List<Friendship> findAllInvolving(
      @Param("userId") Long userId, @Param("status") FriendshipStatus status);

  @Query(
      "SELECT f FROM Friendship f JOIN FETCH f.sender JOIN FETCH f.receiver"
          + " WHERE f.sender.id = :senderId AND f.status = :status"
          + " ORDER BY f.updatedAt DESC")
  List<Friendship> findAllSent(
      @Param("senderId") Long senderId, @Param("status") FriendshipStatus status);

  @Query(
      "SELECT f FROM Friendship f JOIN FETCH f.sender JOIN FETCH f.receiver"
          + " WHERE f.receiver.id = :receiverId AND f.status = :status"
          + " ORDER BY f.updatedAt DESC")
  List<Friendship> findAllReceived(
      @Param("receiverId") Long receiverId, @Param("status") FriendshipStatus status);
}
