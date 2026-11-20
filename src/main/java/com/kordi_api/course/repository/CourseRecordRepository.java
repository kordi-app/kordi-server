package com.kordi_api.course.repository;

import com.kordi_api.course.entity.Course;
import com.kordi_api.course.entity.CourseRecord;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRecordRepository extends JpaRepository<CourseRecord, Long> {

  Optional<CourseRecord> findByUserIdAndCourse(Long userId, Course course);

  /** 기록 갱신용: 같은 유저의 동시 완주가 서로의 갱신을 덮어쓰지 않도록 행을 잠근다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from CourseRecord r where r.user.id = :userId and r.course = :course")
  Optional<CourseRecord> findForUpdate(
      @Param("userId") Long userId, @Param("course") Course course);

  /** 이 유저가 완주한 적 있는 코스들 (보상 판정) */
  @Query("select r.course from CourseRecord r where r.user.id = :userId")
  List<Course> findCoursesByUserId(@Param("userId") Long userId);

  @EntityGraph(attributePaths = "user")
  List<CourseRecord> findByCourseOrderByTimeMsAscAchievedAtAsc(Course course, Limit limit);

  /** 이 기록보다 빠르거나 같은(먼저 낸) 다른 사람 수: +1 하면 순위 (동률은 먼저 낸 사람이 위, 랭킹 목록과 같은 기준) */
  long countByCourseAndTimeMsLessThanEqualAndUserIdNot(Course course, long timeMs, Long userId);

  long countByCourseAndTimeMsLessThanEqual(Course course, long timeMs);
}
