package tritium.music.core.audio;

import java.util.ArrayList;
import java.util.List;

public record MusicalTimeline(long startMillis, long durationMillis, List<Frame> frames, List<Long> beats,
                              List<Long> downbeats, List<BeatAccent> beatAccents) {

    public MusicalTimeline(long startMillis, long durationMillis, List<Frame> frames, List<Long> beats, List<Long> downbeats) {
        this(startMillis, durationMillis, frames, beats, downbeats, List.of());
    }

    public MusicalTimeline {
        frames = List.copyOf(frames);
        beats = List.copyOf(beats);
        downbeats = List.copyOf(downbeats);
        beatAccents = List.copyOf(beatAccents);
    }

    public static MusicalTimeline empty() {
        return new MusicalTimeline(0, 0, List.of(), List.of(), List.of(), List.of());
    }

    private static int closestIndex(List<Long> times, long target) {
        int closest = 0;
        long distance = Math.abs(times.getFirst() - target);
        for (int i = 1; i < times.size(); i++) {
            long candidateDistance = Math.abs(times.get(i) - target);
            if (candidateDistance >= distance) {
                break;
            }
            closest = i;
            distance = candidateDistance;
        }
        return closest;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    public boolean isUsable() {
        return frames.size() >= 4;
    }

    public Frame frameAt(long timeMillis) {
        if (frames.isEmpty()) {
            return Frame.empty(timeMillis);
        }
        Frame closest = frames.getFirst();
        long distance = Math.abs(closest.timeMillis() - timeMillis);
        for (Frame frame : frames) {
            long candidateDistance = Math.abs(frame.timeMillis() - timeMillis);
            if (candidateDistance >= distance) {
                break;
            }
            closest = frame;
            distance = candidateDistance;
        }
        return closest;
    }

    public List<Long> downbeatsBetween(long fromMillis, long toMillis) {
        List<Long> result = new ArrayList<>();
        for (long downbeat : downbeats) {
            if (downbeat >= fromMillis && downbeat <= toMillis) {
                result.add(downbeat);
            }
        }
        return result;
    }

    public List<Long> beatsBetween(long fromMillis, long toMillis) {
        List<Long> result = new ArrayList<>();
        for (long beat : beats) {
            if (beat >= fromMillis && beat <= toMillis) {
                result.add(beat);
            }
        }
        return result;
    }

    public double beatAccentAt(long timeMillis) {
        if (beatAccents.isEmpty()) {
            return 0.5;
        }
        BeatAccent closest = beatAccents.getFirst();
        long distance = Math.abs(closest.timeMillis() - timeMillis);
        for (BeatAccent accent : beatAccents) {
            long candidateDistance = Math.abs(accent.timeMillis() - timeMillis);
            if (candidateDistance >= distance) {
                break;
            }
            closest = accent;
            distance = candidateDistance;
        }
        return closest.strength();
    }

    public double contentSalience(long fromMillis, long toMillis) {
        double total = 0;
        int count = 0;
        for (Frame frame : frames) {
            if (frame.timeMillis() < fromMillis || frame.timeMillis() > toMillis) {
                continue;
            }
            double loudness = clamp((frame.loudnessDb() + 42) / 32);
            double melody = clamp(frame.melodyDensity());
            double harmonicContent = melody * clamp(frame.harmonicClarity());
            double development = clamp(frame.novelty()) * 0.55 + clamp(frame.energyTrend()) * 0.45;
            total += melody * 0.34 + harmonicContent * 0.22 + development * 0.30 + loudness * 0.14;
            count++;
        }
        return count == 0 ? 0 : clamp(total / count);
    }

    public double positiveEnergyTrend(long fromMillis, long toMillis) {
        double total = 0;
        int count = 0;
        for (Frame frame : frames) {
            if (frame.timeMillis() >= fromMillis && frame.timeMillis() <= toMillis) {
                total += clamp(frame.energyTrend());
                count++;
            }
        }
        return count == 0 ? 0 : clamp(total / count);
    }

    public double boundarySafety(long timeMillis) {
        Frame boundary = frameAt(timeMillis);
        Frame before = frameAt(timeMillis - 750);
        Frame after = frameAt(timeMillis + 750);
        double melodyGap = clamp(1 - boundary.melodyDensity());
        double energyRelease = clamp((before.energyTrend() - boundary.energyTrend() + 1) / 2);
        double phraseChange = clamp(Math.max(boundary.novelty() - before.novelty(), boundary.novelty() - after.novelty()) + 0.35);
        return clamp(melodyGap * 0.46 + energyRelease * 0.30 + phraseChange * 0.24);
    }

    public int barPositionAt(long timeMillis, int meter) {
        if (beats.isEmpty() || downbeats.isEmpty() || meter < 2) {
            return -1;
        }
        int beatIndex = closestIndex(beats, timeMillis);
        int downbeatIndex = closestIndex(beats, downbeats.stream().filter(time -> time <= timeMillis + 80).reduce((first, second) -> second).orElse(downbeats.getFirst()));
        return Math.floorMod(beatIndex - downbeatIndex, meter);
    }

    public record BeatAccent(long timeMillis, double strength) {
    }

    public record Frame(long timeMillis, double loudnessDb, double energyTrend, double melodyPitch,
                        double melodyDensity, double harmonicClarity, double novelty, double[] chroma) {

        public Frame {
            chroma = chroma.clone();
        }

        public static Frame empty(long timeMillis) {
            return new Frame(timeMillis, -80, 0, 0, 0, 0, 0, new double[12]);
        }

        @Override
        public double[] chroma() {
            return chroma.clone();
        }
    }
}
