package com.kordi_api.friendship.dto;

import jakarta.validation.constraints.NotNull;

public record FriendshipRequest(@NotNull Long receiverId) {}
