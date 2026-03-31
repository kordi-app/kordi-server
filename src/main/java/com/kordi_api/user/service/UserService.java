package com.kordi_api.user.service;

import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.user.dto.UserResponse;
import com.kordi_api.user.dto.UserUpdateRequest;
import com.kordi_api.user.entity.User;
import com.kordi_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;

  public UserResponse getUser(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CoreException(CoreExceptionCode.USER_NOT_FOUND));
    return UserResponse.from(user);
  }

  @Transactional
  public UserResponse updateProfile(Long userId, UserUpdateRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CoreException(CoreExceptionCode.USER_NOT_FOUND));
    user.updateProfile(request.nickname(), request.profileImageUrl());
    return UserResponse.from(user);
  }

  public boolean isNicknameDuplicated(String nickname) {
    return userRepository.existsByNickname(nickname);
  }
}
