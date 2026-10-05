package tritium.music.core.audio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class MusicBeatGrid {

    private static final int MINIMUM_BEATS = 8;
    private static final int MINIMUM_DOWNBEATS = 2;
    private static final int MINIMUM_BEATS_PER_BAR = 2;
    private static final int MAXIMUM_BEATS_PER_BAR = 8;
    private static final double MINIMUM_INTERVAL_MILLIS = 150;
    private static final double MAXIMUM_INTERVAL_MILLIS = 2_000;
    private static final double MINIMUM_CONFIDENCE = 0.25;
    private static final int JITTER_WINDOW_BEATS = 8;
    private static final double MAXIMUM_JITTER_RATIO = 0.13;
    private static final double MINIMUM_DUPLICATE_RATIO = 0.55;
    private static final int MAXIMUM_MISSING_BEATS_PER_GAP = 4;
    private static final int MAXIMUM_INSERTED_BEATS = 64;
    private static final double MAXIMUM_GAP_MULTIPLE_ERROR = 0.35;
    private static final double MAXIMUM_DOWNBEAT_OFFSET_RATIO = 0.4;
    private static final double MINIMUM_DOWNBEAT_CONGRUENCE = 0.5;
    private static final double MINIMUM_DOMINANT_BAR_RATIO = 0.5;
    private static final double MINIMUM_ACCENT_MARGIN = 0.08;
    private static final double MINIMUM_ACCENT_MARGIN_RATIO = 0.75;
    private static final double LOW_BAND_ACCENT_WEIGHT = 0.45;
    private static final int[] METER_CANDIDATES = {4, 3, 6, 2, 8, 5, 7};
    private static final double COVERAGE_GUARD_BEATS = 2;
    private static final double MINIMUM_WEAK_ACCENT = 0.06;
    private static final double MAXIMUM_WEAK_ACCENT = 0.22;
    private static final double MEASURED_ACCENT_FLOOR = 0.6;
    private static final double MEASURED_ACCENT_RANGE = 0.4;
    private static final double DECAY_INTERVAL_RATIO = 0.34;
    private static final double MINIMUM_DECAY_MILLIS = 70;
    private static final double MAXIMUM_DECAY_MILLIS = 240;
    private static final double FALLBACK_ACCENT = 0.55;

    private final List<Long> beats;
    private final List<Long> downbeats;
    private final double[] beatAccents;
    private final double[] barAccents;
    private final int[] beatsSinceDownbeat;
    private final double beatIntervalMillis;
    private final double confidence;
    private final int beatsPerBar;
    private final int downbeatPhase;
    private final long coverageEndMillis;
    private final boolean reliable;
    private final boolean complete;
    private final String rejection;

    private MusicBeatGrid(List<Long> beats, List<Long> downbeats, double[] beatAccents, double[] barAccents,
                          int[] beatsSinceDownbeat, double beatIntervalMillis, double confidence, int beatsPerBar,
                          int downbeatPhase, long coverageEndMillis, boolean reliable, boolean complete,
                          String rejection) {
        this.complete = complete;
        this.beats = beats;
        this.downbeats = downbeats;
        this.beatAccents = beatAccents;
        this.barAccents = barAccents;
        this.beatsSinceDownbeat = beatsSinceDownbeat;
        this.beatIntervalMillis = beatIntervalMillis;
        this.confidence = confidence;
        this.beatsPerBar = beatsPerBar;
        this.downbeatPhase = downbeatPhase;
        this.coverageEndMillis = coverageEndMillis;
        this.reliable = reliable;
        this.rejection = rejection;
    }

    static MusicBeatGrid build(BeatThisTempoAnalyzer.BeatGrid grid, float[] audio, long coverageEndMillis, boolean complete) {
        if (grid == null || grid.beatTimesMillis().size() < MINIMUM_BEATS) {
            return rejected("too few beats", coverageEndMillis, complete);
        }
        double interval = grid.intervalMillis();
        if (!(interval >= MINIMUM_INTERVAL_MILLIS && interval <= MAXIMUM_INTERVAL_MILLIS)) {
            return rejected("implausible tempo", coverageEndMillis, complete);
        }
        if (grid.confidence() < MINIMUM_CONFIDENCE) {
            return rejected("low beat confidence", coverageEndMillis, complete);
        }
        List<Long> beats = repairBeats(grid.beatTimesMillis(), interval);
        if (beats.size() < MINIMUM_BEATS) {
            return rejected("too few beats", coverageEndMillis, complete);
        }
        if (jitterRatio(beats) > MAXIMUM_JITTER_RATIO) {
            return rejected("unstable tempo", coverageEndMillis, complete, beats, interval, grid.confidence());
        }

        BeatAccentAnalyzer.Accents accents = BeatAccentAnalyzer.measure(audio, 0, beats);
        int[] downbeatIndices = downbeatBeatIndices(beats, grid.downbeatTimesMillis(), interval);
        int meter = dominantGap(downbeatIndices);
        int phase = -1;

        if (meter >= MINIMUM_BEATS_PER_BAR && meter <= MAXIMUM_BEATS_PER_BAR) {
            int candidate = dominantPhase(downbeatIndices, meter);
            if (candidate >= 0 && congruence(downbeatIndices, candidate, meter) >= MINIMUM_DOWNBEAT_CONGRUENCE) {
                phase = candidate;
            }
        }

        if (phase < 0) {
            AccentMeter accentMeter = accentMeter(accents, meter);
            if (accentMeter == null) {
                return rejected("unknown downbeat", coverageEndMillis, complete, beats, interval, grid.confidence());
            }
            meter = accentMeter.meter();
            phase = accentMeter.phase();
        }

        int[] beatsSinceDownbeat = new int[beats.size()];
        for (int index = 0; index < beatsSinceDownbeat.length; index++) {
            beatsSinceDownbeat[index] = Math.floorMod(index - phase, meter);
        }
        double[] barAccents = barAccents(accents.wideband(), beatsSinceDownbeat, meter);
        if (barAccents == null) {
            return rejected("no usable strong beat", coverageEndMillis, complete, beats, interval, grid.confidence());
        }

        long coverageEnd = Math.min(coverageEndMillis, beats.get(beats.size() - 1) + Math.round(interval));
        return new MusicBeatGrid(beats, downbeatsAt(beats, beatsSinceDownbeat), accents.wideband(), barAccents,
                beatsSinceDownbeat, interval, grid.confidence(), meter, phase, coverageEnd, true, complete, null);
    }

    private static MusicBeatGrid rejected(String reason, long coverageEndMillis, boolean complete) {
        return new MusicBeatGrid(List.of(), List.of(), new double[0], new double[0], new int[0], 0, 0, 0, 0,
                coverageEndMillis, false, complete, reason);
    }

    private static MusicBeatGrid rejected(String reason, long coverageEndMillis, boolean complete, List<Long> beats,
                                          double interval, double confidence) {
        return new MusicBeatGrid(beats, List.of(), new double[beats.size()], new double[0], new int[beats.size()],
                interval, confidence, 0, 0, coverageEndMillis, false, complete, reason);
    }

    private static List<Long> downbeatsAt(List<Long> beats, int[] beatsSinceDownbeat) {
        List<Long> result = new ArrayList<>();
        for (int index = 0; index < beatsSinceDownbeat.length; index++) {
            if (beatsSinceDownbeat[index] == 0) {
                result.add(beats.get(index));
            }
        }
        return List.copyOf(result);
    }

    private static List<Long> repairBeats(List<Long> beats, double interval) {
        if (beats.size() < 3 || interval <= 0) {
            return beats;
        }
        List<Long> repaired = new ArrayList<>(beats.size());
        repaired.add(beats.get(0));
        int inserted = 0;
        for (int index = 1; index < beats.size(); index++) {
            long time = beats.get(index);
            long previous = repaired.get(repaired.size() - 1);
            double gap = time - previous;
            if (gap < interval * MINIMUM_DUPLICATE_RATIO) {
                continue;
            }
            int multiple = (int) Math.round(gap / interval);
            if (multiple >= 2
                    && multiple - 1 <= MAXIMUM_MISSING_BEATS_PER_GAP
                    && inserted + multiple - 1 <= MAXIMUM_INSERTED_BEATS
                    && Math.abs(gap - multiple * interval) <= interval * MAXIMUM_GAP_MULTIPLE_ERROR) {
                for (int step = 1; step < multiple; step++) {
                    repaired.add(Math.round(previous + gap * step / multiple));
                }
                inserted += multiple - 1;
            }
            repaired.add(time);
        }
        return inserted == 0 ? beats : List.copyOf(repaired);
    }

    private static double jitterRatio(List<Long> beats) {
        int count = beats.size() - 1;
        if (count < 2) {
            return 1;
        }
        double[] intervals = new double[count];
        for (int index = 0; index < count; index++) {
            intervals[index] = beats.get(index + 1) - beats.get(index);
        }
        double median = median(intervals);
        if (median <= 0) {
            return 1;
        }
        double[] deviations = new double[count];
        for (int index = 0; index < count; index++) {
            int from = Math.max(0, index - JITTER_WINDOW_BEATS);
            int to = Math.min(count, index + JITTER_WINDOW_BEATS + 1);
            double local = median(Arrays.copyOfRange(intervals, from, to));
            deviations[index] = Math.abs(intervals[index] - local);
        }
        return median(deviations) / median;
    }

    private static double median(double[] values) {
        if (values.length == 0) {
            return 0;
        }
        double[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0 ? (sorted[middle - 1] + sorted[middle]) / 2 : sorted[middle];
    }

    private static int[] downbeatBeatIndices(List<Long> beats, List<Long> downbeats, double interval) {
        int[] indices = new int[downbeats.size()];
        int count = 0;
        for (long downbeat : downbeats) {
            int index = nearestBeatIndex(beats, downbeat);
            if (Math.abs(beats.get(index) - downbeat) <= interval * MAXIMUM_DOWNBEAT_OFFSET_RATIO) {
                indices[count++] = index;
            }
        }
        return Arrays.copyOf(indices, count);
    }

    private static int nearestBeatIndex(List<Long> beats, long timeMillis) {
        int low = 0;
        int high = beats.size() - 1;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (beats.get(middle) < timeMillis) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        int index = low;
        if (index > 0 && Math.abs(beats.get(index - 1) - timeMillis) <= Math.abs(beats.get(index) - timeMillis)) {
            index--;
        }
        return index;
    }

    private static int dominantGap(int[] downbeatIndices) {
        int best = 0;
        int bestCount = 0;
        for (int index = 1; index < downbeatIndices.length; index++) {
            int gap = downbeatIndices[index] - downbeatIndices[index - 1];
            if (gap < MINIMUM_BEATS_PER_BAR || gap > MAXIMUM_BEATS_PER_BAR) {
                continue;
            }
            if (best != 0 && gap == best) {
                continue;
            }
            int count = 0;
            for (int other = 1; other < downbeatIndices.length; other++) {
                if (downbeatIndices[other] - downbeatIndices[other - 1] == gap) {
                    count++;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                best = gap;
            }
        }
        int required = Math.max(1, (int) Math.ceil((downbeatIndices.length - 1) * MINIMUM_DOMINANT_BAR_RATIO));
        return bestCount < required ? 0 : best;
    }

    private static int dominantPhase(int[] downbeatIndices, int meter) {
        int best = -1;
        int bestCount = 0;
        for (int phase = 0; phase < meter; phase++) {
            int count = 0;
            for (int index : downbeatIndices) {
                if (Math.floorMod(index, meter) == phase) {
                    count++;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                best = phase;
            }
        }
        return best;
    }

    private static double congruence(int[] downbeatIndices, int phase, int meter) {
        if (downbeatIndices.length == 0) {
            return 0;
        }
        int count = 0;
        for (int index : downbeatIndices) {
            if (Math.floorMod(index, meter) == phase) {
                count++;
            }
        }
        return count / (double) downbeatIndices.length;
    }

    private static AccentMeter accentMeter(BeatAccentAnalyzer.Accents accents, int forcedMeter) {
        int[] meters = forcedMeter >= MINIMUM_BEATS_PER_BAR && forcedMeter <= MAXIMUM_BEATS_PER_BAR
                ? new int[]{forcedMeter}
                : METER_CANDIDATES;
        List<AccentMeter> candidates = new ArrayList<>(meters.length);
        double bestMargin = 0;
        for (int meter : meters) {
            AccentMeter candidate = accentMeterFor(accents, meter);
            if (candidate == null) {
                continue;
            }
            candidates.add(candidate);
            bestMargin = Math.max(bestMargin, candidate.margin());
        }
        if (candidates.isEmpty() || bestMargin < MINIMUM_ACCENT_MARGIN) {
            return null;
        }
        double required = Math.max(MINIMUM_ACCENT_MARGIN, bestMargin * MINIMUM_ACCENT_MARGIN_RATIO);
        AccentMeter best = null;
        for (AccentMeter candidate : candidates) {
            if (candidate.margin() < required) {
                continue;
            }
            if (best == null || candidate.meter() < best.meter()) {
                best = candidate;
            }
        }
        return best;
    }

    private static AccentMeter accentMeterFor(BeatAccentAnalyzer.Accents accents, int meter) {
        if (accents.wideband().length < meter * 2) {
            return null;
        }
        double[] means = new double[meter];
        for (int phase = 0; phase < meter; phase++) {
            double total = 0;
            int count = 0;
            for (int index = phase; index < accents.wideband().length; index += meter) {
                total += accents.blended(index, LOW_BAND_ACCENT_WEIGHT);
                count++;
            }
            means[phase] = count == 0 ? 0 : total / count;
        }
        int phase = 0;
        for (int candidate = 1; candidate < meter; candidate++) {
            if (means[candidate] > means[phase]) {
                phase = candidate;
            }
        }
        double others = 0;
        for (int candidate = 0; candidate < meter; candidate++) {
            if (candidate != phase) {
                others += means[candidate];
            }
        }
        others /= Math.max(1, meter - 1);
        double margin = others <= 1.0e-6 ? means[phase] : (means[phase] - others) / others;
        return new AccentMeter(meter, phase, margin);
    }

    private static double[] barAccents(double[] beatAccents, int[] beatsSinceDownbeat, int beatsPerBar) {
        double[] totals = new double[beatsPerBar];
        int[] counts = new int[beatsPerBar];
        for (int beat = 0; beat < beatsSinceDownbeat.length; beat++) {
            int offset = beatsSinceDownbeat[beat];
            if (offset >= 0 && offset < beatsPerBar) {
                totals[offset] += beatAccents[beat];
                counts[offset]++;
            }
        }
        if (counts[0] == 0) {
            return null;
        }
        double[] result = new double[beatsPerBar];
        double strong = totals[0] / counts[0];
        result[0] = 1;
        for (int offset = 1; offset < beatsPerBar; offset++) {
            double mean = counts[offset] == 0 ? 0 : totals[offset] / counts[offset];
            double ratio = strong <= 1.0e-9 ? 1 : Math.max(0, Math.min(1, mean / strong));
            result[offset] = MINIMUM_WEAK_ACCENT + (MAXIMUM_WEAK_ACCENT - MINIMUM_WEAK_ACCENT) * ratio;
        }
        return result;
    }

    private record AccentMeter(int meter, int phase, double margin) {
    }

    public record Snapshot(boolean reliable, boolean complete, String rejection, double beatIntervalMillis,
                           double confidence, int beatsPerBar, int downbeatPhase, long coverageEndMillis,
                           long[] beats, double[] beatAccents, double[] barAccents) {
    }

    Snapshot snapshot() {
        long[] times = new long[beats.size()];
        for (int index = 0; index < times.length; index++) {
            times[index] = beats.get(index);
        }
        return new Snapshot(reliable, complete, rejection, beatIntervalMillis, confidence, beatsPerBar, downbeatPhase,
                coverageEndMillis, times, beatAccents, barAccents);
    }

    static MusicBeatGrid restore(Snapshot snapshot) {
        if (snapshot == null || snapshot.beats() == null || snapshot.beatAccents() == null || snapshot.barAccents() == null) {
            return null;
        }
        int count = snapshot.beats().length;
        if (count != snapshot.beatAccents().length || !Double.isFinite(snapshot.beatIntervalMillis())) {
            return null;
        }
        List<Long> times = new ArrayList<>(count);
        for (long beat : snapshot.beats()) {
            times.add(beat);
        }
        if (!snapshot.reliable()) {
            return new MusicBeatGrid(times, List.of(), snapshot.beatAccents(), snapshot.barAccents(), new int[count],
                    snapshot.beatIntervalMillis(), snapshot.confidence(), 0, 0, snapshot.coverageEndMillis(),
                    false, snapshot.complete(), snapshot.rejection());
        }
        int meter = snapshot.beatsPerBar();
        if (count < MINIMUM_BEATS || meter < MINIMUM_BEATS_PER_BAR || meter > MAXIMUM_BEATS_PER_BAR
                || snapshot.barAccents().length != meter) {
            return null;
        }
        int phase = Math.floorMod(snapshot.downbeatPhase(), meter);
        int[] beatsSinceDownbeat = new int[count];
        for (int index = 0; index < count; index++) {
            beatsSinceDownbeat[index] = Math.floorMod(index - phase, meter);
        }
        return new MusicBeatGrid(times, downbeatsAt(times, beatsSinceDownbeat), snapshot.beatAccents(),
                snapshot.barAccents(), beatsSinceDownbeat, snapshot.beatIntervalMillis(), snapshot.confidence(),
                meter, phase, snapshot.coverageEndMillis(), true, snapshot.complete(), null);
    }

    public boolean isReliable() {
        return reliable;
    }

    public boolean isComplete() {
        return complete;
    }

    public String rejection() {
        return rejection;
    }

    public double bpm() {
        return beatIntervalMillis <= 0 ? 0 : 60_000 / beatIntervalMillis;
    }

    public double beatIntervalMillis() {
        return beatIntervalMillis;
    }

    public double confidence() {
        return confidence;
    }

    public int beatsPerBar() {
        return beatsPerBar;
    }

    public int downbeatPhase() {
        return downbeatPhase;
    }

    public long coverageEndMillis() {
        return coverageEndMillis;
    }

    public List<Long> beats() {
        return beats;
    }

    public List<Long> downbeats() {
        return downbeats;
    }

    public int beatCount() {
        return beats.size();
    }

    public long beatTime(int index) {
        return beats.get(index);
    }

    public int beatIndexAt(long positionMillis) {
        return floorBeatIndex(positionMillis);
    }

    public boolean isDownbeat(int index) {
        return index >= 0 && index < beatsSinceDownbeat.length && beatsSinceDownbeat[index] == 0;
    }

    public double accentAt(int index) {
        return index >= 0 && index < beatsSinceDownbeat.length ? accentAt(index, 0) : 0;
    }

    public boolean covers(long positionMillis) {
        if (!reliable || beats.isEmpty()) {
            return false;
        }
        long guard = Math.round(beatIntervalMillis * COVERAGE_GUARD_BEATS);
        return positionMillis <= coverageEndMillis - guard && positionMillis >= beats.get(0) - Math.round(beatIntervalMillis);
    }

    public double pulseAt(long positionMillis, int downbeatShiftBeats) {
        if (!covers(positionMillis)) {
            return 0;
        }
        int index = floorBeatIndex(positionMillis);
        if (index < 0) {
            return 0;
        }
        double elapsed = positionMillis - beats.get(index);
        if (elapsed < 0) {
            elapsed = 0;
        }
        double decay = Math.max(MINIMUM_DECAY_MILLIS, Math.min(MAXIMUM_DECAY_MILLIS, beatIntervalMillis * DECAY_INTERVAL_RATIO));
        if (elapsed > decay * 6) {
            return 0;
        }
        double accent = accentAt(index, downbeatShiftBeats);
        return accent <= 0 ? 0 : accent * Math.exp(-elapsed / decay);
    }

    private double accentAt(int index, int downbeatShiftBeats) {
        int offset = beatsSinceDownbeat[index];
        double pattern = offset < beatsPerBar
                ? barAccents[Math.floorMod(offset + downbeatShiftBeats, beatsPerBar)]
                : FALLBACK_ACCENT;
        double measured = MEASURED_ACCENT_FLOOR + MEASURED_ACCENT_RANGE * beatAccents[index];
        return Math.max(0, Math.min(1, pattern * measured));
    }

    private int floorBeatIndex(long positionMillis) {
        int low = 0;
        int high = beats.size() - 1;
        if (positionMillis < beats.get(low)) {
            return -1;
        }
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (beats.get(middle) <= positionMillis) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }
}
