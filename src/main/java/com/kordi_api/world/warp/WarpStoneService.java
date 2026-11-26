package com.kordi_api.world.warp;

import com.kordi_api.global.common.RecordWriter;
import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.user.repository.UserRepository;
import com.kordi_api.world.WorldContract;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 섬의 워프석을 계정에 남긴다: 어느 기기에서 들어와도 깨운 워프석이 그대로다.
 *
 * <p>깨우는 일은 클라이언트가 알린다. 워프석은 섬 안에서 이동을 줄여 주는 편의일 뿐 보상이 아니어서, 보상 지역처럼 여정을 검증하지는 않는다. 손님일 때 브라우저에 모아
 * 둔 것도 로그인하면 이 경로로 한꺼번에 옮겨 온다.
 */
@Service
@RequiredArgsConstructor
public class WarpStoneService {

  private static final WorldContract.WarpStones RULES = WorldContract.RULES.warpStones();

  private final AwakeWarpStoneRepository repository;
  private final UserRepository userRepository;
  private final RecordWriter recordWriter;

  /** 내가 깨운 워프석 (처음부터 깨어 있는 것 포함, 정렬) */
  public List<String> awake(Long userId) {
    return withAlwaysAwake(repository.findStonesByUserId(userId));
  }

  /**
   * 워프석들을 깨운다. 이미 깨어 있는 것은 그대로 두니 같은 요청을 여러 번 보내도 된다.
   *
   * @return 깨운 뒤의 전체 목록
   */
  public List<String> wake(Long userId, Collection<String> stones) {
    validate(stones);
    return recordWriter.write(
        () -> {
          Set<String> have = new HashSet<>(repository.findStonesByUserId(userId));
          for (String stone : Set.copyOf(stones)) {
            if (RULES.alwaysAwake().contains(stone) || !have.add(stone)) {
              continue;
            }
            repository.saveAndFlush(
                AwakeWarpStone.of(userRepository.getReferenceById(userId), stone));
          }
          return withAlwaysAwake(have);
        });
  }

  /** 계약에 없는 워프석은 받지 않는다 */
  static void validate(Collection<String> stones) {
    if (stones == null || stones.isEmpty()) {
      throw new CoreException(CoreExceptionCode.REQUIRED_VALUE_MISSING, "stones");
    }
    for (String stone : stones) {
      if (!RULES.stones().contains(stone)) {
        throw new CoreException(CoreExceptionCode.INVALID_REQUEST, "unknown warp stone: " + stone);
      }
    }
  }

  private static List<String> withAlwaysAwake(Collection<String> stored) {
    return Stream.concat(RULES.alwaysAwake().stream(), stored.stream())
        .distinct()
        .sorted()
        .toList();
  }
}
