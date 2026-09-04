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
    void acceptsWiderAndHalfTimeTempoMatches() {
        AutoMixProfile bpm120 = profileAtBpm(120);
        AutoMixProfile bpm108 = profileAtBpm(108);
        AutoMixProfile bpm60 = profileAtBpm(60);
        AutoMixProfile bpm90 = profileAtBpm(90);
        AutoMixProfile bpm85Point7 = profileAtBpm(85.7);
        AutoMixProfile bpm96Point8 = profileAtBpm(96.8);

        assertTrue(bpm120.isTempoCompatible(bpm108, 0.12));
        assertEquals(0.9, bpm120.beatMatchRateTo(bpm108, 0.12), 0.001);
        assertTrue(bpm120.isTempoCompatible(bpm60, 0.12));
        assertEquals(1, bpm120.beatMatchRateTo(bpm60, 0.12), 0.001);
        assertEquals(1, bpm120.beatMatchRateTo(bpm90, 0.12), 0.001);
        assertEquals(96.8 / 85.7, bpm85Point7.beatMatchRateTo(bpm96Point8, 0.14), 0.001);
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
    void allowsHighConfidenceCruelSummerToShapeOfYouTempoSync() {
        AutoMixProfile currentProfile = transitionProfile(85.7, 0.97, -13, 177_200);
        AutoMixProfile nextProfile = transitionProfile(96.8, 0.96, -14, 31_000);
        AutoMixTrackAnalysis current = new AutoMixTrackAnalysis(currentProfile, 0, 300,
                172_000, 177_000, 178_000, AutoMixTrackAnalysis.EndingType.TRAILING_SILENCE, 0.8);
        AutoMixTrackAnalysis next = new AutoMixTrackAnalysis(nextProfile, 0, 450,
                170_000, 177_000, 180_000, AutoMixTrackAnalysis.EndingType.HARD, 0.8);
        double rate = currentProfile.beatMatchRateTo(nextProfile, AutoMixTempoPolicy.MAX_TEMPO_MATCH_CHANGE);

        assertEquals(96.8 / 85.7, rate, 0.001);
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
    void selectsCompatibleDownbeatsAndRejectsAMelodicCollision() {
        AutoMixProfile profile = transitionProfile(120, 0.96, -12, 29_000);
        List<Long> outgoingDownbeats = List.of(14_000L, 18_000L, 22_000L, 26_000L);
        List<Long> incomingDownbeats = List.of(0L, 2_000L, 4_000L, 6_000L);
        double[] harmony = {1, 0.4, 0.2, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        MusicalTimeline compatibleOutgoing = timeline(12_000, 18_000, outgoingDownbeats,
                harmony, 0.08, -0.2, 0.82);
        MusicalTimeline compatibleIncoming = timeline(0, 8_000, incomingDownbeats,
                harmony, 0.1, 0.35, 0.78);
        AutoMixTrackAnalysis current = analysis(profile, 30_000, compatibleOutgoing);
        AutoMixTrackAnalysis next = analysis(profile, 180_000, compatibleIncoming);

        TransitionCandidateSearch.Candidate candidate = TransitionCandidateSearch.find(current, next);

        assertNotNull(candidate);
        assertTrue(outgoingDownbeats.contains(candidate.outgoingMillis()));
        assertTrue(incomingDownbeats.contains(candidate.incomingMillis()));

        double[] conflictingHarmony = {0, 0, 0, 0, 0, 0, 1, 0.5, 0, 0, 0, 0};
        MusicalTimeline conflictingIncoming = timeline(0, 8_000, incomingDownbeats,
                conflictingHarmony, 1, -0.8, 0);
        assertNull(TransitionCandidateSearch.find(current,
                analysis(profile, 180_000, conflictingIncoming)));
    }

    @Test
    void alignsAccentPatternsWhenTheReportedDownbeatIsOneBeatWrong() {
        AutoMixProfile profile = transitionProfile(120, 0.97, -12, 29_000);
        List<Long> outgoingBeats = java.util.stream.LongStream.range(0, 32)
                .map(index -> 14_000 + index * 500).boxed().toList();
        List<Long> incomingBeats = java.util.stream.LongStream.range(0, 20)
                .map(index -> index * 500).boxed().toList();
        double[] chroma = {1, 0, 0.4, 0, 0.7, 0, 0, 0.5, 0, 0, 0, 0};
        MusicalTimeline outgoing = accentedTimeline(12_000, 18_000, outgoingBeats,
                List.of(14_000L, 16_000L, 18_000L, 20_000L, 22_000L, 24_000L, 26_000L, 28_000L),
                chroma, 0);
        MusicalTimeline incoming = accentedTimeline(0, 10_000, incomingBeats,
                List.of(500L, 2_500L, 4_500L, 6_500L, 8_500L), chroma, 0);

        TransitionCandidateSearch.Candidate candidate = TransitionCandidateSearch.find(
                analysis(profile, 30_000, outgoing), analysis(profile, 180_000, incoming));

        assertNotNull(candidate);
        assertTrue(outgoing.beatAccentAt(candidate.outgoingMillis()) > 0.9);
        assertTrue(incoming.beatAccentAt(candidate.incomingMillis()) > 0.9);
        assertTrue(candidate.meterScore() > 0.6, candidate.toString());
    }

    @Test
    void plansTheSmallestConfidentKeyChange() {
        AutoMixProfile profile = transitionProfile(120, 0.97, -12, 29_000);
        List<Long> outgoingBeats = java.util.stream.LongStream.range(0, 32)
                .map(index -> 14_000 + index * 500).boxed().toList();
        List<Long> incomingBeats = java.util.stream.LongStream.range(0, 20)
                .map(index -> index * 500).boxed().toList();
        double[] sourceChroma = {1, 0, 0.55, 0, 0.8, 0, 0, 0.45, 0, 0, 0, 0};
        double[] targetChroma = rotateChroma(sourceChroma, 2);
        MusicalTimeline outgoing = accentedTimeline(12_000, 18_000, outgoingBeats,
                List.of(14_000L, 16_000L, 18_000L, 20_000L, 22_000L, 24_000L, 26_000L, 28_000L),
                sourceChroma, 0);
        MusicalTimeline incoming = accentedTimeline(0, 10_000, incomingBeats,
                List.of(0L, 2_000L, 4_000L, 6_000L, 8_000L), targetChroma, 0);

        TransitionCandidateSearch.Candidate candidate = TransitionCandidateSearch.find(
                analysis(profile, 30_000, outgoing), analysis(profile, 180_000, incoming));

        assertNotNull(candidate);
        assertEquals(2, candidate.pitchShiftSemitones());
    }

    @Test
    void classifiesDenseMelodicMusicMoreConservativelyThanClubMusic() {
        AutoMixProfile profile = transitionProfile(120, 0.97, -12, 29_000);
        MusicalTimeline club = styledTimeline(12_000, 18_000, 0.06, 0.76, 0.08, 0);
        MusicalTimeline melodic = styledTimeline(12_000, 18_000, 0.72, 0.88, 0.24, 0.08);

        assertEquals(AutoMixStyleProfile.Style.CLUB,
                AutoMixStyleProfile.classify(analysis(profile, 30_000, club)));
        assertEquals(AutoMixStyleProfile.Style.VOCAL_MELODIC,
                AutoMixStyleProfile.classify(analysis(profile, 30_000, melodic)));
    }

    @Test
    void protectsADevelopingMelodicOutroFromAnEarlyBlend() {
        AutoMixProfile profile = transitionProfile(120, 0.97, -12, 29_000);
        MusicalTimeline outgoing = styledTimeline(12_000, 18_000, 0.82, 0.92, 0.88, 0.78);
        MusicalTimeline incoming = styledTimeline(0, 10_000, 0.08, 0.82, 0.10, 0);
        AutoMixTrackAnalysis current = analysis(profile, 30_000, outgoing);
        AutoMixTrackAnalysis next = analysis(profile, 180_000, incoming);
        AutoMixStyleProfile guidance = AutoMixStyleProfile.forPair(current, next);

        TransitionCandidateSearch.Candidate candidate = TransitionCandidateSearch.find(current, next, guidance);

        assertEquals(AutoMixStyleProfile.Intent.CONTENT_HANDOFF, guidance.intent());
        assertTrue(guidance.maxOverlapMillis() <= 3_200);
        assertTrue(candidate == null || candidate.outgoingMillis() >= 26_800, String.valueOf(candidate));
    }

    @Test
    void doesNotSkipAHighSalienceOpeningToFindAnEasierBeat() {
        AutoMixProfile profile = transitionProfile(120, 0.97, -12, 29_000);
        MusicalTimeline outgoing = styledTimeline(12_000, 18_000, 0.08, 0.82, 0.10, 0);
        MusicalTimeline incoming = styledTimeline(0, 10_000, 0.86, 0.94, 0.92, 0.52);
        AutoMixTrackAnalysis current = analysis(profile, 30_000, outgoing);
        AutoMixTrackAnalysis next = analysis(profile, 180_000, incoming);
        AutoMixStyleProfile guidance = AutoMixStyleProfile.forPair(current, next);

        TransitionCandidateSearch.Candidate candidate = TransitionCandidateSearch.find(current, next, guidance);

        assertTrue(guidance.maxIntroSkipMillis() <= 900);
        assertTrue(candidate == null || candidate.incomingMillis() <= 900, String.valueOf(candidate));
    }

    @Test
    void keepsLongBeatMixesAvailableForSparseRhythmicTracks() {
        AutoMixProfile profile = transitionProfile(120, 0.98, -12, 29_000);
        AutoMixTrackAnalysis current = analysis(profile, 30_000,
                styledTimeline(12_000, 18_000, 0.05, 0.82, 0.08, -0.04));
        AutoMixTrackAnalysis next = analysis(profile, 180_000,
                styledTimeline(0, 14_000, 0.07, 0.84, 0.10, 0.05));

        AutoMixStyleProfile guidance = AutoMixStyleProfile.forPair(current, next);

        assertEquals(AutoMixStyleProfile.Intent.BEAT_MIX, guidance.intent());
        assertEquals(18_000, guidance.maxOverlapMillis());
        assertEquals(12_000, guidance.maxIntroSkipMillis());
        assertTrue(guidance.allowTempoAndPitch());
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
                byte[] output = processor.process(input, offset, Math.min(chunk, input.length - offset), 1, 2);
                transformed.writeBytes(output);
            }
            transformed.writeBytes(processor.flush());
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

    private static MusicalTimeline timeline(
            long start,
            long duration,
            List<Long> downbeats,
            double[] chroma,
            double density,
            double trend,
            double novelty) {
        List<MusicalTimeline.Frame> frames = java.util.stream.LongStream
                .iterate(start, time -> time < start + duration, time -> time + 500)
                .mapToObj(time -> new MusicalTimeline.Frame(time, -12, trend, 48,
                        density, 0.8, novelty, chroma))
                .toList();
        return new MusicalTimeline(start, duration, frames, downbeats, downbeats);
    }

    private static MusicalTimeline accentedTimeline(
            long start,
            long duration,
            List<Long> beats,
            List<Long> downbeats,
            double[] chroma,
            int accentOffset) {
        List<MusicalTimeline.Frame> frames = java.util.stream.LongStream
                .iterate(start, time -> time < start + duration, time -> time + 500)
                .mapToObj(time -> new MusicalTimeline.Frame(time, -12, 0, 48,
                        0.08, 0.9, 0.8, chroma))
                .toList();
        List<MusicalTimeline.BeatAccent> accents = java.util.stream.IntStream.range(0, beats.size())
                .mapToObj(index -> new MusicalTimeline.BeatAccent(beats.get(index),
                        Math.floorMod(index - accentOffset, 4) == 0 ? 1 : 0.18))
                .toList();
        return new MusicalTimeline(start, duration, frames, beats, downbeats, accents);
    }

    private static MusicalTimeline styledTimeline(
            long start,
            long duration,
            double density,
            double clarity,
            double novelty,
            double trend) {
        List<Long> beats = java.util.stream.LongStream
                .iterate(start, time -> time < start + duration, time -> time + 500)
                .boxed().toList();
        List<Long> downbeats = java.util.stream.LongStream
                .iterate(start, time -> time < start + duration, time -> time + 2_000)
                .boxed().toList();
        double[] chroma = {1, 0, 0.42, 0, 0.68, 0, 0, 0.48, 0, 0, 0, 0};
        List<MusicalTimeline.Frame> frames = beats.stream()
                .map(time -> new MusicalTimeline.Frame(time, -12, trend, 52,
                        density, clarity, novelty, chroma))
                .toList();
        List<MusicalTimeline.BeatAccent> accents = java.util.stream.IntStream.range(0, beats.size())
                .mapToObj(index -> new MusicalTimeline.BeatAccent(beats.get(index),
                        index % 4 == 0 ? 1 : 0.16))
                .toList();
        return new MusicalTimeline(start, duration, frames, beats, downbeats, accents);
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
