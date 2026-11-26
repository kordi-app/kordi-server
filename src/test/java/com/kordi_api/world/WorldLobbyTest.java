package com.kordi_api.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorldLobbyTest {

  private static WorldLobby.ChannelKey hall(int number) {
    return new WorldLobby.ChannelKey("hall", number);
  }

  private static WorldMove at(double x, Integer midi) {
    return new WorldMove(x, 0.0, 0.0, 0.0, midi, null);
  }

  @Test
  void 채널_정원이_차면_다음_채널로_배정된다() {
    WorldLobby lobby = new WorldLobby();
    for (int i = 0; i < WorldLobby.CHANNEL_CAPACITY; i++) {
      assertThat(lobby.join("hall", "p" + i).orElseThrow().channel()).isEqualTo(hall(1));
    }
    assertThat(lobby.join("hall", "next").orElseThrow().channel()).isEqualTo(hall(2));
    assertThat(lobby.playersIn(hall(1))).hasSize(WorldLobby.CHANNEL_CAPACITY);
    assertThat(lobby.playersIn(hall(2))).hasSize(1);
  }

  @Test
  void 앞_채널에_빈자리가_생기면_다시_채운다() {
    WorldLobby lobby = new WorldLobby();
    String first = lobby.join("hall", "a").orElseThrow().player().id();
    for (int i = 1; i < WorldLobby.CHANNEL_CAPACITY; i++) {
      lobby.join("hall", "p" + i);
    }
    lobby.join("hall", "ch2");
    lobby.leave(first);

    assertThat(lobby.join("hall", "back").orElseThrow().channel()).isEqualTo(hall(1));
  }

  @Test
  void 전체_정원을_넘으면_입장할_수_없다() {
    WorldLobby lobby = new WorldLobby();
    for (int i = 0; i < WorldLobby.MAX_TOTAL; i++) {
      lobby.join("hall", "p" + i);
    }
    assertThat(lobby.join("hall", "late")).isEmpty();
    assertThat(lobby.totalPlayers()).isEqualTo(WorldLobby.MAX_TOTAL);
  }

  @Test
  void 움직인_사람만_묶어서_꺼내고_꺼낸_뒤에는_비운다() {
    WorldLobby lobby = new WorldLobby();
    String a = lobby.join("hall", "a").orElseThrow().player().id();
    lobby.join("hall", "b");

    assertThat(lobby.move(a, at(1.0, 60), 1_000)).isTrue();
    assertThat(lobby.drainMoves(hall(1)))
        .singleElement()
        .satisfies(
            p -> {
              assertThat(p.id()).isEqualTo(a);
              assertThat(p.m()).isEqualTo(60);
            });
    assertThat(lobby.drainMoves(hall(1))).isEmpty();
  }

  @Test
  void 범위를_벗어나거나_너무_잦은_이동은_무시한다() {
    WorldLobby lobby = new WorldLobby();
    String id = lobby.join("hall", "a").orElseThrow().player().id();

    assertThat(lobby.move(id, at(999.0, null), 1_000)).isFalse();
    assertThat(lobby.move(id, at(Double.NaN, null), 1_000)).isFalse();
    assertThat(lobby.move(id, at(0.0, 200), 1_000)).isFalse();
    assertThat(lobby.move(id, new WorldMove(null, 0.0, 0.0, 0.0, null, null), 1_000)).isFalse();
    assertThat(lobby.move("nobody", at(0.0, null), 1_000)).isFalse();

    assertThat(lobby.move(id, at(1.0, null), 2_000)).isTrue();
    assertThat(lobby.move(id, at(2.0, null), 2_010)).isFalse();
  }

  @Test
  void 마지막_사람이_나가면_채널이_사라진다() {
    WorldLobby lobby = new WorldLobby();
    String id = lobby.join("hall", "a").orElseThrow().player().id();

    assertThat(lobby.leave(id)).contains(hall(1));
    assertThat(lobby.channelNumbers()).isEmpty();
    assertThat(lobby.leave(id)).isEmpty();
  }

  @Test
  void 이름은_제어문자와_꺾쇠를_지우고_길이를_자른다() {
    assertThat(WorldLobby.sanitizeName("  <b>hi</b>\n ")).isEqualTo("bhi/b");
    assertThat(WorldLobby.sanitizeName("가나다라마바사아자차카타파하")).hasSize(WorldLobby.MAX_NAME_LENGTH);
    assertThat(WorldLobby.sanitizeName("   ")).isEqualTo(WorldLobby.DEFAULT_NAME);
  }

  @Test
  void 지역마다_채널이_따로_매겨지고_서로_보이지_않는다() {
    WorldLobby lobby = new WorldLobby();
    var inHall = lobby.join("hall", "a").orElseThrow();
    var inNeon = lobby.join("neon", "b").orElseThrow();

    assertThat(inHall.channel()).isEqualTo(new WorldLobby.ChannelKey("hall", 1));
    assertThat(inNeon.channel()).isEqualTo(new WorldLobby.ChannelKey("neon", 1));
    assertThat(lobby.playersIn(inHall.channel()))
        .extracting(WorldPlayer::name)
        .containsExactly("a");
    assertThat(lobby.playersIn(inNeon.channel()))
        .extracting(WorldPlayer::name)
        .containsExactly("b");
  }

  @Test
  void 모르는_지역은_기본_지역으로_들어간다() {
    WorldLobby lobby = new WorldLobby();
    assertThat(lobby.join("../etc", "a").orElseThrow().channel().region()).isEqualTo("hall");
    assertThat(lobby.join(null, "b").orElseThrow().channel().region()).isEqualTo("hall");
  }

  @Test
  void 지역별_접속자_수를_센다() {
    WorldLobby lobby = new WorldLobby();
    lobby.join("hall", "a");
    lobby.join("neon", "b");
    lobby.join("neon", "c");

    assertThat(lobby.playersPerRegion())
        .containsEntry("hall", 1)
        .containsEntry("neon", 2)
        .containsEntry("cathedral", 0)
        .containsEntry("lounge", 0)
        .containsEntry("clouds", 0)
        .containsEntry("night", 0)
        .containsEntry("ruins", 0);
  }

  @Test
  void 높이는_생략하면_0이고_범위를_벗어나면_무시한다() {
    WorldLobby lobby = new WorldLobby();
    String id = lobby.join("clouds", "a").orElseThrow().player().id();
    var channel = new WorldLobby.ChannelKey("clouds", 1);

    assertThat(lobby.move(id, new WorldMove(1.0, null, 0.0, 0.0, null, null), 1_000)).isTrue();
    assertThat(lobby.drainMoves(channel)).singleElement().extracting(WorldPlayer::y).isEqualTo(0.0);

    assertThat(lobby.move(id, new WorldMove(1.0, 99.0, 0.0, 0.0, null, null), 2_000)).isFalse();
    assertThat(lobby.move(id, new WorldMove(1.0, 12.5, 0.0, 0.0, 60, null), 3_000)).isTrue();
    assertThat(lobby.drainMoves(channel))
        .singleElement()
        .extracting(WorldPlayer::y)
        .isEqualTo(12.5);
  }

  @Test
  void 감정_표현은_허용된_것만_전달한다() {
    WorldLobby lobby = new WorldLobby();
    String id = lobby.join("hall", "a").orElseThrow().player().id();
    var channel = new WorldLobby.ChannelKey("hall", 1);

    lobby.move(id, new WorldMove(0.0, 0.0, 0.0, 0.0, null, "wave"), 1_000);
    assertThat(lobby.drainMoves(channel))
        .singleElement()
        .extracting(WorldPlayer::e)
        .isEqualTo("wave");

    lobby.move(id, new WorldMove(0.0, 0.0, 0.0, 0.0, null, "<script>"), 2_000);
    assertThat(lobby.drainMoves(channel)).singleElement().extracting(WorldPlayer::e).isNull();
  }

  @Test
  void 모습을_입히면_이후_이동에도_유지된다() {
    WorldLobby lobby = new WorldLobby();
    String id = lobby.join("hall", "a").orElseThrow().player().id();
    WorldLook look = new WorldLook("mint", "crown", "smile", null, null);

    assertThat(lobby.dress(id, look)).isPresent();
    lobby.move(id, new WorldMove(1.0, 0.0, 2.0, 0.0, null, null), 1_000);

    var channel = lobby.channelOf(id).orElseThrow();
    assertThat(lobby.playersIn(channel))
        .singleElement()
        .extracting(WorldPlayer::look)
        .isEqualTo(look);
    assertThat(lobby.dress("nobody", look)).isEmpty();
  }

  private static WorldMove atBell() {
    return new WorldMove(WorldLobby.BELL_X + 1, 15.4, WorldLobby.BELL_Z, 0.0, null, null);
  }

  @Test
  void 종_곁에서_치면_채널에_별_계단이_열린다() {
    WorldLobby lobby = new WorldLobby();
    String a = lobby.join("hall", "a").orElseThrow().player().id();
    lobby.move(a, atBell(), 1_000);

    assertThat(lobby.ringBell(a, 2_000))
        .hasValueSatisfying(bell -> assertThat(bell.channel()).isEqualTo(hall(1)));
    assertThat(lobby.skyLeft(hall(1), 2_000)).isEqualTo(WorldLobby.SKY_OPEN_MS);
    assertThat(lobby.skyLeft(hall(1), 2_000 + WorldLobby.SKY_OPEN_MS + 1)).isZero();
  }

  @Test
  void 종에서_멀거나_홀이_아니면_칠_수_없다() {
    WorldLobby lobby = new WorldLobby();
    String far = lobby.join("hall", "far").orElseThrow().player().id();
    lobby.move(far, at(10.0, null), 1_000);
    String neon = lobby.join("neon", "neon").orElseThrow().player().id();
    lobby.move(neon, atBell(), 1_000);

    assertThat(lobby.ringBell(far, 2_000)).isEmpty();
    assertThat(lobby.ringBell(neon, 2_000)).isEmpty();
    assertThat(lobby.skyLeft(hall(1), 2_000)).isZero();
  }

  @Test
  void 연달아_치면_쿨다운_안에서는_한_번만_받는다() {
    WorldLobby lobby = new WorldLobby();
    String a = lobby.join("hall", "a").orElseThrow().player().id();
    lobby.move(a, atBell(), 1_000);

    assertThat(lobby.ringBell(a, 2_000)).isPresent();
    assertThat(lobby.ringBell(a, 2_000 + WorldLobby.BELL_COOLDOWN_MS - 1)).isEmpty();
    assertThat(lobby.ringBell(a, 2_000 + WorldLobby.BELL_COOLDOWN_MS)).isPresent();
  }

  @Test
  void 꿈의_바다_꼭대기에_선_사람만_고래를_만났다고_본다() {
    WorldLobby lobby = new WorldLobby();
    WorldMove summit =
        new WorldMove(WorldLobby.WHALE_SUMMIT_X, 7.2, WorldLobby.WHALE_SUMMIT_Z, 0.0, null, null);
    WorldMove below =
        new WorldMove(WorldLobby.WHALE_SUMMIT_X, 0.0, WorldLobby.WHALE_SUMMIT_Z, 0.0, null, null);
    String up = lobby.join("whale", "up").orElseThrow().player().id();
    WorldMove arrival = new WorldMove(0.0, 0.0, 14.0, 0.0, null, null);
    lobby.move(up, arrival, 1_000);
    walk(lobby, up, arrival, summit, 1_000); // 걸어서
    String low = lobby.join("whale", "low").orElseThrow().player().id();
    lobby.move(low, below, 1_000);
    String hall = lobby.join("hall", "hall").orElseThrow().player().id();
    lobby.move(hall, summit, 1_000);

    assertThat(lobby.atWhaleSummit(up)).isTrue();
    assertThat(lobby.atWhaleSummit(low)).isFalse();
    assertThat(lobby.atWhaleSummit(hall)).isFalse();
  }

  @Test
  void 혼자_종을_치고_나갔다_들어와도_별_계단은_열려_있다() {
    WorldLobby lobby = new WorldLobby();
    String a = lobby.join("hall", "a").orElseThrow().player().id();
    lobby.move(a, atBell(), 1_000);
    lobby.ringBell(a, 2_000);
    lobby.leave(a); // 채널이 비어 사라진다

    lobby.join("hall", "back");
    assertThat(lobby.skyLeft(hall(1), 3_000)).isEqualTo(WorldLobby.SKY_OPEN_MS - 1_000);
  }

  private static WorldBall kick(double x, double z) {
    return new WorldBall(0, x, 0.6, z, 4.0, 2.0, 0.0);
  }

  private static WorldBall restAt(double x, double z) {
    return new WorldBall(0, x, 0.6, z, 0.0, 0.0, 0.0);
  }

  @Test
  void 공_곁에서_찬_공은_채널에_남아_나중에_온_사람도_받는다() {
    WorldLobby lobby = new WorldLobby();
    String a = lobby.join("hall", "a").orElseThrow().player().id();
    lobby.move(a, at(3.0, null), 1_000);

    assertThat(lobby.moveBall(a, kick(3.5, 0.2), 1_000)).contains(hall(1));
    lobby.leave(a); // 아무도 없어도 공은 그 자리에

    assertThat(lobby.ballsIn(hall(1), 3_500))
        .singleElement()
        .satisfies(
            b -> {
              assertThat(b.x()).isEqualTo(3.5);
              assertThat(b.vx()).isEqualTo(4.0);
              assertThat(b.age()).isEqualTo(2_500);
            });
  }

  @Test
  void 멀리서_찬_공은_무시한다() {
    WorldLobby lobby = new WorldLobby();
    String a = lobby.join("hall", "a").orElseThrow().player().id();
    lobby.move(a, at(-20.0, null), 1_000);

    assertThat(lobby.moveBall(a, kick(3.5, 0.2), 1_000)).isEmpty();
    assertThat(lobby.ballsIn(hall(1), 1_000)).isEmpty();
  }

  @Test
  void 멈춘_자리는_마지막으로_찬_사람만_정한다() {
    WorldLobby lobby = new WorldLobby();
    String a = lobby.join("hall", "a").orElseThrow().player().id();
    String b = lobby.join("hall", "b").orElseThrow().player().id();
    lobby.move(a, at(3.0, null), 1_000);
    lobby.moveBall(a, kick(3.5, 0.2), 1_000);

    assertThat(lobby.moveBall(b, restAt(40, 5), 2_000)).isEmpty();
    assertThat(lobby.moveBall(a, restAt(9, 1), 5_000)).contains(hall(1));
    assertThat(lobby.ballsIn(hall(1), 5_000))
        .singleElement()
        .satisfies(ball -> assertThat(ball.x()).isEqualTo(9.0));
  }

  @Test
  void 공_번호_속도_지역이_맞지_않으면_무시한다() {
    WorldLobby lobby = new WorldLobby();
    String a = lobby.join("hall", "a").orElseThrow().player().id();
    lobby.move(a, at(3.0, null), 1_000);

    assertThat(
            lobby.moveBall(
                a, new WorldBall(WorldLobby.MAX_BALLS, 3.0, 0.6, 0.0, 1.0, 0.0, 0.0), 1_000))
        .isEmpty();
    assertThat(lobby.moveBall(a, new WorldBall(0, 3.0, 0.6, 0.0, 100.0, 0.0, 0.0), 1_000))
        .isEmpty();
    assertThat(lobby.moveBall(a, new WorldBall(0, 3.0, 0.6, 0.0, Double.NaN, 0.0, 0.0), 1_000))
        .isEmpty();

    String c = lobby.join("clouds", "c").orElseThrow().player().id();
    lobby.move(c, at(3.0, null), 1_000);
    assertThat(lobby.moveBall(c, kick(3.5, 0.2), 1_000)).isEmpty();
  }

  @Test
  void 공_소식이_너무_잦으면_무시한다() {
    WorldLobby lobby = new WorldLobby();
    String a = lobby.join("hall", "a").orElseThrow().player().id();
    lobby.move(a, at(3.0, null), 1_000);

    assertThat(lobby.moveBall(a, kick(3.5, 0.2), 1_000)).isPresent();
    assertThat(lobby.moveBall(a, kick(3.6, 0.2), 1_050)).isEmpty();
    assertThat(lobby.moveBall(a, kick(3.6, 0.2), 1_000 + WorldLobby.MIN_BALL_INTERVAL_MS))
        .isPresent();
  }

  @Test
  void 채널마다_공이_따로다() {
    WorldLobby lobby = new WorldLobby();
    String a = lobby.join("hall", "a").orElseThrow().player().id();
    String n = lobby.join("neon", "n").orElseThrow().player().id();
    lobby.move(a, at(3.0, null), 1_000);
    lobby.move(n, at(3.0, null), 1_000);
    lobby.moveBall(a, kick(3.5, 0.2), 1_000);

    assertThat(lobby.ballsIn(hall(1), 1_000)).hasSize(1);
    assertThat(lobby.ballsIn(new WorldLobby.ChannelKey("neon", 1), 1_000)).isEmpty();
  }

  private static WorldMove cave(double x, double y, double z) {
    return new WorldMove(x, y, z, 0.0, null, null);
  }

  /** 걷기(초당 4칸)로, 실제 클라이언트처럼 0.1초마다 위치를 알리며 to까지 간다. 도착 시각을 돌려준다. */
  private static long walk(WorldLobby lobby, String id, WorldMove from, WorldMove to, long at) {
    double distance = Math.hypot(to.x() - from.x(), to.z() - from.z());
    int steps = Math.max(1, (int) Math.ceil(distance / 0.4));
    for (int i = 1; i <= steps; i++) {
      double k = (double) i / steps;
      lobby.move(
          id,
          new WorldMove(
              from.x() + (to.x() - from.x()) * k,
              from.y() + (to.y() - from.y()) * k,
              from.z() + (to.z() - from.z()) * k,
              0.0,
              null,
              null),
          at + i * 100L);
    }
    return at + steps * 100L;
  }

  /** 입구에서 시작해 다리 건너편 쉼터를 지나 제단까지 걸어간다 */
  private static void walkToAltar(WorldLobby lobby, String id, long from) {
    lobby.move(id, cave(0, 0, 4), from);
    long t = walk(lobby, id, cave(0, 0, 4), cave(0, 0, -38), from);
    walk(lobby, id, cave(0, 0, -38), cave(0.5, 1.2, -51), t);
  }

  private static final WorldLook COSMIC =
      new WorldLook("cream", "star-crown", "default", null, null);

  @Test
  void 동굴_제단은_왕관을_쓰고_다리를_건너_걸어온_사람만_인정한다() {
    WorldLobby lobby = new WorldLobby();
    String walker = lobby.join("cave", "a").orElseThrow().player().id();
    String bare = lobby.join("cave", "b").orElseThrow().player().id();
    lobby.dress(walker, COSMIC);
    walkToAltar(lobby, walker, 1_000);
    walkToAltar(lobby, bare, 1_000);

    assertThat(lobby.checkAltar(walker)).isEqualTo(CaveClaim.OK);
    assertThat(lobby.checkAltar(bare)).isEqualTo(CaveClaim.NO_CROWN);
  }

  @Test
  void 켜지_않은_쉼터_앞으로는_되살아날_수_없다() {
    WorldLobby lobby = new WorldLobby();
    String id = lobby.join("cave", "a").orElseThrow().player().id();
    lobby.dress(id, COSMIC);
    lobby.move(id, cave(0, 0, 4), 1_000);
    lobby.move(id, cave(0, 0, -36.8), 1_100); // 다리 건너편 쉼터 앞으로 "되살아남"
    walk(lobby, id, cave(0, 0, -36.8), cave(0.5, 1.2, -51), 1_100);

    assertThat(lobby.checkAltar(id)).isEqualTo(CaveClaim.MOVEMENT);
  }

  @Test
  void 동굴에서_순간이동하면_인정하지_않는다() {
    WorldLobby lobby = new WorldLobby();
    String id = lobby.join("cave", "a").orElseThrow().player().id();
    lobby.dress(id, COSMIC);
    lobby.move(id, cave(0, 0, 4), 1_000);
    lobby.move(id, cave(0, 0, -38), 1_100); // 42칸을 0.1초에
    lobby.move(id, cave(0.5, 1.2, -51), 30_000);

    assertThat(lobby.checkAltar(id)).isEqualTo(CaveClaim.MOVEMENT);
  }

  @Test
  void 다리를_건너지_않고_제단에_가면_인정하지_않는다() {
    WorldLobby lobby = new WorldLobby();
    String id = lobby.join("cave", "a").orElseThrow().player().id();
    lobby.dress(id, COSMIC);
    lobby.move(id, cave(0, 0, 4), 1_000);
    long t = walk(lobby, id, cave(0, 0, 4), cave(8, 0, -30), 1_000);
    walk(lobby, id, cave(8, 0, -30), cave(0.5, 1.2, -51), t); // 체크포인트(0, -38)를 비켜 간 길

    assertThat(lobby.checkAltar(id)).isEqualTo(CaveClaim.PATH);
  }

  @Test
  void 끊겼던_연결이_풀리며_위치가_몰려_와도_정상으로_본다() {
    WorldLobby lobby = new WorldLobby();
    String id = lobby.join("cave", "a").orElseThrow().player().id();
    lobby.dress(id, COSMIC);
    lobby.move(id, cave(0, 0, 4), 1_000);
    // 2초 동안 오지 않던 위치 20개가 한꺼번에 (걸은 거리 8칸)
    for (int i = 1; i <= 20; i++) {
      lobby.move(id, cave(0, 0, 4 - i * 0.4), 3_000 + i * 31L);
    }
    long t = walk(lobby, id, cave(0, 0, -4), cave(0, 0, -38), 3_700);
    walk(lobby, id, cave(0, 0, -38), cave(0.5, 1.2, -51), t);

    assertThat(lobby.checkAltar(id)).isEqualTo(CaveClaim.OK);
  }

  @Test
  void 낭떠러지에서_쉼터로_되살아나는_건_순간이동이어도_괜찮다() {
    WorldLobby lobby = new WorldLobby();
    String id = lobby.join("cave", "a").orElseThrow().player().id();
    lobby.dress(id, COSMIC);
    lobby.move(id, cave(0, 0, 4), 1_000);
    long t = walk(lobby, id, cave(0, 0, 4), cave(4, 0, -27), 1_000); // 다리 위
    lobby.move(id, cave(0, 0, 4), t + 200); // 떨어져 입구로 (정해진 자리)
    walkToAltar(lobby, id, t + 300);

    assertThat(lobby.checkAltar(id)).isEqualTo(CaveClaim.OK);
  }

  @Test
  void 제단에_막_올라섰는데_서버가_아는_위치가_아직_아래면_다시_물어볼_만하다() {
    WorldLobby lobby = new WorldLobby();
    String id = lobby.join("cave", "a").orElseThrow().player().id();
    lobby.dress(id, COSMIC);
    lobby.move(id, cave(0, 0, 4), 1_000);
    long t = walk(lobby, id, cave(0, 0, 4), cave(0, 0, -38), 1_000);
    walk(lobby, id, cave(0, 0, -38), cave(0.5, 0.6, -49.2), t); // 두 칸 아래 계단

    assertThat(lobby.checkAltar(id)).isEqualTo(CaveClaim.NOT_ON_ALTAR);
    assertThat(CaveClaim.NOT_ON_ALTAR.retryable()).isTrue();
  }

  @Test
  void 꼭대기_바로_아래_계단에서_받침대_곁이면_목걸이를_준다() {
    WorldLobby lobby = new WorldLobby();
    String id = lobby.join("cave", "a").orElseThrow().player().id();
    lobby.dress(id, COSMIC);
    lobby.move(id, cave(0, 0, 4), 1_000);
    long t = walk(lobby, id, cave(0, 0, 4), cave(0, 0, -38), 1_000);
    walk(lobby, id, cave(0, 0, -38), cave(0, 0.9, -50.1), t); // 세 번째 계단, 꼭대기 바로 앞

    assertThat(lobby.checkAltar(id)).isEqualTo(CaveClaim.OK);
  }

  @Test
  void 꿈의_바다_꼭대기도_순간이동해_오면_인정하지_않는다() {
    WorldLobby lobby = new WorldLobby();
    String id = lobby.join("whale", "a").orElseThrow().player().id();
    lobby.move(id, cave(0, 0, 14), 1_000);
    lobby.move(
        id,
        new WorldMove(WorldLobby.WHALE_SUMMIT_X, 7.2, WorldLobby.WHALE_SUMMIT_Z, 0.0, null, null),
        1_100);

    assertThat(lobby.atWhaleSummit(id)).isFalse();
  }

  @Test
  void 보상_지역에_들어오자마자_도착_지점이_아닌_곳에_있으면_인정하지_않는다() {
    WorldLobby lobby = new WorldLobby();
    String id = lobby.join("whale", "a").orElseThrow().player().id();
    lobby.move(
        id,
        new WorldMove(WorldLobby.WHALE_SUMMIT_X, 7.2, WorldLobby.WHALE_SUMMIT_Z, 0.0, null, null),
        1_000);

    assertThat(lobby.atWhaleSummit(id)).isFalse();
  }

  @Test
  void 공은_굴러갈_수_없는_곳으로_끌어올_수_없다() {
    WorldLobby lobby = new WorldLobby();
    String a = lobby.join("hall", "a").orElseThrow().player().id();
    String b = lobby.join("hall", "b").orElseThrow().player().id();
    lobby.move(a, at(3.0, null), 1_000);
    lobby.moveBall(a, kick(3.5, 0.2), 1_000); // 초속 4로 굴러간다
    lobby.moveBall(a, restAt(9, 0.2), 4_000); // 3초 뒤 9에 멈췄다

    // 멈춘 공에서 멀리 떨어진 곳에서 "찼다"고 해도 받지 않는다
    lobby.move(b, at(30.0, null), 5_000);
    assertThat(lobby.moveBall(b, kick(30.5, 0.2), 5_000)).isEmpty();
    // 공 곁에서 차면 받는다
    lobby.move(b, at(8.5, null), 6_000);
    assertThat(lobby.moveBall(b, kick(9.2, 0.2), 6_000)).isPresent();
  }

  @Test
  void 공마다_따로_간격을_센다() {
    WorldLobby lobby = new WorldLobby();
    String a = lobby.join("hall", "a").orElseThrow().player().id();
    lobby.move(a, at(3.0, null), 1_000);

    assertThat(lobby.moveBall(a, kick(3.5, 0.2), 1_000)).isPresent();
    assertThat(lobby.moveBall(a, new WorldBall(1, 3.4, 0.6, 0.3, 1.0, 1.0, 0.0), 1_010))
        .isPresent();
  }
}
