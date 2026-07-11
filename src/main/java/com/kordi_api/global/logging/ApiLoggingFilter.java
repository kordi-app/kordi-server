package com.kordi_api.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiLoggingFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    long startTime = System.currentTimeMillis();
    try {
      filterChain.doFilter(request, response);
    } finally {
      long elapsed = System.currentTimeMillis() - startTime;
      String query = request.getQueryString();
      String uri = query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;

      log.info("{} {} {} ({}ms)", request.getMethod(), uri, response.getStatus(), elapsed);
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().equals("/favicon.ico");
  }
}
