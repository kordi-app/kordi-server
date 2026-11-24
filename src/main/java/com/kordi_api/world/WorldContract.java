package com.kordi_api.world;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 피아노 월드에서 서버가 지키게 하는 규칙과 클라이언트가 거기에 맞춰 만든 값들 (resources/world-contract.json).
 *
 * <p>같은 파일이 클라이언트 저장소(src/widgets/piano-world/engine/world-contract.json)에도 있고, 클라이언트 CI가 두 파일이 같은지
 * 확인한다. 값을 바꿀 때는 두 저장소를 함께 바꾼다. 서버 코드의 상수들은 여기서 읽어 온다.
 *
 * @param protocol 소켓 프로토콜 버전 (클라이언트는 접속할 때 ?v=로 자기 버전을 알린다)
 */
public record WorldContract(
    int protocol,
    List<String> regions,
    List<String> emotes,
    Bounds bounds,
    Movement movement,
    Balls balls,
    CourseRules course,
    Bell bell,
    Whale whale,
    Cave cave) {

  /** 위치로 받아 주는 범위 (가장 넓은 지역인 섬보다 여유 있게) */
  public record Bounds(double maxAbsX, double maxAbsZ, double maxY) {}

  /**
   * 사람이 낼 수 있는 이동 (점프 코스 판정과 보상 지역의 여정 검증이 같이 쓴다). 클라이언트 물리(걷기 4.2, 점프 7.5, 튕기는 발판 12)가 이 안에 있어야
   * 한다.
   *
   * @param burstSeconds 예산이 쌓이는 최대 시간 (네트워크 지연 허용치)
   * @param slack 예산이 이보다 더 마이너스면 사람이 낸 움직임이 아니다
   * @param journeyBurstSeconds 보상 지역 여정의 예산은 더 오래 쌓인다: 끊겼던 연결이 풀리며 위치가 몰려 와도 정상 플레이어를 의심하지 않도록. 그래도
   *     입구에서 제단·꼭대기로의 순간이동(50칸 넘게)은 잡힌다
   * @param respawnReach 정해진 되살아나는 자리에서 이만큼 안이면 그리로 순간이동한 것으로 본다
   * @param arrivalReach 보상 지역에 들어와 처음 알린 위치가 도착 지점에서 이만큼 안이어야 한다
   */
  public record Movement(
      double maxHorizontalSpeed,
      double maxRiseSpeed,
      double burstSeconds,
      double slack,
      double journeyBurstSeconds,
      double respawnReach,
      double arrivalReach) {}

  /** 음표 공이 있는 지역과 지역마다 최대 개수 */
  public record Balls(List<String> regions, int max) {}

  /** 점프 코스 판정 (CourseTimer) */
  public record CourseRules(
      double areaX,
      double areaZMin,
      double areaZMax,
      double floorY,
      double startY,
      double goalTolerance,
      Map<String, CourseGoal> courses) {}

  /** 코스 하나: 골 발판 높이와 사람이 낼 수 있는 최소 기록 */
  public record CourseGoal(double goalY, long minMillis) {}

  public record Spot(double x, double z) {}

  /** 자정의 종: 종탑 지붕 위, 이 안에 서 있어야 칠 수 있다 */
  public record Bell(double x, double z, double reach, double minY) {}

  /** 꿈의 바다: 도착 지점, 꼭대기 섬, 머물러야 하는 시간 */
  public record Whale(
      Spot arrival, double x, double z, double reach, double minY, long minStayMs) {}

  /**
   * 별이 잠든 동굴.
   *
   * @param arrival 들어오면 서는 자리. 낭떠러지에 떨어지면 아직 쉼터를 켜지 않았을 때 여기로 돌아온다
   * @param checkpoint 끊어진 다리 건너편의 별의 쉼터: 제단에 가려면 반드시 지난다
   * @param rests 별의 쉼터 (이 안에 들어서면 켜진다). 떨어지면 마지막으로 켠 쉼터 앞(z + respawnOffsetZ)으로 돌아온다
   */
  public record Cave(
      Spot arrival,
      Altar altar,
      long minStayMs,
      Checkpoint checkpoint,
      List<Spot> rests,
      double restReach,
      double respawnOffsetZ) {}

  public record Altar(double x, double z, double reach, double minY) {}

  public record Checkpoint(double x, double z, double reach) {}

  /** 서버가 쓰는 규칙 (시작할 때 한 번 읽는다) */
  public static final WorldContract RULES = load();

  static WorldContract load() {
    JsonMapper json =
        JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    try (InputStream in = WorldContract.class.getResourceAsStream("/world-contract.json")) {
      if (in == null) {
        throw new IllegalStateException("world-contract.json is missing from the classpath");
      }
      return json.readValue(in, WorldContract.class);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
