package com.kordi_api.world.warp;

import java.util.List;

/**
 * 깨어 있는 워프석 (처음부터 깨어 있는 것 포함, 이름순)
 *
 * @param awake 워프석 이름
 */
public record WarpStonesResponse(List<String> awake) {}
