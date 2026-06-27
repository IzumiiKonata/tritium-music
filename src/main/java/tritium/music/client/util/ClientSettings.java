package tritium.music.client.util;

public final class ClientSettings {

    private ClientSettings() {
    }

    public static final Flag RENDER_BLUR = new Flag(false);
    public static final Flag RENDER_GLOW = new Flag(false);
    public static final Flag SHOW_WIDGET_BOUNDARY = new Flag(false);
    public static final Flag DEBUG_MODE = new Flag(false);

    public static final class Flag {
        private boolean value;

        public Flag(boolean value) {
            this.value = value;
        }

        public boolean getValue() {
            return value;
        }

        public void setValue(boolean value) {
            this.value = value;
        }
    }
}
