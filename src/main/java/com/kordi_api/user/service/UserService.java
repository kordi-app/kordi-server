package com.kordi_api.user.service;

import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.user.dto.UserResponse;
import com.kordi_api.user.dto.UserSearchResponse;
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
    return UserResponse.from(getUserOrThrow(userId));
  }

  @Transactional
  public UserResponse updateProfile(Long userId, UserUpdateRequest request) {
    User user = getUserOrThrow(userId);

    if (!user.getNickname().equals(request.nickname())
        && userRepository.existsByNickname(request.nickname())) {
      throw new CoreException(CoreExceptionCode.NICKNAME_DUPLICATED);
    }

    user.updateNickname(request.nickname());
    return UserResponse.from(user);
  }

  public boolean isNicknameDuplicated(String nickname) {
    return userRepository.existsByNickname(nickname);
  }

  public UserSearchResponse searchByNickname(String nickname) {
    return userRepository
        .findByNickname(nickname)
        .map(UserSearchResponse::from)
        .orElseThrow(() -> new CoreException(CoreExceptionCode.USER_NOT_FOUND));
  }

  private User getUserOrThrow(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CoreException(CoreExceptionCode.USER_NOT_FOUND));
  }
}
