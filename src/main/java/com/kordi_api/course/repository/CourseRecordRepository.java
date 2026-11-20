package com.kordi_api.course.repository;

import com.kordi_api.course.entity.Course;
import com.kordi_api.course.entity.CourseRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRecordRepository extends JpaRepository<CourseRecord, Long> {

  Optional<CourseRecord> findByUserIdAndCourse(Long userId, Course course);

  @EntityGraph(attributePaths = "user")
  List<CourseRecord> findByCourseOrderByTimeMsAscAchievedAtAsc(Course course, Limit limit);

  /** 이 기록보다 빠른 사람 수 (+1 하면 순위) */
  long countByCourseAndTimeMsLessThan(Course course, long timeMs);
}
