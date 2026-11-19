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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendshipService {

  private final FriendshipRepository friendshipRepository;
  private final UserRepository userRepository;

  /**
   * 친구 요청. 동시에 같은 쌍으로 요청이 들어오면 유니크 제약(uk_friendship_pair) 위반이 나고, GlobalExceptionHandler가 409로
   * 응답한다.
   */
  @Transactional
  public FriendshipResponse sendRequest(Long senderId, Long receiverId) {
    if (senderId.equals(receiverId)) {
      throw new CoreException(CoreExceptionCode.CANNOT_ADD_SELF);
    }
    User sender = getUserOrThrow(senderId);
    User receiver = getUserOrThrow(receiverId);

    Friendship friendship =
        friendshipRepository
            .findBetween(senderId, receiverId)
            .map(
                existing -> {
                  existing.reopen(sender, receiver);
                  return existing;
                })
            .orElseGet(() -> friendshipRepository.save(Friendship.request(sender, receiver)));

    return FriendshipResponse.from(friendship);
  }

  @Transactional
  public FriendshipResponse acceptRequest(Long friendshipId, Long userId) {
    Friendship friendship = getFriendshipOrThrow(friendshipId);
    friendship.accept(userId);
    return FriendshipResponse.from(friendship);
  }

  @Transactional
  public FriendshipResponse rejectRequest(Long friendshipId, Long userId) {
    Friendship friendship = getFriendshipOrThrow(friendshipId);
    friendship.reject(userId);
    return FriendshipResponse.from(friendship);
  }

  @Transactional
  public FriendshipResponse cancelRequest(Long friendshipId, Long userId) {
    Friendship friendship = getFriendshipOrThrow(friendshipId);
    friendship.cancel(userId);
    return FriendshipResponse.from(friendship);
  }

  @Transactional
  public void deleteFriendship(Long friendshipId, Long userId) {
    Friendship friendship = getFriendshipOrThrow(friendshipId);
    friendship.validateInvolved(userId);
    friendshipRepository.delete(friendship);
  }

  public List<FriendshipResponse> getFriends(Long userId) {
    return toResponses(friendshipRepository.findAllInvolving(userId, FriendshipStatus.ACCEPTED));
  }

  public List<FriendshipResponse> getSentRequests(Long userId) {
    return toResponses(friendshipRepository.findAllSent(userId, FriendshipStatus.PENDING));
  }

  public List<FriendshipResponse> getReceivedRequests(Long userId) {
    return toResponses(friendshipRepository.findAllReceived(userId, FriendshipStatus.PENDING));
  }

  private List<FriendshipResponse> toResponses(List<Friendship> friendships) {
    return friendships.stream().map(FriendshipResponse::from).toList();
  }

  private User getUserOrThrow(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CoreException(CoreExceptionCode.USER_NOT_FOUND));
  }

  private Friendship getFriendshipOrThrow(Long friendshipId) {
    return friendshipRepository
        .findWithUsersById(friendshipId)
        .orElseThrow(() -> new CoreException(CoreExceptionCode.FRIENDSHIP_NOT_FOUND));
  }
}
