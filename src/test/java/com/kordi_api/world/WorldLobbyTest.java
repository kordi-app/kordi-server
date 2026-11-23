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
    WorldLook look = new WorldLook("mint", "crown", "smile", null);

    assertThat(lobby.dress(id, look)).isPresent();
    lobby.move(id, new WorldMove(1.0, 0.0, 2.0, 0.0, null, null), 1_000);

    var channel = lobby.channelOf(id).orElseThrow();
    assertThat(lobby.playersIn(channel))
        .singleElement()
        .extracting(WorldPlayer::look)
        .isEqualTo(look);
    assertThat(lobby.dress("nobody", look)).isEmpty();
  }
}
