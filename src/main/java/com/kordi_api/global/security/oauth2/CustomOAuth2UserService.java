package com.kordi_api.global.security.oauth2;

import com.kordi_api.user.entity.OAuthProvider;
import com.kordi_api.user.entity.User;
import com.kordi_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

  private final UserRepository userRepository;
  private final NicknameGenerator nicknames;

  @Override
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User oAuth2User = super.loadUser(userRequest);

    String registrationId = userRequest.getClientRegistration().getRegistrationId();
    OAuthProvider provider = OAuthProvider.from(registrationId);

    OAuth2UserInfo userInfo =
        OAuth2UserInfoFactory.getOAuth2UserInfo(provider, oAuth2User.getAttributes());

    if (userRepository.findByOauthProviderAndOauthId(provider, userInfo.getOauthId()).isEmpty()) {
      register(provider, userInfo);
    }
    return oAuth2User;
  }

  /**
   * 조회 후 저장 사이에 같은 유저의 중복 콜백이나 같은 닉네임의 동시 가입이 끼어들면 유니크 제약에 걸린다. 그때는 먼저 가입된 행이 있는지 다시 보고, 없으면(닉네임이
   * 겹친 것) 새 닉네임으로 한 번 더 시도한다.
   */
  private void register(OAuthProvider provider, OAuth2UserInfo userInfo) {
    for (int attempt = 0; ; attempt++) {
      try {
        userRepository.save(
            User.builder()
                .email(userInfo.getEmail())
                .nickname(nicknames.uniqueFrom(userInfo.getName()))
                .profileImageUrl(userInfo.getProfileImageUrl())
                .oauthProvider(provider)
                .oauthId(userInfo.getOauthId())
                .build());
        return;
      } catch (DataIntegrityViolationException e) {
        if (userRepository
            .findByOauthProviderAndOauthId(provider, userInfo.getOauthId())
            .isPresent()) {
          return;
        }
        if (attempt >= 1) {
          throw e;
        }
      }
    }
  }
}
