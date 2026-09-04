package tritium.music.core.audio;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoMixAnalyzerTest {
    private static final int SAMPLE_RATE = 44_100;

    @Test
    void detectsBeatGridAndSkipsLeadingSilence() {
        AutoMixAnalyzer analyzer = new AutoMixAnalyzer();
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
        byte[] audio = pulseTrack(22, 1, 0.5);

        analyzer.accept(audio, 0, audio.length, format);
        AutoMixProfile profile = analyzer.snapshot();

        assertTrue(profile.hasReliableBeat(), profile.toString());
        assertEquals(500, profile.beatIntervalMillis(), 45);
        assertTrue(profile.cueInMillis() >= 850 && profile.cueInMillis() <= 1_550, profile.toString());
        assertTrue(profile.loudnessDb() > -30 && profile.loudnessDb() < -3);
    }

    @Test
    void fallsBackForInsufficientAudio() {
        AutoMixAnalyzer analyzer = new AutoMixAnalyzer();
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
        byte[] audio = pulseTrack(2, 0, 0.5);

        analyzer.accept(audio, 0, audio.length, format);

        assertEquals(AutoMixProfile.fallback(), analyzer.snapshot());
    }

    @Test
    void beatThisRejectsEmptyAudioBeforeInitializingTheModel() throws Exception {
        BeatThisTempoAnalyzer analyzer = new BeatThisTempoAnalyzer();

        assertNull(analyzer.analyzeDetailed(0));
    }

    @Test
    void autoMixAlwaysHasAUsableFallbackWindow() {
        AutoMixTransitionTiming.Window early = AutoMixTransitionTiming.fallback(180_000, 30_000);
        AutoMixTransitionTiming.Window late = AutoMixTransitionTiming.fallback(180_000, 178_500);

        assertEquals(174_000, early.startMillis());
        assertEquals(6_000, early.durationMillis());
        assertEquals(178_500, late.startMillis());
        assertEquals(1_500, late.durationMillis());
    }

    @Test
    void autoMixShrinksMissedWindowsInsteadOfStartingAStaleLongBlend() {
        AutoMixTransitionTiming.Window fitted = AutoMixTransitionTiming.fit(
                new AutoMixTransitionTiming.Window(168_000, 12_000), 176_000, 180_000);

        assertEquals(176_000, fitted.startMillis());
        assertEquals(4_000, fitted.durationMillis());
    }

    @Test
    void tracksTheLatestWindowAndDetectsAQuietEnding() {
        AutoMixAnalyzer analyzer = new AutoMixAnalyzer();
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
        byte[] active = pulseTrack(60, 0, 0.5);
        byte[] silence = new byte[SAMPLE_RATE * 4 * 10];

        analyzer.accept(active, 0, active.length, format);
        analyzer.accept(silence, 0, silence.length, format);
        AutoMixProfile profile = analyzer.snapshot();

        assertTrue(profile.hasReliableBeat(), profile.toString());
        assertTrue(profile.analyzedMillis() >= 69_900, profile.toString());
        assertTrue(profile.quietDurationMillis() >= 9_800, profile.toString());
        assertTrue(profile.recentLoudnessDb() <= -60, profile.toString());
        assertTrue(profile.lastOnsetMillis() >= 58_000 && profile.lastOnsetMillis() <= 60_000, profile.toString());
    }

    @Test
    void preservesThePlaybackTimelineAfterAnIntroCue() {
        AutoMixAnalyzer analyzer = new AutoMixAnalyzer(30_000);
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
        byte[] audio = pulseTrack(10, 0, 0.5);

        analyzer.accept(audio, 0, audio.length, format);
        AutoMixProfile profile = analyzer.snapshot();

        assertTrue(profile.hasReliableBeat(), profile.toString());
        assertTrue(profile.beatPhaseMillis() >= 30_000, profile.toString());
        assertTrue(profile.analyzedMillis() >= 39_900, profile.toString());
        assertTrue(profile.lastOnsetMillis() >= 39_000, profile.toString());
    }

    @Test
    void transitionLowPassRemovesHighFrequencyEnergy() {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
        byte[] audio = sineTrack(1, 8_000);
        double before = rms(audio, audio.length / 2);
        AudioPlayer player = new AudioPlayer(new File("unused.wav"), 1_000);

        player.setTransitionEq(1, 1, 1, 1_000);
        player.applyTransitionFilter(audio, 0, audio.length, format);
        double after = rms(audio, audio.length / 2);
        player.close();

        assertTrue(after < before * 0.25, "before=" + before + ", after=" + after);
    }

    @Test
    void seekingInvalidatesTheActiveTransitionState() {
        AudioPlayer player = new AudioPlayer(new File("unused.wav"), 30_000);
        long revision = player.getSeekRevision();
        player.setMixGain(0.25f);
        player.setTransitionEq(0.1f, 0.4f, 0.7f, 1_200);
        player.setPlaybackRate(1.06);
        player.setPitchShiftSemitones(2);

        player.setPlaybackTime(12_345);

        assertEquals(revision + 1, player.getSeekRevision());
        assertEquals(12_345, player.getCurrentTimeMillis(), 1);
        player.close();
    }

    @Test
    void classifiesARecordedFadeWithoutDoubleFadingIt() {
        AutoMixAnalyzer analyzer = new AutoMixAnalyzer();
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
        byte[] audio = shapedSineTrack(30, 20, 29.5);

        analyzer.accept(audio, 0, audio.length, format);
        AutoMixTrackAnalysis analysis = analyzer.trackAnalysis(30_000);

        assertEquals(AutoMixTrackAnalysis.EndingType.NATURAL_FADE, analysis.endingType(), analysis.toString());
        assertTrue(analysis.fadeOutStartMillis() >= 18_000 && analysis.fadeOutStartMillis() <= 24_000,
                analysis.toString());
        assertTrue(analysis.lastSoundMillis() >= 28_000, analysis.toString());
    }

    @Test
    void identifiesAndSkipsTrailingSilence() {
        AutoMixAnalyzer analyzer = new AutoMixAnalyzer();
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
        byte[] audio = shapedSineTrack(30, 30, 23);

        analyzer.accept(audio, 0, audio.length, format);
        AutoMixTrackAnalysis analysis = analyzer.trackAnalysis(30_000);

        assertEquals(AutoMixTrackAnalysis.EndingType.TRAILING_SILENCE, analysis.endingType(), analysis.toString());
        assertTrue(analysis.lastSoundMillis() >= 22_000 && analysis.lastSoundMillis() <= 24_000,
                analysis.toString());
    }

    @Test
    void keepsAnAbruptEndingShort() {
        AutoMixAnalyzer analyzer = new AutoMixAnalyzer();
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
        byte[] audio = shapedSineTrack(30, 30, 30);

        analyzer.accept(audio, 0, audio.length, format);
        AutoMixTrackAnalysis analysis = analyzer.trackAnalysis(30_000);

        assertEquals(AutoMixTrackAnalysis.EndingType.HARD, analysis.endingType(), analysis.toString());
        assertTrue(analysis.lastSoundMillis() >= 29_500, analysis.toString());
    }

    @Test
    void resamplesPcmForBoundedBeatMatching() {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
        byte[] audio = sineTrack(1, 220);

        byte[] faster = StreamingSoundPlayer.resampleForAutoMix(audio, format, 1.05);
        byte[] highConfidenceFaster = StreamingSoundPlayer.resampleForAutoMix(audio, format, 1.13);
        byte[] slower = StreamingSoundPlayer.resampleForAutoMix(audio, format, 0.95);

        assertTrue(faster.length < audio.length * 0.96, "length=" + faster.length);
        assertTrue(highConfidenceFaster.length < audio.length * 0.89, "length=" + highConfidenceFaster.length);
        assertTrue(slower.length > audio.length * 1.04, "length=" + slower.length);
        assertTrue(rms(faster, 0) > 0.3);
        assertTrue(rms(slower, 0) > 0.3);
    }

    @Test
    void streamingFallbackResamplerKeepsChunkBoundariesContinuous() {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
        byte[] input = sineTrack(2, 440);
        StreamingSoundPlayer.StreamingResampler resampler = new StreamingSoundPlayer.StreamingResampler(format);
        ByteArrayOutputStream transformed = new ByteArrayOutputStream();
        int chunk = SAMPLE_RATE / 100 * format.getFrameSize();
        for (int offset = 0; offset < input.length; offset += chunk) {
            StreamingSoundPlayer.PcmChunk output = resampler.process(input, offset,
                    Math.min(chunk, input.length - offset), 1.07);
            transformed.write(output.data(), output.offset(), output.length());
        }
        StreamingSoundPlayer.PcmChunk tail = resampler.flush();
        transformed.write(tail.data(), tail.offset(), tail.length());
        byte[] output = transformed.toByteArray();
        int maximumStep = 0;
        int maximumOffset = 0;
        int maximumPrevious = 0;
        int maximumSample = 0;
        int previous = (short) (output[0] & 0xff | output[1] << 8);
        for (int offset = format.getFrameSize(); offset + 1 < output.length; offset += format.getFrameSize()) {
            int sample = (short) (output[offset] & 0xff | output[offset + 1] << 8);
            int step = Math.abs(sample - previous);
            if (step > maximumStep) {
                maximumStep = step;
                maximumOffset = offset;
                maximumPrevious = previous;
                maximumSample = sample;
            }
            previous = sample;
        }
        assertTrue(maximumStep < 2_000, "maximumStep=" + maximumStep + ", offset=" + maximumOffset
                + ", previous=" + maximumPrevious + ", sample=" + maximumSample
                + ", outputLength=" + output.length);
    }

    @Test
    void acceptsOnlySmallOrEquivalentTempoMatches() {
        AutoMixProfile bpm120 = profileAtBpm(120);
        AutoMixProfile bpm117 = profileAtBpm(117);
        AutoMixProfile bpm60 = profileAtBpm(60);
        AutoMixProfile bpm90 = profileAtBpm(90);

        assertTrue(bpm120.isTempoCompatible(bpm117, AutoMixTempoPolicy.MAX_TEMPO_MATCH_CHANGE));
        assertEquals(117.0 / 120, bpm120.beatMatchRateTo(bpm117,
                AutoMixTempoPolicy.MAX_TEMPO_MATCH_CHANGE), 0.001);
        assertTrue(bpm120.isTempoCompatible(bpm60, AutoMixTempoPolicy.MAX_TEMPO_MATCH_CHANGE));
        assertEquals(1, bpm120.beatMatchRateTo(bpm60,
                AutoMixTempoPolicy.MAX_TEMPO_MATCH_CHANGE), 0.001);
        assertEquals(1, bpm120.beatMatchRateTo(bpm90,
                AutoMixTempoPolicy.MAX_TEMPO_MATCH_CHANGE), 0.001);
    }

    @Test
    void alignsTempoPreparationToDownbeats() {
        AutoMixProfile profile = new AutoMixProfile(0, 500, 100, 0.9, -14, -14, 0,
                30_000, 29_000, true, 2_000, 100);

        assertEquals(2_100, profile.alignToDownbeat(2_600));
        assertEquals(2_100, profile.downbeatAtOrBefore(3_900));
        assertEquals(2_600, profile.alignToBeat(2_600));
    }

    @Test
    void allowsHighConfidenceSmallTempoSync() {
        AutoMixProfile currentProfile = transitionProfile(120, 0.97, -13, 177_200);
        AutoMixProfile nextProfile = transitionProfile(117, 0.96, -14, 177_000);
        AutoMixTrackAnalysis current = new AutoMixTrackAnalysis(currentProfile, 0, 300,
                172_000, 177_000, 178_000, AutoMixTrackAnalysis.EndingType.TRAILING_SILENCE, 0.8);
        AutoMixTrackAnalysis next = new AutoMixTrackAnalysis(nextProfile, 0, 450,
                170_000, 177_000, 180_000, AutoMixTrackAnalysis.EndingType.HARD, 0.8);
        double rate = currentProfile.beatMatchRateTo(nextProfile, AutoMixTempoPolicy.MAX_TEMPO_MATCH_CHANGE);

        assertEquals(117.0 / 120, rate, 0.001);
        assertTrue(AutoMixTempoPolicy.shouldSync(current, next, rate));
    }

    @Test
    void detectsTempoAcrossTheMixingRange() {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
        AutoMixAnalyzer slow = new AutoMixAnalyzer();
        AutoMixAnalyzer fast = new AutoMixAnalyzer();

        byte[] slowAudio = pulseTrack(32, 0, 60.0 / 92);
        slow.accept(slowAudio, 0, slowAudio.length, format);
        byte[] fastAudio = pulseTrack(32, 0, 60.0 / 156);
        fast.accept(fastAudio, 0, fastAudio.length, format);

        assertEquals(92, 60_000 / slow.snapshot().beatIntervalMillis(), 4);
        assertEquals(156, 60_000 / fast.snapshot().beatIntervalMillis(), 5);
    }

    @Test
    void extractsAZeroTrainingMusicalTimeline() throws Exception {
        int sampleRate = 22_050;
        float[] audio = new float[sampleRate * 4];
        for (int i = 0; i < audio.length; i++) {
            audio[i] = (float) ((Math.sin(i * Math.PI * 2 * 220 / sampleRate)
                    + Math.sin(i * Math.PI * 2 * 329.63 / sampleRate)) * 0.32);
        }
        BeatThisTempoAnalyzer.BeatGrid grid = new BeatThisTempoAnalyzer.BeatGrid(
                500, 0, 0.95, 8, 2, 2_000, 0,
                List.of(0L, 500L, 1_000L, 1_500L, 2_000L, 2_500L, 3_000L, 3_500L),
                List.of(0L, 2_000L));

        MusicalTimeline timeline = new BasicPitchAnalyzer().analyze(audio, 0, grid);

        assertTrue(timeline.isUsable());
        assertTrue(timeline.frames().stream().anyMatch(frame -> frame.melodyDensity() > 0.01));
        assertEquals(List.of(0L, 2_000L), timeline.downbeats());
    }

    @Test
    void appliesOnlyAConfidentSmallKeyChange() {
        AutoMixProfile profile = transitionProfile(120, 0.97, -12, 29_000);
        double[] sourceChroma = {1, 0, 0.55, 0, 0.8, 0, 0, 0.45, 0, 0, 0, 0};
        double[] targetChroma = rotateChroma(sourceChroma, 2);
        MusicalTimeline outgoing = harmonicTimeline(12_000, 18_000, sourceChroma);
        MusicalTimeline incoming = harmonicTimeline(0, 10_000, targetChroma);

        AutoMixHarmonicMatch match = AutoMixHarmonicMatch.between(
                analysis(profile, 30_000, outgoing), analysis(profile, 180_000, incoming));

        assertEquals(2, match.pitchShiftSemitones());
        assertTrue(match.improvement() >= 0.1, match.toString());
    }

    @Test
    void derivesContentPreservingTransitionFromDetectedStructure() {
        AutoMixProfile profile = transitionProfile(120, 0.97, -12, 29_000);
        double[] chroma = {1, 0, 0.55, 0, 0.8, 0, 0, 0.45, 0, 0, 0, 0};
        MusicalTimeline outgoing = structuredTimeline(0, 30_000, 24_000, 30_000, chroma);
        MusicalTimeline incoming = structuredTimeline(0, 30_000, 2_000, 16_000, chroma);

        AutoMixTransitionSearch.Selection selection = AutoMixTransitionSearch.find(
                analysis(profile, 30_000, outgoing), analysis(profile, 30_000, incoming));

        assertNotNull(selection);
        assertTrue(outgoing.downbeats().contains(selection.outgoingMillis()), selection.toString());
        assertTrue(incoming.downbeats().contains(selection.incomingMillis()), selection.toString());
        assertEquals(30_000 - selection.outgoingMillis(), selection.trackOverlapMillis());
        assertTrue(selection.trackOverlapMillis() <= 11_000, selection.toString());
        assertTrue(selection.incomingMillis() <= 3_500, selection.toString());
    }

    @Test
    void soundTouchChangesPitchWithoutChangingTempo() {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
        byte[] input = chordTrack(3, 220, 277.18, 329.63);
        ByteArrayOutputStream transformed = new ByteArrayOutputStream();
        try (SoundTouchAudioProcessor processor = SoundTouchAudioProcessor.create(format)) {
            assertTrue(processor.isAvailable(), SoundTouchAudioProcessor.failureMessage());
            int chunk = SAMPLE_RATE / 10 * format.getFrameSize();
            for (int offset = 0; offset < input.length; offset += chunk) {
                byte[] output = processor.process(input, offset, Math.min(chunk, input.length - offset), 1, 2, 1);
                transformed.writeBytes(output);
            }
            transformed.writeBytes(processor.flush(1));
        }
        byte[] output = transformed.toByteArray();

        assertEquals(input.length, output.length, input.length * 0.08);
        double ratio = Math.pow(2, 2 / 12.0);
        double shiftedEnergy = spectralMagnitude(output, format, 220 * ratio)
                + spectralMagnitude(output, format, 277.18 * ratio)
                + spectralMagnitude(output, format, 329.63 * ratio);
        double originalEnergy = spectralMagnitude(output, format, 220)
                + spectralMagnitude(output, format, 277.18)
                + spectralMagnitude(output, format, 329.63);
        assertTrue(shiftedEnergy > originalEnergy * 1.8,
                "shifted=" + shiftedEnergy + ", original=" + originalEnergy);
    }

    @Test
    void soundTouchAppliesGainBeforePcmConversion() {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
        byte[] input = chordTrack(1, 220, 277.18, 329.63);
        ByteArrayOutputStream transformed = new ByteArrayOutputStream();
        try (SoundTouchAudioProcessor processor = SoundTouchAudioProcessor.create(format)) {
            assertTrue(processor.isAvailable(), SoundTouchAudioProcessor.failureMessage());
            int chunk = SAMPLE_RATE / 10 * format.getFrameSize();
            for (int offset = 0; offset < input.length; offset += chunk) {
                transformed.writeBytes(processor.process(input, offset,
                        Math.min(chunk, input.length - offset), 1, 2, 0.2f));
            }
            transformed.writeBytes(processor.flush(0.2f));
        }
        byte[] output = transformed.toByteArray();
        int peak = 0;
        for (int offset = 0; offset + 1 < output.length; offset += 2) {
            int sample = (short) (output[offset] & 0xff | output[offset + 1] << 8);
            peak = Math.max(peak, Math.abs(sample));
        }
        assertTrue(peak > 0);
        assertTrue(peak <= 6_554, "peak=" + peak);
    }

    @Test
    void rejectsQuietOffbeatsAsTheMainTempo() {
        AutoMixAnalyzer analyzer = new AutoMixAnalyzer();
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
        byte[] audio = accentedPulseTrack(36, 90);

        analyzer.accept(audio, 0, audio.length, format);

        assertEquals(90, 60_000 / analyzer.snapshot().beatIntervalMillis(), 4);
    }

    private static byte[] pulseTrack(int seconds, double silenceSeconds, double beatSeconds) {
        int frames = seconds * SAMPLE_RATE;
        byte[] result = new byte[frames * 4];
        for (int frame = 0; frame < frames; frame++) {
            double time = frame / (double) SAMPLE_RATE;
            double beatPosition = (time - silenceSeconds) % beatSeconds;
            double envelope = time >= silenceSeconds && beatPosition >= 0 && beatPosition < 0.07
                    ? Math.exp(-beatPosition * 45)
                    : 0;
            short sample = (short) Math.round(Math.sin(time * Math.PI * 2 * 90) * envelope * 22_000);
            int offset = frame * 4;
            result[offset] = (byte) sample;
            result[offset + 1] = (byte) (sample >>> 8);
            result[offset + 2] = (byte) sample;
            result[offset + 3] = (byte) (sample >>> 8);
        }
        return result;
    }

    private static byte[] sineTrack(int seconds, double frequency) {
        int frames = seconds * SAMPLE_RATE;
        byte[] result = new byte[frames * 4];
        for (int frame = 0; frame < frames; frame++) {
            short sample = (short) Math.round(Math.sin(frame * Math.PI * 2 * frequency / SAMPLE_RATE) * 22_000);
            int offset = frame * 4;
            result[offset] = (byte) sample;
            result[offset + 1] = (byte) (sample >>> 8);
            result[offset + 2] = (byte) sample;
            result[offset + 3] = (byte) (sample >>> 8);
        }
        return result;
    }

    private static byte[] shapedSineTrack(int seconds, double fadeStartSeconds, double soundEndSeconds) {
        int frames = seconds * SAMPLE_RATE;
        byte[] result = new byte[frames * 4];
        for (int frame = 0; frame < frames; frame++) {
            double time = frame / (double) SAMPLE_RATE;
            double gain = time >= soundEndSeconds ? 0 : 0.68;
            if (time > fadeStartSeconds && soundEndSeconds > fadeStartSeconds) {
                gain *= Math.max(0, (soundEndSeconds - time) / (soundEndSeconds - fadeStartSeconds));
            }
            short sample = (short) Math.round(Math.sin(time * Math.PI * 2 * 180) * gain * 32767);
            int offset = frame * 4;
            result[offset] = (byte) sample;
            result[offset + 1] = (byte) (sample >>> 8);
            result[offset + 2] = (byte) sample;
            result[offset + 3] = (byte) (sample >>> 8);
        }
        return result;
    }

    private static byte[] accentedPulseTrack(int seconds, double bpm) {
        int frames = seconds * SAMPLE_RATE;
        byte[] result = new byte[frames * 4];
        double beatSeconds = 60 / bpm;
        for (int frame = 0; frame < frames; frame++) {
            double time = frame / (double) SAMPLE_RATE;
            double position = time % beatSeconds;
            double distance = Math.min(position, Math.abs(position - beatSeconds * 0.5));
            double accent = position < beatSeconds * 0.15 ? 1 : 0.28;
            double envelope = distance < 0.055 ? Math.exp(-distance * 52) * accent : 0;
            short sample = (short) Math.round(Math.sin(time * Math.PI * 2 * 105) * envelope * 24_000);
            int offset = frame * 4;
            result[offset] = (byte) sample;
            result[offset + 1] = (byte) (sample >>> 8);
            result[offset + 2] = (byte) sample;
            result[offset + 3] = (byte) (sample >>> 8);
        }
        return result;
    }

    private static double rms(byte[] audio, int offset) {
        double squareSum = 0;
        int count = 0;
        for (int i = offset; i + 1 < audio.length; i += 2) {
            short sample = (short) ((audio[i] & 0xff) | audio[i + 1] << 8);
            double value = sample / 32768.0;
            squareSum += value * value;
            count++;
        }
        return Math.sqrt(squareSum / count);
    }

    private static AutoMixProfile profileAtBpm(double bpm) {
        return new AutoMixProfile(0, 60_000 / bpm, 0, 0.8, -14, -14, 0, 30_000, 29_000,
                true, 240_000 / bpm, 0);
    }

    private static AutoMixProfile transitionProfile(double bpm, double confidence, double loudness, long lastOnset) {
        return new AutoMixProfile(0, 60_000 / bpm, 0, confidence, loudness, loudness, 0,
                180_000, lastOnset, true, 240_000 / bpm, 0);
    }

    private static MusicalTimeline harmonicTimeline(long start, long duration, double[] chroma) {
        List<Long> beats = java.util.stream.LongStream
                .iterate(start, time -> time < start + duration, time -> time + 500)
                .boxed().toList();
        List<Long> downbeats = java.util.stream.LongStream
                .iterate(start, time -> time < start + duration, time -> time + 2_000)
                .boxed().toList();
        List<MusicalTimeline.Frame> frames = beats.stream()
                .map(time -> new MusicalTimeline.Frame(time, -12, 0, 52,
                        0.1, 0.9, 0.1, chroma))
                .toList();
        return new MusicalTimeline(start, duration, frames, beats, downbeats);
    }

    private static MusicalTimeline structuredTimeline(long start, long duration,
                                                       long firstBoundary, long secondBoundary,
                                                       double[] chroma) {
        List<Long> beats = java.util.stream.LongStream
                .iterate(start, time -> time < start + duration, time -> time + 500)
                .boxed().toList();
        List<Long> downbeats = java.util.stream.LongStream
                .iterate(start, time -> time < start + duration, time -> time + 2_000)
                .boxed().toList();
        List<MusicalTimeline.Frame> frames = beats.stream().map(time -> {
            boolean boundary = time == firstBoundary || time == secondBoundary;
            boolean beforeBoundary = time == firstBoundary - 500 || time == secondBoundary - 500;
            return new MusicalTimeline.Frame(time, -12,
                    boundary ? -0.7 : beforeBoundary ? 0.5 : 0,
                    52, boundary ? 0 : 0.72, 0.9,
                    boundary ? 1 : 0.04, chroma);
        }).toList();
        return new MusicalTimeline(start, duration, frames, beats, downbeats);
    }

    private static double[] rotateChroma(double[] source, int semitones) {
        double[] result = new double[source.length];
        for (int pitchClass = 0; pitchClass < source.length; pitchClass++) {
            result[Math.floorMod(pitchClass + semitones, source.length)] = source[pitchClass];
        }
        return result;
    }

    private static byte[] chordTrack(int seconds, double... frequencies) {
        int frames = seconds * SAMPLE_RATE;
        byte[] result = new byte[frames * 4];
        for (int frame = 0; frame < frames; frame++) {
            double time = frame / (double) SAMPLE_RATE;
            double envelope = 0.45 + 0.55 * Math.exp(-(time % 0.5) * 7);
            double value = 0;
            for (double frequency : frequencies) {
                value += Math.sin(time * Math.PI * 2 * frequency);
            }
            short sample = (short) Math.round(value / frequencies.length * envelope * 24_000);
            int offset = frame * 4;
            result[offset] = (byte) sample;
            result[offset + 1] = (byte) (sample >>> 8);
            result[offset + 2] = (byte) sample;
            result[offset + 3] = (byte) (sample >>> 8);
        }
        return result;
    }

    private static double spectralMagnitude(byte[] audio, AudioFormat format, double frequency) {
        int frameSize = format.getFrameSize();
        int frames = audio.length / frameSize;
        int start = frames / 3;
        int end = frames * 2 / 3;
        double real = 0;
        double imaginary = 0;
        for (int frame = start; frame < end; frame++) {
            int offset = frame * frameSize;
            short sample = (short) ((audio[offset] & 0xff) | audio[offset + 1] << 8);
            double angle = Math.PI * 2 * frequency * frame / format.getSampleRate();
            real += sample * Math.cos(angle);
            imaginary -= sample * Math.sin(angle);
        }
        return Math.hypot(real, imaginary) / (end - start);
    }

    private static AutoMixTrackAnalysis analysis(
            AutoMixProfile profile,
            long duration,
            MusicalTimeline timeline) {
        return new AutoMixTrackAnalysis(profile, 0, 300, duration - 8_000, duration - 1_000,
                duration, AutoMixTrackAnalysis.EndingType.HARD, 0.9, timeline);
    }
}
