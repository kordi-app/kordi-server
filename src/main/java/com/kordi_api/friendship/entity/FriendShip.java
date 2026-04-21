package com.kordi_api.friendship.entity;

import com.kordi_api.global.common.BaseTimeEntity;
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
public class FriendShip extends BaseTimeEntity {

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
    this.status = FriendshipStatus.ACCEPTED;
  }

  public void reject() {
    this.status = FriendshipStatus.REJECTED;
  }

  public void cancel() {
    this.status = FriendshipStatus.CANCELLED;
  }

  @Builder
  public FriendShip(User sender, User receiver, FriendshipStatus status) {
    this.sender = sender;
    this.receiver = receiver;
    this.status = status;
  }
}
