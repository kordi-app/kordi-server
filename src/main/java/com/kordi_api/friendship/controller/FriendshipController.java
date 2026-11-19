package com.kordi_api.friendship.controller;

import com.kordi_api.friendship.dto.FriendshipRequest;
import com.kordi_api.friendship.dto.FriendshipResponse;
import com.kordi_api.friendship.service.FriendshipService;
import com.kordi_api.global.common.ApiResponse;
import com.kordi_api.global.security.LoginUser;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/friendships")
@RequiredArgsConstructor
public class FriendshipController {

  private final FriendshipService friendshipService;

  @PostMapping
  public ApiResponse<FriendshipResponse> sendRequest(
      @LoginUser Long userId, @Valid @RequestBody FriendshipRequest request) {
    return ApiResponse.success(friendshipService.sendRequest(userId, request.receiverId()));
  }

  @GetMapping
  public ApiResponse<List<FriendshipResponse>> getFriends(@LoginUser Long userId) {
    return ApiResponse.success(friendshipService.getFriends(userId));
  }

  @GetMapping("/sent")
  public ApiResponse<List<FriendshipResponse>> getSentRequests(@LoginUser Long userId) {
    return ApiResponse.success(friendshipService.getSentRequests(userId));
  }

  @GetMapping("/received")
  public ApiResponse<List<FriendshipResponse>> getReceivedRequests(@LoginUser Long userId) {
    return ApiResponse.success(friendshipService.getReceivedRequests(userId));
  }

  @PatchMapping("/{id}/accept")
  public ApiResponse<FriendshipResponse> acceptRequest(
      @LoginUser Long userId, @PathVariable Long id) {
    return ApiResponse.success(friendshipService.acceptRequest(id, userId));
  }

  @PatchMapping("/{id}/reject")
  public ApiResponse<FriendshipResponse> rejectRequest(
      @LoginUser Long userId, @PathVariable Long id) {
    return ApiResponse.success(friendshipService.rejectRequest(id, userId));
  }

  @PatchMapping("/{id}/cancel")
  public ApiResponse<FriendshipResponse> cancelRequest(
      @LoginUser Long userId, @PathVariable Long id) {
    return ApiResponse.success(friendshipService.cancelRequest(id, userId));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> deleteFriendship(@LoginUser Long userId, @PathVariable Long id) {
    friendshipService.deleteFriendship(id, userId);
    return ApiResponse.success();
  }
}
