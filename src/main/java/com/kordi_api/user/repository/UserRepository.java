package com.kordi_api.user.repository;

import com.kordi_api.auth.model.OAuthProvider;
import com.kordi_api.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByOauthProviderAndOauthId(OAuthProvider oauthProvider, String oauthId);

  boolean existsByNickname(String nickname);

  Optional<User> findByNickname(String nickname);
}
