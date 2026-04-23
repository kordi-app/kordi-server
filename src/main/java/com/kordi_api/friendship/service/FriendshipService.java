package com.kordi_api.friendship.service;

import com.kordi_api.friendship.dto.FriendshipResponse;
import com.kordi_api.friendship.entity.Friendship;
import com.kordi_api.friendship.entity.FriendshipStatus;
import com.kordi_api.friendship.repository.FriendshipRepository;
import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.user.entity.User;
import com.kordi_api.user.repository.UserRepository;
import java.util.List;
import java.util.stream.Stream;
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

    User sender = getUserOrThrow(senderId);
    User receiver = getUserOrThrow(receiverId);

    if (friendshipRepository.existsBetween(sender, receiver)) {
      throw new CoreException(CoreExceptionCode.FRIENDSHIP_ALREADY_EXISTS);
    }

    Friendship friendship =
        Friendship.builder()
            .sender(sender)
            .receiver(receiver)
            .status(FriendshipStatus.PENDING)
            .build();

    friendshipRepository.save(friendship);
    return FriendshipResponse.from(friendship);
  }

  @Transactional
  public FriendshipResponse acceptRequest(Long friendshipId, Long userId) {
    Friendship friendship = getFriendshipOrThrow(friendshipId);
    validateReceiver(friendship, userId);
    friendship.accept();
    return FriendshipResponse.from(friendship);
  }

  @Transactional
  public FriendshipResponse rejectRequest(Long friendshipId, Long userId) {
    Friendship friendship = getFriendshipOrThrow(friendshipId);
    validateReceiver(friendship, userId);
    friendship.reject();
    return FriendshipResponse.from(friendship);
  }

  public List<FriendshipResponse> getFriends(Long userId) {
    User user = getUserOrThrow(userId);
    List<Friendship> sent =
        friendshipRepository.findBySenderAndStatus(user, FriendshipStatus.ACCEPTED);
    List<Friendship> received =
        friendshipRepository.findByReceiverAndStatus(user, FriendshipStatus.ACCEPTED);

    return Stream.concat(
            sent.stream().map(FriendshipResponse::from),
            received.stream().map(FriendshipResponse::from))
        .toList();
  }

  public List<FriendshipResponse> getSentRequests(Long userId) {
    User user = getUserOrThrow(userId);
    return friendshipRepository.findBySenderAndStatus(user, FriendshipStatus.PENDING).stream()
        .map(FriendshipResponse::from)
        .toList();
  }

  public List<FriendshipResponse> getReceivedRequests(Long userId) {
    User user = getUserOrThrow(userId);
    return friendshipRepository.findByReceiverAndStatus(user, FriendshipStatus.PENDING).stream()
        .map(FriendshipResponse::from)
        .toList();
  }

  @Transactional
  public FriendshipResponse cancelRequest(Long friendshipId, Long userId) {
    Friendship friendship = getFriendshipOrThrow(friendshipId);
    validateSender(friendship, userId);
    friendship.cancel();
    return FriendshipResponse.from(friendship);
  }

  @Transactional
  public void deleteFriendship(Long friendshipId, Long userId) {
    Friendship friendship = getFriendshipOrThrow(friendshipId);
    validateInvolved(friendship, userId);
    friendshipRepository.delete(friendship);
  }

  private User getUserOrThrow(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CoreException(CoreExceptionCode.USER_NOT_FOUND));
  }

  private Friendship getFriendshipOrThrow(Long friendshipId) {
    return friendshipRepository
        .findById(friendshipId)
        .orElseThrow(() -> new CoreException(CoreExceptionCode.FRIENDSHIP_NOT_FOUND));
  }

  private void validateReceiver(Friendship friendship, Long userId) {
    if (!friendship.getReceiver().getId().equals(userId)) {
      throw new CoreException(CoreExceptionCode.INVALID_REQUEST);
    }
  }

  private void validateSender(Friendship friendship, Long userId) {
    if (!friendship.getSender().getId().equals(userId)) {
      throw new CoreException(CoreExceptionCode.INVALID_REQUEST);
    }
  }

  private void validateInvolved(Friendship friendship, Long userId) {
    if (!friendship.getSender().getId().equals(userId)
        && !friendship.getReceiver().getId().equals(userId)) {
      throw new CoreException(CoreExceptionCode.INVALID_REQUEST);
    }
  }
}
