package com.kordi_api.friendship.entity;

import static com.kordi_api.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.user.entity.User;
import org.junit.jupiter.api.Test;

class FriendshipTest {

  private final User alice = user(1L);
  private final User bob = user(2L);
  private final User carol = user(3L);

  @Test
  void 요청하면_PENDING이고_쌍은_방향과_무관하게_정렬된다() {
    Friendship friendship = Friendship.request(bob, alice);

    assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.PENDING);
    assertThat(friendship.getUserLowId()).isEqualTo(1L);
    assertThat(friendship.getUserHighId()).isEqualTo(2L);
  }

  @Test
  void 자기_자신에게는_요청할_수_없다() {
    assertThatThrownBy(() -> Friendship.request(alice, alice))
        .isInstanceOf(CoreException.class)
        .extracting("errorCode")
        .isEqualTo(CoreExceptionCode.CANNOT_ADD_SELF);
  }

  @Test
  void 받은_사람만_수락할_수_있다() {
    Friendship friendship = Friendship.request(alice, bob);

    assertForbidden(() -> friendship.accept(alice.getId()));
    assertForbidden(() -> friendship.accept(carol.getId()));

    friendship.accept(bob.getId());
    assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
  }

  @Test
  void 보낸_사람만_취소할_수_있다() {
    Friendship friendship = Friendship.request(alice, bob);

    assertForbidden(() -> friendship.cancel(bob.getId()));

    friendship.cancel(alice.getId());
    assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.CANCELLED);
  }

  @Test
  void 대기_중이_아니면_처리할_수_없다() {
    Friendship friendship = Friendship.request(alice, bob);
    friendship.reject(bob.getId());

    assertThatThrownBy(() -> friendship.accept(bob.getId()))
        .isInstanceOf(CoreException.class)
        .extracting("errorCode")
        .isEqualTo(CoreExceptionCode.FRIENDSHIP_NOT_PENDING);
  }

  @Test
  void 거절된_관계는_반대_방향으로_다시_요청할_수_있다() {
    Friendship friendship = Friendship.request(alice, bob);
    friendship.reject(bob.getId());

    friendship.reopen(bob, alice);

    assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.PENDING);
    assertThat(friendship.getSender()).isEqualTo(bob);
    assertThat(friendship.getReceiver()).isEqualTo(alice);
  }

  @Test
  void 이미_친구거나_대기_중이면_다시_요청할_수_없다() {
    Friendship friendship = Friendship.request(alice, bob);

    assertThatThrownBy(() -> friendship.reopen(alice, bob))
        .isInstanceOf(CoreException.class)
        .extracting("errorCode")
        .isEqualTo(CoreExceptionCode.FRIENDSHIP_ALREADY_EXISTS);
  }

  @Test
  void 관계_당사자가_아니면_삭제할_수_없다() {
    Friendship friendship = Friendship.request(alice, bob);

    assertForbidden(() -> friendship.validateInvolved(carol.getId()));
    friendship.validateInvolved(alice.getId());
    friendship.validateInvolved(bob.getId());
  }

  private void assertForbidden(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(CoreException.class)
        .extracting("errorCode")
        .isEqualTo(CoreExceptionCode.FORBIDDEN);
  }
}
