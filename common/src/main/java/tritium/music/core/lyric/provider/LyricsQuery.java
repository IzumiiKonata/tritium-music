package tritium.music.core.lyric.provider;

public record LyricsQuery(long songId, String artists, String title, String album) {
    public LyricsQuery {
        artists = artists == null ? "" : artists;
        title = title == null ? "" : title;
        album = album == null ? "" : album;
    }
}
