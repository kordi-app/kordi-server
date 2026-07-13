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

  @ManyToOne
  @JoinColumn(name = "sender_id")
  private User sender;

  @ManyToOne
  @JoinColumn(name = "receiver_id")
  private User receiver;

  @Enumerated(EnumType.STRING)
  @Column
  private FriendshipStatus status;

  @Column(name = "user_low_id", nullable = false)
  private Long userLowId;

  @Column(name = "user_high_id", nullable = false)
  private Long userHighId;

  public void accept() {
    validatePending();
    this.status = FriendshipStatus.ACCEPTED;
  }

  public void reject() {
    validatePending();
    this.status = FriendshipStatus.REJECTED;
  }

  public void cancel() {
    validatePending();
    this.status = FriendshipStatus.CANCELLED;
  }

  private void validatePending() {
    if (this.status != FriendshipStatus.PENDING) {
      throw new CoreException(CoreExceptionCode.FRIENDSHIP_NOT_PENDING);
    }
  }

  private void assignPair() {
    this.userLowId = Math.min(sender.getId(), receiver.getId());
    this.userHighId = Math.max(sender.getId(), receiver.getId());
  }

  public void reopen(User sender, User receiver) {
    if (this.status == FriendshipStatus.PENDING || this.status == FriendshipStatus.ACCEPTED) {
      throw new CoreException(CoreExceptionCode.FRIENDSHIP_ALREADY_EXISTS);
    }
    this.sender = sender;
    this.receiver = receiver;
    this.status = FriendshipStatus.PENDING;
  }

  @Builder
  public Friendship(User sender, User receiver, FriendshipStatus status) {
    this.sender = sender;
    this.receiver = receiver;
    this.status = status;
    assignPair();
  }
}
