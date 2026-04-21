package com.kordi_api.friendship.dto;

import com.kordi_api.friendship.entity.FriendShip;
import com.kordi_api.friendship.entity.FriendshipStatus;

public record FriendshipResponse(
    Long id,
    Long senderId,
    String senderNickname,
    Long receiverId,
    String receiverNickname,
    FriendshipStatus status) {

  public static FriendshipResponse from(FriendShip friendShip) {
    return new FriendshipResponse(
        friendShip.getId(),
        friendShip.getSender().getId(),
        friendShip.getSender().getNickname(),
        friendShip.getReceiver().getId(),
        friendShip.getReceiver().getNickname(),
        friendShip.getStatus());
  }
}
