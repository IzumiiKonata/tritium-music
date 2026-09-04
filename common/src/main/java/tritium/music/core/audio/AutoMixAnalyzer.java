package tritium.music.core.audio;

import javax.sound.sampled.AudioFormat;
import java.util.Arrays;

final class AutoMixAnalyzer {
    private static final int ENVELOPE_RATE = 50;
    private static final int MAX_ENVELOPE_FRAMES = ENVELOPE_RATE * 64;

    private final double[] envelope = new double[MAX_ENVELOPE_FRAMES];
    private final double[] lowEnvelope = new double[MAX_ENVELOPE_FRAMES];
    private final double[] highEnvelope = new double[MAX_ENVELOPE_FRAMES];
    private final long timelineOffsetMillis;
    private int envelopeSize;
    private int envelopeWriteOffset;
    private long totalEnvelopeFrames;
    private double frameSquareSum;
    private double frameLowSquareSum;
    private double frameHighSquareSum;
    private int frameSampleCount;
    private int samplesPerEnvelopeFrame;
    private float sampleRate;
    private double lowPassState;
    private double upperPassState;

    AutoMixAnalyzer() {
        this(0);
    }

    AutoMixAnalyzer(long timelineOffsetMillis) {
        this.timelineOffsetMillis = Math.max(0, timelineOffsetMillis);
    }

    private static double[] onsetEnvelope(double[] values, double[] lowValues, double[] highValues) {
        double[] onset = new double[values.length];
        double[] full = logEnvelope(smoothEnvelope(values, 3));
        double[] low = logEnvelope(smoothEnvelope(lowValues, 3));
        double[] high = logEnvelope(smoothEnvelope(highValues, 3));
        for (int i = 1; i < values.length; i++) {
            double fullRise = Math.max(0, full[i] - full[i - 1]);
            double lowRise = Math.max(0, low[i] - low[i - 1]);
            double highRise = Math.max(0, high[i] - high[i - 1]);
            onset[i] = fullRise * 0.8 + lowRise * 1.25 + highRise * 1.05;
        }
        double[] localMean = smoothEnvelope(onset, ENVELOPE_RATE / 2 + 1);
        for (int i = 0; i < onset.length; i++) {
            onset[i] = Math.max(0, onset[i] - localMean[i] * 0.55);
        }
        double scale = percentile(onset, 0.94);
        if (scale > 1.0e-9) {
            for (int i = 0; i < onset.length; i++) {
                onset[i] = Math.min(1, onset[i] / scale);
            }
        }
        return onset;
    }

    private static double[] logEnvelope(double[] values) {
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = Math.log1p(values[i] * 80);
        }
        return result;
    }

    private static double[] smoothEnvelope(double[] values, int windowSize) {
        double[] result = new double[values.length];
        int radius = windowSize / 2;
        for (int i = 0; i < values.length; i++) {
            int start = Math.max(0, i - radius);
            int end = Math.min(values.length, i + radius + 1);
            double sum = 0;
            for (int j = start; j < end; j++) {
                sum += values[j];
            }
            result[i] = sum / (end - start);
        }
        return result;
    }

    private static BeatEstimate estimateBeat(double[] onset) {
        int minLag = Math.max(1, Math.round(ENVELOPE_RATE * 60f / 190f));
        int maxLag = Math.min(onset.length / 3, Math.round(ENVELOPE_RATE * 60f / 70f));
        double bestScore = 0;
        double secondScore = 0;
        int bestLag = 0;
        int bestPhase = 0;
        double onsetScale = Math.max(1.0e-9, percentile(onset, 0.9));
        for (int lag = minLag; lag <= maxLag; lag++) {
            double correlation = correlation(onset, lag);
            double harmonic = lag * 2 <= maxLag ? correlation(onset, lag * 2) : 0;
            PhaseEstimate phase = estimatePhase(onset, lag, onsetScale);
            double bpm = 60.0 * ENVELOPE_RATE / lag;
            double tempoPrior = 0.86 + 0.14 * Math.exp(-0.5 * Math.pow(Math.log(bpm / 122) / 0.52, 2));
            double score = (correlation * 0.58 + harmonic * 0.14 + phase.strength * 0.28) * tempoPrior;
            if (score > bestScore) {
                if (bestLag == 0 || Math.abs(lag - bestLag) > 2) {
                    secondScore = bestScore;
                }
                bestScore = score;
                bestLag = lag;
                bestPhase = phase.phase;
            } else if (Math.abs(lag - bestLag) > 2 && score > secondScore) {
                secondScore = score;
            }
        }
        if (bestLag == 0) {
            return new BeatEstimate(0, 0, 0);
        }
        double refinedLag = refineLag(onset, bestLag, minLag, maxLag);
        double separation = bestScore <= 1.0e-9 ? 0 : Math.max(0, (bestScore - secondScore) / bestScore);
        double confidence = Math.min(1, bestScore * 0.78 + separation * 0.22);
        return new BeatEstimate(refinedLag, bestPhase, confidence);
    }

    private static PhaseEstimate estimatePhase(double[] onset, int lag, double scale) {
        int bestPhase = 0;
        double bestStrength = 0;
        for (int phase = 0; phase < lag; phase++) {
            double score = 0;
            int count = 0;
            for (int i = phase; i < onset.length; i += lag) {
                double local = onset[i];
                if (i > 0) {
                    local = Math.max(local, onset[i - 1] * 0.72);
                }
                if (i + 1 < onset.length) {
                    local = Math.max(local, onset[i + 1] * 0.72);
                }
                score += local;
                count++;
            }
            double strength = score / Math.max(1, count) / scale;
            if (strength > bestStrength) {
                bestStrength = strength;
                bestPhase = phase;
            }
        }
        return new PhaseEstimate(bestPhase, Math.min(1, bestStrength));
    }

    private static double refineLag(double[] onset, int lag, int minLag, int maxLag) {
        if (lag <= minLag || lag >= maxLag) {
            return lag;
        }
        double left = correlation(onset, lag - 1);
        double center = correlation(onset, lag);
        double right = correlation(onset, lag + 1);
        double denominator = left - 2 * center + right;
        if (Math.abs(denominator) < 1.0e-9) {
            return lag;
        }
        return lag + Math.max(-0.5, Math.min(0.5, 0.5 * (left - right) / denominator));
    }

    private static double correlation(double[] values, int lag) {
        double numerator = 0;
        double leftEnergy = 0;
        double rightEnergy = 0;
        for (int i = lag; i < values.length; i++) {
            numerator += values[i] * values[i - lag];
            leftEnergy += values[i] * values[i];
            rightEnergy += values[i - lag] * values[i - lag];
        }
        return numerator / Math.sqrt(Math.max(1.0e-12, leftEnergy * rightEnergy));
    }

    private static int firstSustained(double[] values, double threshold, int frames) {
        int run = 0;
        for (int i = 0; i < values.length; i++) {
            run = values[i] >= threshold ? run + 1 : 0;
            if (run >= frames) {
                return i - frames + 1;
            }
        }
        return 0;
    }

    private static int firstWindowAbove(double[] values, double threshold, int frames) {
        for (int i = 0; i <= values.length - frames; i++) {
            if (windowRms(values, i, i + frames) >= threshold) {
                return i;
            }
        }
        return 0;
    }

    private static int lastWindowAbove(double[] values, double threshold, int frames) {
        for (int i = values.length - frames; i >= 0; i--) {
            if (windowRms(values, i, i + frames) >= threshold) {
                return i + frames - 1;
            }
        }
        return values.length - 1;
    }

    private static double windowRms(double[] values, int start, int end) {
        double squareSum = 0;
        for (int i = start; i < end; i++) {
            squareSum += values[i] * values[i];
        }
        return Math.sqrt(squareSum / Math.max(1, end - start));
    }

    private static FadeEstimate estimateFade(double[] values, double activeLevel, int lastStrong, int lastSound, long rangeStartMillis) {
        int minimumFrames = ENVELOPE_RATE * 3;
        if (lastSound - lastStrong < minimumFrames) {
            return new FadeEstimate(frameMillis(rangeStartMillis, lastStrong), 0);
        }
        int searchStart = Math.max(0, lastSound - ENVELOPE_RATE * 18);
        double activeDb = 20 * Math.log10(Math.max(1.0e-7, activeLevel));
        int fadeStart = lastStrong;
        for (int i = searchStart; i <= lastStrong; i += ENVELOPE_RATE / 4) {
            double level = windowDb(values, i, Math.min(lastSound + 1, i + ENVELOPE_RATE));
            if (level <= activeDb - 2.5) {
                fadeStart = i;
                break;
            }
        }
        double bestConfidence = 0;
        int bestStart = fadeStart;
        for (int start = fadeStart; start <= lastStrong; start += ENVELOPE_RATE / 2) {
            int length = lastSound - start + 1;
            if (length < minimumFrames) {
                continue;
            }
            double first = windowDb(values, start, Math.min(lastSound + 1, start + ENVELOPE_RATE));
            double last = windowDb(values, Math.max(start, lastSound - ENVELOPE_RATE + 1), lastSound + 1);
            double drop = first - last;
            int descending = 0;
            int comparisons = 0;
            double previous = windowDb(values, start, Math.min(lastSound + 1, start + ENVELOPE_RATE));
            for (int i = start + ENVELOPE_RATE; i <= lastSound; i += ENVELOPE_RATE / 2) {
                double current = windowDb(values, i, Math.min(lastSound + 1, i + ENVELOPE_RATE));
                if (current <= previous + 1.2) {
                    descending++;
                }
                comparisons++;
                previous = current;
            }
            double monotonic = comparisons == 0 ? 0 : descending / (double) comparisons;
            double confidence = Math.min(1, Math.max(0, (drop - 8) / 18)) * 0.62 + monotonic * 0.38;
            if (confidence > bestConfidence + 0.04) {
                bestConfidence = confidence;
                bestStart = start;
            }
        }
        return new FadeEstimate(frameMillis(rangeStartMillis, bestStart), bestConfidence);
    }

    private static double windowDb(double[] values, int start, int end) {
        double squareSum = 0;
        for (int i = start; i < end; i++) {
            squareSum += values[i] * values[i];
        }
        return 20 * Math.log10(Math.max(1.0e-7, Math.sqrt(squareSum / Math.max(1, end - start))));
    }

    private static long frameMillis(long rangeStartMillis, int frame) {
        return rangeStartMillis + Math.round(frame * 1000.0 / ENVELOPE_RATE);
    }

    private static double activeLoudness(double[] values, double threshold) {
        double squareSum = 0;
        int count = 0;
        for (double value : values) {
            if (value >= threshold) {
                squareSum += value * value;
                count++;
            }
        }
        if (count == 0) {
            return -18;
        }
        return Math.max(-36, Math.min(-3, 20 * Math.log10(Math.sqrt(squareSum / count))));
    }

    private static double recentLoudness(double[] values) {
        int start = Math.max(0, values.length - ENVELOPE_RATE / 2);
        double squareSum = 0;
        for (int i = start; i < values.length; i++) {
            squareSum += values[i] * values[i];
        }
        double rms = Math.sqrt(squareSum / Math.max(1, values.length - start));
        return Math.max(-72, 20 * Math.log10(Math.max(1.0e-7, rms)));
    }

    private static long quietDuration(double[] values, double loudnessDb) {
        double threshold = Math.max(0.0015, Math.pow(10, (loudnessDb - 18) / 20));
        int frames = 0;
        for (int i = values.length - 1; i >= 0 && values[i] < threshold; i--) {
            frames++;
        }
        return Math.round(frames * 1000.0 / ENVELOPE_RATE);
    }

    private static long lastOnsetMillis(double[] onset, long firstFrame) {
        double threshold = Arrays.stream(onset).max().orElse(0) * 0.24;
        if (threshold <= 1.0e-6) {
            return 0;
        }
        for (int i = onset.length - 1; i >= 0; i--) {
            if (onset[i] >= threshold) {
                return Math.round((firstFrame + i) * 1000.0 / ENVELOPE_RATE);
            }
        }
        return 0;
    }

    private static double percentile(double[] values, double percentile) {
        double[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        return sorted[Math.max(0, Math.min(sorted.length - 1, (int) Math.round((sorted.length - 1) * percentile)))];
    }

    private static double readSample(byte[] data, int offset, int bytes, boolean bigEndian) {
        int value = 0;
        if (bigEndian) {
            for (int i = 0; i < bytes; i++) {
                value = (value << 8) | data[offset + i] & 0xff;
            }
        } else {
            for (int i = bytes - 1; i >= 0; i--) {
                value = (value << 8) | data[offset + i] & 0xff;
            }
        }
        int shift = 32 - bytes * 8;
        return (value << shift >> shift) / (double) (1L << (bytes * 8 - 1));
    }

    synchronized void accept(byte[] data, int offset, int length, AudioFormat format) {
        if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())) {
            return;
        }
        int channels = Math.max(1, format.getChannels());
        int frameSize = format.getFrameSize();
        int bytesPerSample = frameSize / channels;
        if (bytesPerSample < 1 || bytesPerSample > 4) {
            return;
        }
        sampleRate = format.getSampleRate();
        samplesPerEnvelopeFrame = Math.max(1, Math.round(sampleRate / ENVELOPE_RATE));
        double lowAlpha = 1 - Math.exp(-Math.PI * 2 * 180 / sampleRate);
        double upperAlpha = 1 - Math.exp(-Math.PI * 2 * 3_200 / sampleRate);
        int end = offset + length - frameSize + 1;
        for (int frameOffset = offset; frameOffset < end; frameOffset += frameSize) {
            double mono = 0;
            for (int channel = 0; channel < channels; channel++) {
                mono += readSample(data, frameOffset + channel * bytesPerSample, bytesPerSample, format.isBigEndian());
            }
            mono /= channels;
            lowPassState += lowAlpha * (mono - lowPassState);
            upperPassState += upperAlpha * (mono - upperPassState);
            double high = mono - upperPassState;
            frameSquareSum += mono * mono;
            frameLowSquareSum += lowPassState * lowPassState;
            frameHighSquareSum += high * high;
            frameSampleCount++;
            if (frameSampleCount >= samplesPerEnvelopeFrame) {
                envelope[envelopeWriteOffset] = Math.sqrt(frameSquareSum / frameSampleCount);
                lowEnvelope[envelopeWriteOffset] = Math.sqrt(frameLowSquareSum / frameSampleCount);
                highEnvelope[envelopeWriteOffset] = Math.sqrt(frameHighSquareSum / frameSampleCount);
                envelopeWriteOffset = (envelopeWriteOffset + 1) % envelope.length;
                envelopeSize = Math.min(envelope.length, envelopeSize + 1);
                totalEnvelopeFrames++;
                frameSquareSum = 0;
                frameLowSquareSum = 0;
                frameHighSquareSum = 0;
                frameSampleCount = 0;
            }
        }
    }

    synchronized AutoMixProfile snapshot() {
        if (envelopeSize < ENVELOPE_RATE * 4) {
            return AutoMixProfile.fallback();
        }
        double[] values = orderedEnvelope();
        long firstFrame = totalEnvelopeFrames - envelopeSize;
        double peak = Arrays.stream(values).max().orElse(0);
        double noise = percentile(values, 0.15);
        double audibleThreshold = Math.max(0.0015, Math.max(noise * 3.5, peak * 0.028));
        int firstAudible = firstSustained(values, audibleThreshold, 3);
        double loudness = activeLoudness(values, Math.max(audibleThreshold, peak * 0.08));
        double recentLoudness = recentLoudness(values);
        long quietDuration = quietDuration(values, loudness);

        double[] onset = onsetEnvelope(values, orderedEnvelope(lowEnvelope), orderedEnvelope(highEnvelope));
        BeatEstimate beat = estimateBeat(onset);
        long cueMillis = firstFrame == 0 && timelineOffsetMillis == 0 ? Math.min(12_000, Math.round(firstAudible * 1000.0 / ENVELOPE_RATE)) : 0;
        long analyzedMillis = timelineOffsetMillis + Math.round(totalEnvelopeFrames * 1000.0 / ENVELOPE_RATE);
        long lastOnsetMillis = timelineOffsetMillis + lastOnsetMillis(onset, firstFrame);
        if (beat.confidence >= 0.16) {
            double intervalMillis = beat.lag * 1000.0 / ENVELOPE_RATE;
            double phaseMillis = timelineOffsetMillis + (firstFrame + beat.phase) * 1000.0 / ENVELOPE_RATE;
            double aligned = phaseMillis + Math.ceil((cueMillis - phaseMillis) / intervalMillis) * intervalMillis;
            if (aligned >= 0 && aligned <= cueMillis + intervalMillis * 1.5) {
                cueMillis = Math.min(12_000, Math.round(aligned));
            }
            return new AutoMixProfile(cueMillis, intervalMillis, phaseMillis, beat.confidence, loudness, recentLoudness, quietDuration, analyzedMillis, lastOnsetMillis, false, 0, 0);
        }
        return new AutoMixProfile(cueMillis, 0, 0, beat.confidence, loudness, recentLoudness, quietDuration, analyzedMillis, lastOnsetMillis, false, 0, 0);
    }

    synchronized AutoMixTrackAnalysis trackAnalysis(long declaredDurationMillis) {
        AutoMixProfile profile = snapshot();
        if (envelopeSize < ENVELOPE_RATE * 4) {
            return AutoMixTrackAnalysis.fallback(declaredDurationMillis);
        }
        double[] values = orderedEnvelope();
        long firstFrame = totalEnvelopeFrames - envelopeSize;
        long rangeStartMillis = timelineOffsetMillis + Math.round(firstFrame * 1000.0 / ENVELOPE_RATE);
        double peak = Arrays.stream(values).max().orElse(0);
        double noise = percentile(values, 0.12);
        double noiseFloor = noise < peak * 0.08 ? noise * 3.2 : 0;
        double soundThreshold = Math.max(0.0012, Math.max(noiseFloor, peak * 0.018));
        double active = percentile(values, 0.82);
        double strongThreshold = Math.max(soundThreshold * 2.2, active * 0.42);
        int firstSound = firstWindowAbove(values, soundThreshold, 5);
        int firstStrong = firstWindowAbove(values, strongThreshold, 10);
        int lastSound = lastWindowAbove(values, soundThreshold, 5);
        int lastStrong = lastWindowAbove(values, strongThreshold, 10);
        long firstSoundMillis = frameMillis(rangeStartMillis, firstSound);
        long firstStrongMillis = frameMillis(rangeStartMillis, Math.max(firstSound, firstStrong));
        long lastSoundMillis = frameMillis(rangeStartMillis, lastSound);
        long lastStrongMillis = frameMillis(rangeStartMillis, Math.max(firstSound, lastStrong));
        long trailingSilence = Math.max(0, declaredDurationMillis - lastSoundMillis);
        FadeEstimate fade = estimateFade(values, active, lastStrong, lastSound, rangeStartMillis);
        AutoMixTrackAnalysis.EndingType endingType;
        double confidence;
        if (fade.confidence >= 0.58 && lastSoundMillis - fade.startMillis >= 2_800) {
            endingType = AutoMixTrackAnalysis.EndingType.NATURAL_FADE;
            confidence = fade.confidence;
        } else if (trailingSilence >= 650) {
            endingType = AutoMixTrackAnalysis.EndingType.TRAILING_SILENCE;
            confidence = Math.min(1, trailingSilence / 3_000.0);
        } else {
            endingType = AutoMixTrackAnalysis.EndingType.HARD;
            confidence = Math.max(0.35, 1 - fade.confidence);
        }
        long fadeStart = endingType == AutoMixTrackAnalysis.EndingType.NATURAL_FADE ? fade.startMillis : lastStrongMillis;
        return new AutoMixTrackAnalysis(profile, firstSoundMillis, firstStrongMillis, fadeStart, lastStrongMillis, lastSoundMillis, endingType, confidence);
    }

    private double[] orderedEnvelope() {
        return orderedEnvelope(envelope);
    }

    private double[] orderedEnvelope(double[] source) {
        double[] values = new double[envelopeSize];
        int start = envelopeSize == envelope.length ? envelopeWriteOffset : 0;
        for (int i = 0; i < envelopeSize; i++) {
            values[i] = source[(start + i) % source.length];
        }
        return values;
    }

    private record BeatEstimate(double lag, int phase, double confidence) {
    }

    private record PhaseEstimate(int phase, double strength) {
    }

    private record FadeEstimate(long startMillis, double confidence) {
    }
}
