package com.kordi_api.friendship.repository;

import com.kordi_api.friendship.entity.FriendShip;
import com.kordi_api.friendship.entity.FriendshipStatus;
import com.kordi_api.user.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FriendshipRepository extends JpaRepository<FriendShip, Long> {

  boolean existsBySenderAndReceiver(User sender, User receiver);

  List<FriendShip> findByReceiverAndStatus(User receiver, FriendshipStatus status);

  List<FriendShip> findBySenderAndStatus(User sender, FriendshipStatus status);
}
