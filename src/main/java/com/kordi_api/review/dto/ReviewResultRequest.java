package com.kordi_api.review.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param results 친 순서대로 (같은 코드가 여러 번 나와도 된다)
 */
public record ReviewResultRequest(
    @NotNull @Size(min = 1, max = ReviewResultRequest.MAX_RESULTS)
        List<@NotNull @Valid ReviewOutcomeRequest> results) {

  public static final int MAX_RESULTS = 50;
}
