package tritium.music.core;

import tritium.music.core.model.Music;

public interface MusicListener {

    default void onSongStart(Music music) {
    }

    default void onLyricsLoaded(Music music) {
    }

    default void onCurrentLyricChanged() {
    }
}
