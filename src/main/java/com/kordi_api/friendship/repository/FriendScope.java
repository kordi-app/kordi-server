package com.kordi_api.friendship.repository;

import com.kordi_api.friendship.entity.FriendshipStatus;
import com.kordi_api.friendship.entity.QFriendship;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.jpa.JPAExpressions;

/** 다른 쿼리에 붙이는 "나와 수락된 친구만" 조건 (QueryDSL). 관계는 (작은 id, 큰 id) 한 줄이므로 양쪽 방향을 모두 본다. */
public final class FriendScope {

  private FriendScope() {}

  /**
   * @param userIdPath 거를 유저 id 컬럼
   * @param me 기준 유저
   */
  public static BooleanExpression meOrFriends(NumberPath<Long> userIdPath, Long me) {
    QFriendship low = new QFriendship("friendLow");
    QFriendship high = new QFriendship("friendHigh");
    return userIdPath
        .eq(me)
        .or(
            userIdPath.in(
                JPAExpressions.select(low.userHighId)
                    .from(low)
                    .where(low.userLowId.eq(me), low.status.eq(FriendshipStatus.ACCEPTED))))
        .or(
            userIdPath.in(
                JPAExpressions.select(high.userLowId)
                    .from(high)
                    .where(high.userHighId.eq(me), high.status.eq(FriendshipStatus.ACCEPTED))));
  }
}
