package tritium.music.core.assets;

public record RemoteAsset(String path, long size, String sha256, Kind kind) {

    public enum Kind {
        FONT,
        MODEL,
        NATIVE
    }

    public String fileName() {
        int index = path.lastIndexOf('/');
        return index < 0 ? path : path.substring(index + 1);
    }

    public String folder() {
        int index = path.lastIndexOf('/');
        return index < 0 ? "" : path.substring(0, index);
    }

    public boolean essential() {
        return kind == Kind.FONT;
    }

    public String kindKey() {
        return switch (kind) {
            case FONT -> "tritium-music.ui.assets.kind.font";
            case MODEL -> "tritium-music.ui.assets.kind.model";
            case NATIVE -> "tritium-music.ui.assets.kind.runtime";
        };
    }
}
