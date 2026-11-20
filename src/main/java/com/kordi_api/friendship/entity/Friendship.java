package com.kordi_api.friendship.entity;

import com.kordi_api.global.common.BaseTimeEntity;
import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "friendships",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_friendship_pair",
            columnNames = {"user_low_id", "user_high_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friendship extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sender_id", nullable = false)
  private User sender;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "receiver_id", nullable = false)
  private User receiver;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private FriendshipStatus status;

  /** (작은 id, 큰 id) 쌍 — 방향과 무관하게 두 유저 사이에 관계가 하나만 존재하도록 유니크 제약에 사용 */
  @Column(name = "user_low_id", nullable = false)
  private Long userLowId;

  @Column(name = "user_high_id", nullable = false)
  private Long userHighId;

  /** 동시 수락·취소가 서로를 덮어쓰지 않도록 (늦은 쪽은 409) */
  @Version private Long version;

  @Builder
  private Friendship(User sender, User receiver) {
    this.sender = sender;
    this.receiver = receiver;
    this.status = FriendshipStatus.PENDING;
    this.userLowId = Math.min(sender.getId(), receiver.getId());
    this.userHighId = Math.max(sender.getId(), receiver.getId());
  }

  public static Friendship request(User sender, User receiver) {
    if (sender.getId().equals(receiver.getId())) {
      throw new CoreException(CoreExceptionCode.CANNOT_ADD_SELF);
    }
    return Friendship.builder().sender(sender).receiver(receiver).build();
  }

  /** 이미 관계가 있는 쌍에 다시 요청한다. 상대가 보낸 요청이 대기 중이면 서로 원하는 것이니 바로 친구가 된다. 거절/취소된 관계면 새 요청자 기준으로 다시 연다. */
  public void reopen(User sender, User receiver) {
    if (status == FriendshipStatus.PENDING && isSender(receiver.getId())) {
      this.status = FriendshipStatus.ACCEPTED;
      return;
    }
    if (status == FriendshipStatus.PENDING || status == FriendshipStatus.ACCEPTED) {
      throw new CoreException(CoreExceptionCode.FRIENDSHIP_ALREADY_EXISTS);
    }
    this.sender = sender;
    this.receiver = receiver;
    this.status = FriendshipStatus.PENDING;
  }

  public void accept(Long userId) {
    validateReceiver(userId);
    validatePending();
    this.status = FriendshipStatus.ACCEPTED;
  }

  public void reject(Long userId) {
    validateReceiver(userId);
    validatePending();
    this.status = FriendshipStatus.REJECTED;
  }

  public void cancel(Long userId) {
    validateSender(userId);
    validatePending();
    this.status = FriendshipStatus.CANCELLED;
  }

  public void validateInvolved(Long userId) {
    if (!isSender(userId) && !isReceiver(userId)) {
      throw new CoreException(CoreExceptionCode.FORBIDDEN);
    }
  }

  private boolean isSender(Long userId) {
    return sender.getId().equals(userId);
  }

  private boolean isReceiver(Long userId) {
    return receiver.getId().equals(userId);
  }

  private void validateSender(Long userId) {
    if (!isSender(userId)) {
      throw new CoreException(CoreExceptionCode.FORBIDDEN);
    }
  }

  private void validateReceiver(Long userId) {
    if (!isReceiver(userId)) {
      throw new CoreException(CoreExceptionCode.FORBIDDEN);
    }
  }

  private void validatePending() {
    if (status != FriendshipStatus.PENDING) {
      throw new CoreException(CoreExceptionCode.FRIENDSHIP_NOT_PENDING);
    }
  }
}
