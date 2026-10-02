/*
 * Copyright 2009 Phil Burk, Mobileer Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tritium.music.core.audio;

public final class Fft {

    public static final int SIZE = 4096;

    private static final int MAX_BITS = 16;
    private static final int[][] REVERSE_TABLES = new int[MAX_BITS + 1][];
    private static final float[][] SINE_TABLES = new float[MAX_BITS + 1][];

    private Fft() {
    }

    public static float[] analyzeSample(float[] samples, int bands) {
        if (bands <= 0 || Integer.bitCount(bands) != 1 || bands > samples.length) {
            throw new IllegalArgumentException("Invalid FFT band count: " + bands);
        }
        float[] imaginary = new float[samples.length];
        float[] magnitudes = new float[bands];
        transform(1, bands, samples, imaginary);
        for (int i = 0; i < bands; i++) {
            magnitudes[i] = (float) Math.sqrt(samples[i] * samples[i] + imaginary[i] * imaginary[i]);
        }
        return magnitudes;
    }

    public static void transform(int sign, int n, float[] real, float[] imaginary) {
        float scale = sign > 0 ? 2.0f / n : 0.5f;

        int bits = numBits(n);
        int[] reverse = reverseTable(bits);
        float[] sine = sineTable(bits);
        int mask = n - 1;
        int cosineOffset = n / 4;

        for (int i = 0; i < n; i++) {
            int j = reverse[i];
            if (j >= i) {
                float tempReal = real[j] * scale;
                float tempImaginary = imaginary[j] * scale;
                real[j] = real[i] * scale;
                imaginary[j] = imaginary[i] * scale;
                real[i] = tempReal;
                imaginary[i] = tempImaginary;
            }
        }

        int numerator = sign * n;
        for (int mmax = 1, stride = 2; mmax < n; mmax = stride, stride = 2 * mmax) {
            int phase = 0;
            int phaseIncrement = numerator / (2 * mmax);
            for (int m = 0; m < mmax; m++) {
                float wr = sine[(phase + cosineOffset) & mask];
                float wi = sine[phase];
                for (int i = m; i < n; i += stride) {
                    int j = i + mmax;
                    float tr = wr * real[j] - wi * imaginary[j];
                    float ti = wr * imaginary[j] + wi * real[j];
                    real[j] = real[i] - tr;
                    imaginary[j] = imaginary[i] - ti;
                    real[i] += tr;
                    imaginary[i] += ti;
                }
                phase = (phase + phaseIncrement) & mask;
            }
        }
    }

    private static int numBits(int powerOfTwo) {
        int bits = -1;
        while (powerOfTwo > 0) {
            powerOfTwo >>= 1;
            bits++;
        }
        return bits;
    }

    private static int[] reverseTable(int bits) {
        int[] table = REVERSE_TABLES[bits];
        if (table == null) {
            int length = 1 << bits;
            table = new int[length];
            for (int i = 0; i < length; i++) {
                table[i] = reverseBits(i, bits);
            }
            REVERSE_TABLES[bits] = table;
        }
        return table;
    }

    private static float[] sineTable(int bits) {
        float[] table = SINE_TABLES[bits];
        if (table == null) {
            int length = 1 << bits;
            table = new float[length];
            for (int i = 0; i < length; i++) {
                table[i] = (float) Math.sin(i * Math.PI * 2.0 / length);
            }
            SINE_TABLES[bits] = table;
        }
        return table;
    }

    private static int reverseBits(int index, int bits) {
        int reversed = 0;
        for (int i = 0; i < bits; i++) {
            reversed = (reversed << 1) | (index & 1);
            index >>= 1;
        }
        return reversed;
    }
}
