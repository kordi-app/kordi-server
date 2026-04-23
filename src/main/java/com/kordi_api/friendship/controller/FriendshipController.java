package com.kordi_api.friendship.controller;

import com.kordi_api.friendship.dto.FriendshipRequest;
import com.kordi_api.friendship.dto.FriendshipResponse;
import com.kordi_api.friendship.service.FriendshipService;
import com.kordi_api.global.common.ApiResponse;
import com.kordi_api.global.security.CustomUserDetails;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/friendships")
@RequiredArgsConstructor
public class FriendshipController {

  private final FriendshipService friendshipService;

  @PostMapping
  public ApiResponse<FriendshipResponse> sendRequest(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestBody FriendshipRequest request) {
    return ApiResponse.success(
        friendshipService.sendRequest(userDetails.getUser().getId(), request.receiverId()));
  }

  @GetMapping
  public ApiResponse<List<FriendshipResponse>> getFriends(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ApiResponse.success(friendshipService.getFriends(userDetails.getUser().getId()));
  }

  @GetMapping("/sent")
  public ApiResponse<List<FriendshipResponse>> getSentRequests(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ApiResponse.success(friendshipService.getSentRequests(userDetails.getUser().getId()));
  }

  @GetMapping("/received")
  public ApiResponse<List<FriendshipResponse>> getReceivedRequests(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ApiResponse.success(
        friendshipService.getReceivedRequests(userDetails.getUser().getId()));
  }

  @PatchMapping("/{id}/accept")
  public ApiResponse<FriendshipResponse> acceptRequest(
      @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id) {
    return ApiResponse.success(friendshipService.acceptRequest(id, userDetails.getUser().getId()));
  }

  @PatchMapping("/{id}/reject")
  public ApiResponse<FriendshipResponse> rejectRequest(
      @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id) {
    return ApiResponse.success(friendshipService.rejectRequest(id, userDetails.getUser().getId()));
  }

  @PatchMapping("/{id}/cancel")
  public ApiResponse<FriendshipResponse> cancelRequest(
      @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id) {
    return ApiResponse.success(friendshipService.cancelRequest(id, userDetails.getUser().getId()));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> deleteFriendship(
      @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id) {
    friendshipService.deleteFriendship(id, userDetails.getUser().getId());
    return ApiResponse.success(null);
  }
}
