package com.kordi_api.friendship.service;

import com.kordi_api.friendship.dto.FriendshipResponse;
import com.kordi_api.friendship.entity.FriendShip;
import com.kordi_api.friendship.entity.FriendshipStatus;
import com.kordi_api.friendship.repository.FriendshipRepository;
import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.user.entity.User;
import com.kordi_api.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendshipService {

  private final FriendshipRepository friendshipRepository;
  private final UserRepository userRepository;

  @Transactional
  public FriendshipResponse sendRequest(Long senderId, Long receiverId) {

    if (senderId.equals(receiverId)) {
      throw new CoreException(CoreExceptionCode.CANNOT_ADD_SELF);
    }

    User sender =
        userRepository
            .findById(senderId)
            .orElseThrow(() -> new CoreException(CoreExceptionCode.USER_NOT_FOUND));
    User receiver =
        userRepository
            .findById(receiverId)
            .orElseThrow(() -> new CoreException(CoreExceptionCode.USER_NOT_FOUND));

    if (friendshipRepository.existsBySenderAndReceiver(sender, receiver)) {
      throw new CoreException(CoreExceptionCode.FRIENDSHIP_ALREADY_EXISTS);
    }

    FriendShip friendShip =
        FriendShip.builder()
            .sender(sender)
            .receiver(receiver)
            .status(FriendshipStatus.PENDING)
            .build();

    friendshipRepository.save(friendShip);

    return FriendshipResponse.from(friendShip);
  }

  @Transactional
  public FriendshipResponse acceptRequest(Long friendshipId, Long userId) {
    FriendShip friendShip =
        friendshipRepository
            .findById(friendshipId)
            .orElseThrow(() -> new CoreException(CoreExceptionCode.FRIENDSHIP_NOT_FOUND));

    if (!friendShip.getReceiver().getId().equals(userId)) {
      throw new CoreException(CoreExceptionCode.INVALID_REQUEST);
    }

    friendShip.accept();
    return FriendshipResponse.from(friendShip);
  }

  @Transactional
  public FriendshipResponse rejectRequest(Long friendshipId, Long userId) {
    FriendShip friendShip =
        friendshipRepository
            .findById(friendshipId)
            .orElseThrow(() -> new CoreException(CoreExceptionCode.FRIENDSHIP_NOT_FOUND));

    if (!friendShip.getReceiver().getId().equals(userId)) {
      throw new CoreException(CoreExceptionCode.INVALID_REQUEST);
    }

    friendShip.reject();
    return FriendshipResponse.from(friendShip);
  }

  public List<FriendshipResponse> getFriends(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CoreException(CoreExceptionCode.USER_NOT_FOUND));

    List<FriendShip> sent =
        friendshipRepository.findBySenderAndStatus(user, FriendshipStatus.ACCEPTED);
    List<FriendShip> received =
        friendshipRepository.findByReceiverAndStatus(user, FriendshipStatus.ACCEPTED);

    List<FriendshipResponse> friends = new ArrayList<>();
    friends.addAll(sent.stream().map(FriendshipResponse::from).toList());
    friends.addAll(received.stream().map(FriendshipResponse::from).toList());
    return friends;
  }

  public List<FriendshipResponse> getSentRequests(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CoreException(CoreExceptionCode.USER_NOT_FOUND));
    return friendshipRepository.findBySenderAndStatus(user, FriendshipStatus.PENDING).stream()
        .map(FriendshipResponse::from)
        .toList();
  }

  public List<FriendshipResponse> getReceivedRequests(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CoreException(CoreExceptionCode.USER_NOT_FOUND));
    return friendshipRepository.findByReceiverAndStatus(user, FriendshipStatus.PENDING).stream()
        .map(FriendshipResponse::from)
        .toList();
  }

  @Transactional
  public FriendshipResponse cancelRequest(Long friendShipId, Long userId) {
    FriendShip friendShip =
        friendshipRepository
            .findById(friendShipId)
            .orElseThrow(() -> new CoreException(CoreExceptionCode.FRIENDSHIP_NOT_FOUND));

    if (!friendShip.getSender().getId().equals(userId)) {
      throw new CoreException(CoreExceptionCode.INVALID_REQUEST);
    }

    friendShip.cancel();
    return FriendshipResponse.from(friendShip);
  }

  @Transactional
  public void deleteFriendship(Long friendshipId, Long userId) {
    FriendShip friendShip =
        friendshipRepository
            .findById(friendshipId)
            .orElseThrow(() -> new CoreException(CoreExceptionCode.FRIENDSHIP_NOT_FOUND));

    if (!friendShip.getSender().getId().equals(userId)
        && !friendShip.getReceiver().getId().equals(userId)) {
      throw new CoreException(CoreExceptionCode.INVALID_REQUEST);
    }

    friendshipRepository.delete(friendShip);
  }
}
