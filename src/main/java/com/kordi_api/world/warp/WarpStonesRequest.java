package com.kordi_api.world.warp;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 깨운 워프석들 (한 개, 또는 손님일 때 모아 둔 것 전부)
 *
 * @param stones 워프석 이름 (world-contract.json의 warpStones.stones)
 */
public record WarpStonesRequest(@NotEmpty @Size(max = 16) List<String> stones) {}
