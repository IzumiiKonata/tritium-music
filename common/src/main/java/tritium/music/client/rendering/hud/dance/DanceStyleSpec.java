package tritium.music.client.rendering.hud.dance;

import java.util.List;

public class DanceStyleSpec {

    public String name;
    public String pack;
    public Double frameDurationMs;
    public Double beatsPerCycle;
    public Double speed;
    public Double scale;
    public Double anchorX;
    public Double anchorY;
    public Double shadowWidth;
    public Double shadowOffsetY;
    public Double opacity;
    public List<String> frames;
    public Atlas atlas;

    public static class Atlas {
        public String file;
        public Integer columns;
        public Integer rows;
        public Integer row;
        public Integer column;
        public Integer x;
        public Integer y;
        public Integer width;
        public Integer height;
    }
}
