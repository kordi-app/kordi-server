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
@Table(name = "friendships")
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

  @Builder
  public Friendship(User sender, User receiver, FriendshipStatus status) {
    this.sender = sender;
    this.receiver = receiver;
    this.status = status;
  }
}
