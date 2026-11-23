package com.kordi_api.world;

import com.kordi_api.global.common.ApiResponse;
import com.kordi_api.global.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/world")
@RequiredArgsConstructor
public class WorldTicketController {

  private final WorldTicketService ticketService;

  /** 로그인한 유저가 월드 소켓에서 자기 자신임을 밝힐 1회용 입장권. */
  @PostMapping("/ticket")
  public ApiResponse<WorldTicketResponse> ticket(@LoginUser Long userId) {
    return ApiResponse.success(new WorldTicketResponse(ticketService.issue(userId)));
  }
}
