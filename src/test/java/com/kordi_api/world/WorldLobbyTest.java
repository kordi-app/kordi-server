package com.kordi_api.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorldLobbyTest {

  private static WorldMove at(double x, Integer midi) {
    return new WorldMove(x, 0.0, 0.0, midi);
  }

  @Test
  void 채널_정원이_차면_다음_채널로_배정된다() {
    WorldLobby lobby = new WorldLobby();
    for (int i = 0; i < WorldLobby.CHANNEL_CAPACITY; i++) {
      assertThat(lobby.join("p" + i).orElseThrow().channel()).isEqualTo(1);
    }
    assertThat(lobby.join("next").orElseThrow().channel()).isEqualTo(2);
    assertThat(lobby.playersIn(1)).hasSize(WorldLobby.CHANNEL_CAPACITY);
    assertThat(lobby.playersIn(2)).hasSize(1);
  }

  @Test
  void 앞_채널에_빈자리가_생기면_다시_채운다() {
    WorldLobby lobby = new WorldLobby();
    String first = lobby.join("a").orElseThrow().player().id();
    for (int i = 1; i < WorldLobby.CHANNEL_CAPACITY; i++) {
      lobby.join("p" + i);
    }
    lobby.join("ch2");
    lobby.leave(first);

    assertThat(lobby.join("back").orElseThrow().channel()).isEqualTo(1);
  }

  @Test
  void 전체_정원을_넘으면_입장할_수_없다() {
    WorldLobby lobby = new WorldLobby();
    for (int i = 0; i < WorldLobby.MAX_TOTAL; i++) {
      lobby.join("p" + i);
    }
    assertThat(lobby.join("late")).isEmpty();
    assertThat(lobby.totalPlayers()).isEqualTo(WorldLobby.MAX_TOTAL);
  }

  @Test
  void 움직인_사람만_묶어서_꺼내고_꺼낸_뒤에는_비운다() {
    WorldLobby lobby = new WorldLobby();
    String a = lobby.join("a").orElseThrow().player().id();
    lobby.join("b");

    assertThat(lobby.move(a, at(1.0, 60), 1_000)).isTrue();
    assertThat(lobby.drainMoves(1))
        .singleElement()
        .satisfies(
            p -> {
              assertThat(p.id()).isEqualTo(a);
              assertThat(p.m()).isEqualTo(60);
            });
    assertThat(lobby.drainMoves(1)).isEmpty();
  }

  @Test
  void 범위를_벗어나거나_너무_잦은_이동은_무시한다() {
    WorldLobby lobby = new WorldLobby();
    String id = lobby.join("a").orElseThrow().player().id();

    assertThat(lobby.move(id, at(999.0, null), 1_000)).isFalse();
    assertThat(lobby.move(id, at(Double.NaN, null), 1_000)).isFalse();
    assertThat(lobby.move(id, at(0.0, 200), 1_000)).isFalse();
    assertThat(lobby.move(id, new WorldMove(null, 0.0, 0.0, null), 1_000)).isFalse();
    assertThat(lobby.move("nobody", at(0.0, null), 1_000)).isFalse();

    assertThat(lobby.move(id, at(1.0, null), 2_000)).isTrue();
    assertThat(lobby.move(id, at(2.0, null), 2_010)).isFalse();
  }

  @Test
  void 마지막_사람이_나가면_채널이_사라진다() {
    WorldLobby lobby = new WorldLobby();
    String id = lobby.join("a").orElseThrow().player().id();

    assertThat(lobby.leave(id)).contains(1);
    assertThat(lobby.channelNumbers()).isEmpty();
    assertThat(lobby.leave(id)).isEmpty();
  }

  @Test
  void 이름은_제어문자와_꺾쇠를_지우고_길이를_자른다() {
    assertThat(WorldLobby.sanitizeName("  <b>hi</b>\n ")).isEqualTo("bhi/b");
    assertThat(WorldLobby.sanitizeName("가나다라마바사아자차카타파하")).hasSize(WorldLobby.MAX_NAME_LENGTH);
    assertThat(WorldLobby.sanitizeName("   ")).isEqualTo(WorldLobby.DEFAULT_NAME);
  }
}
