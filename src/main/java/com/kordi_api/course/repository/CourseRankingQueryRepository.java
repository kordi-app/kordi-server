package com.kordi_api.course.repository;

import static com.kordi_api.course.entity.QCourseRecord.courseRecord;
import static com.kordi_api.user.entity.QUser.user;

import com.kordi_api.course.entity.Course;
import com.kordi_api.course.entity.CourseRecord;
import com.kordi_api.friendship.repository.FriendScope;
import com.kordi_api.global.common.Querydsl4RepositorySupport;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 점프 코스 랭킹 (전체 또는 나와 친구만). 빠른 기록부터, 같으면 먼저 낸 기록이 위. */
@Repository
public class CourseRankingQueryRepository extends Querydsl4RepositorySupport<CourseRecord> {

  public CourseRankingQueryRepository() {
    super(CourseRecord.class);
  }

  /**
   * @param friendsOf 이 유저와 친구만 (null이면 전체)
   */
  public List<CourseRecord> findRanking(Course course, Long friendsOf, int limit) {
    return selectFrom(courseRecord)
        .join(courseRecord.user, user)
        .fetchJoin()
        .where(
            courseRecord.course.eq(course),
            friendsOf == null ? null : FriendScope.meOrFriends(user.id, friendsOf))
        .orderBy(courseRecord.timeMs.asc(), courseRecord.achievedAt.asc())
        .limit(limit)
        .fetch();
  }
}
