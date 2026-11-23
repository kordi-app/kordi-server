package com.kordi_api.course.controller;

import com.kordi_api.course.dto.CourseRankingResponse;
import com.kordi_api.course.entity.Course;
import com.kordi_api.course.service.CourseRecordService;
import com.kordi_api.global.common.ApiResponse;
import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 점프 코스 역대 랭킹 (공개). */
@RestController
@RequestMapping("/api/course-rankings")
@RequiredArgsConstructor
public class CourseRankingController {

  private final CourseRecordService courseRecordService;

  /**
   * @param course 지역 이름 (clouds, night, ruins)
   */
  @GetMapping
  public ApiResponse<List<CourseRankingResponse>> ranking(@RequestParam String course) {
    Course target =
        Course.ofRegion(course)
            .orElseThrow(() -> new CoreException(CoreExceptionCode.INVALID_REQUEST));
    return ApiResponse.success(courseRecordService.ranking(target));
  }
}
