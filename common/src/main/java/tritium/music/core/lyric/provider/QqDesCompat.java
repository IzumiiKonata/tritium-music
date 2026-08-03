package tritium.music.core.lyric.provider;

final class QqDesCompat {
    private static final int[] SHIFTS = {1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1};
    private static final int[][] SBOXES = {
            {14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7, 0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8, 4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0, 15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13},
            {15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10, 3, 13, 4, 7, 15, 2, 8, 15, 12, 0, 1, 10, 6, 9, 11, 5, 0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15, 13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9},
            {10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8, 13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1, 13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7, 1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12},
            {7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15, 13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9, 10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4, 3, 15, 0, 6, 10, 10, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14},
            {2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9, 14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6, 4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14, 11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3},
            {12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11, 10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8, 9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6, 4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13},
            {4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1, 13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6, 1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2, 6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12},
            {13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7, 1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2, 7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8, 2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11}
    };

    private QqDesCompat() {
    }

    static byte[] decrypt(byte[] input, byte[] key) {
        if (key.length != 24 || input.length % 8 != 0) throw new IllegalArgumentException();
        byte[][][] schedules = new byte[3][16][6];
        schedule(key, 16, schedules[0], true);
        schedule(key, 8, schedules[1], false);
        schedule(key, 0, schedules[2], true);
        byte[] output = new byte[input.length];
        for (int offset = 0; offset < input.length; offset += 8) {
            byte[] block = new byte[8];
            System.arraycopy(input, offset, block, 0, 8);
            for (byte[][] schedule : schedules) block = crypt(block, schedule);
            System.arraycopy(block, 0, output, offset, 8);
        }
        return output;
    }

    private static void schedule(byte[] key, int offset, byte[][] result, boolean decrypt) {
        int[] keyPermC = {56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17, 9, 1, 58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35};
        int[] keyPermD = {62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37, 29, 21, 13, 5, 60, 52, 44, 36, 28, 20, 12, 4, 27, 19, 11, 3};
        int[] compression = {13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9, 22, 18, 11, 3, 25, 7, 15, 6, 26, 19, 12, 1, 40, 51, 30, 36, 46, 54, 29, 39, 50, 44, 32, 47, 43, 48, 38, 55, 33, 52, 45, 41, 49, 35, 28, 31};
        int c = 0;
        int d = 0;
        for (int i = 0; i < 28; i++) c |= bitNumber(key, offset, keyPermC[i], 31 - i);
        for (int i = 0; i < 28; i++) d |= bitNumber(key, offset, keyPermD[i], 31 - i);
        for (int i = 0; i < 16; i++) {
            c = ((c << SHIFTS[i]) | (c >>> (28 - SHIFTS[i]))) & 0xfffffff0;
            d = ((d << SHIFTS[i]) | (d >>> (28 - SHIFTS[i]))) & 0xfffffff0;
            int target = decrypt ? 15 - i : i;
            for (int j = 0; j < 24; j++) result[target][j / 8] |= bitNumberIntRight(c, compression[j], 7 - j % 8);
            for (int j = 24; j < 48; j++) result[target][j / 8] |= bitNumberIntRight(d, compression[j] - 27, 7 - j % 8);
        }
    }

    private static byte[] crypt(byte[] input, byte[][] schedule) {
        int[] leftBits = {57, 49, 41, 33, 25, 17, 9, 1, 59, 51, 43, 35, 27, 19, 11, 3, 61, 53, 45, 37, 29, 21, 13, 5, 63, 55, 47, 39, 31, 23, 15, 7};
        int[] rightBits = {56, 48, 40, 32, 24, 16, 8, 0, 58, 50, 42, 34, 26, 18, 10, 2, 60, 52, 44, 36, 28, 20, 12, 4, 62, 54, 46, 38, 30, 22, 14, 6};
        int left = 0;
        int right = 0;
        for (int i = 0; i < 32; i++) left |= bitNumber(input, 0, leftBits[i], 31 - i);
        for (int i = 0; i < 32; i++) right |= bitNumber(input, 0, rightBits[i], 31 - i);
        for (int i = 0; i < 15; i++) {
            int previousRight = right;
            right = function(right, schedule[i]) ^ left;
            left = previousRight;
        }
        left = function(right, schedule[15]) ^ left;
        byte[] output = new byte[8];
        output[3] = (byte) (bitNumberIntRight(right, 7, 7) | bitNumberIntRight(left, 7, 6) | bitNumberIntRight(right, 15, 5) | bitNumberIntRight(left, 15, 4) | bitNumberIntRight(right, 23, 3) | bitNumberIntRight(left, 23, 2) | bitNumberIntRight(right, 31, 1) | bitNumberIntRight(left, 31, 0));
        output[2] = (byte) (bitNumberIntRight(right, 6, 7) | bitNumberIntRight(left, 6, 6) | bitNumberIntRight(right, 14, 5) | bitNumberIntRight(left, 14, 4) | bitNumberIntRight(right, 22, 3) | bitNumberIntRight(left, 22, 2) | bitNumberIntRight(right, 30, 1) | bitNumberIntRight(left, 30, 0));
        output[1] = (byte) (bitNumberIntRight(right, 5, 7) | bitNumberIntRight(left, 5, 6) | bitNumberIntRight(right, 13, 5) | bitNumberIntRight(left, 13, 4) | bitNumberIntRight(right, 21, 3) | bitNumberIntRight(left, 21, 2) | bitNumberIntRight(right, 29, 1) | bitNumberIntRight(left, 29, 0));
        output[0] = (byte) (bitNumberIntRight(right, 4, 7) | bitNumberIntRight(left, 4, 6) | bitNumberIntRight(right, 12, 5) | bitNumberIntRight(left, 12, 4) | bitNumberIntRight(right, 20, 3) | bitNumberIntRight(left, 20, 2) | bitNumberIntRight(right, 28, 1) | bitNumberIntRight(left, 28, 0));
        output[7] = (byte) (bitNumberIntRight(right, 3, 7) | bitNumberIntRight(left, 3, 6) | bitNumberIntRight(right, 11, 5) | bitNumberIntRight(left, 11, 4) | bitNumberIntRight(right, 19, 3) | bitNumberIntRight(left, 19, 2) | bitNumberIntRight(right, 27, 1) | bitNumberIntRight(left, 27, 0));
        output[6] = (byte) (bitNumberIntRight(right, 2, 7) | bitNumberIntRight(left, 2, 6) | bitNumberIntRight(right, 10, 5) | bitNumberIntRight(left, 10, 4) | bitNumberIntRight(right, 18, 3) | bitNumberIntRight(left, 18, 2) | bitNumberIntRight(right, 26, 1) | bitNumberIntRight(left, 26, 0));
        output[5] = (byte) (bitNumberIntRight(right, 1, 7) | bitNumberIntRight(left, 1, 6) | bitNumberIntRight(right, 9, 5) | bitNumberIntRight(left, 9, 4) | bitNumberIntRight(right, 17, 3) | bitNumberIntRight(left, 17, 2) | bitNumberIntRight(right, 25, 1) | bitNumberIntRight(left, 25, 0));
        output[4] = (byte) (bitNumberIntRight(right, 0, 7) | bitNumberIntRight(left, 0, 6) | bitNumberIntRight(right, 8, 5) | bitNumberIntRight(left, 8, 4) | bitNumberIntRight(right, 16, 3) | bitNumberIntRight(left, 16, 2) | bitNumberIntRight(right, 24, 1) | bitNumberIntRight(left, 24, 0));
        return output;
    }

    private static int function(int state, byte[] key) {
        int first = bitNumberIntLeft(state, 31, 0) | ((state & 0xf0000000) >>> 1) | bitNumberIntLeft(state, 4, 5)
                | bitNumberIntLeft(state, 3, 6) | ((state & 0x0f000000) >>> 3) | bitNumberIntLeft(state, 8, 11)
                | bitNumberIntLeft(state, 7, 12) | ((state & 0x00f00000) >>> 5) | bitNumberIntLeft(state, 12, 17)
                | bitNumberIntLeft(state, 11, 18) | ((state & 0x000f0000) >>> 7) | bitNumberIntLeft(state, 16, 23);
        int second = bitNumberIntLeft(state, 15, 0) | ((state & 0x0000f000) << 15) | bitNumberIntLeft(state, 20, 5)
                | bitNumberIntLeft(state, 19, 6) | ((state & 0x00000f00) << 13) | bitNumberIntLeft(state, 24, 11)
                | bitNumberIntLeft(state, 23, 12) | ((state & 0x000000f0) << 11) | bitNumberIntLeft(state, 28, 17)
                | bitNumberIntLeft(state, 27, 18) | ((state & 0x0000000f) << 9) | bitNumberIntLeft(state, 0, 23);
        int[] expanded = {
                (first >>> 24) & 0xff, (first >>> 16) & 0xff, (first >>> 8) & 0xff,
                (second >>> 24) & 0xff, (second >>> 16) & 0xff, (second >>> 8) & 0xff
        };
        for (int i = 0; i < 6; i++) expanded[i] ^= key[i] & 0xff;
        int[] six = {
                expanded[0] >>> 2,
                ((expanded[0] & 3) << 4) | (expanded[1] >>> 4),
                ((expanded[1] & 15) << 2) | (expanded[2] >>> 6),
                expanded[2] & 63,
                expanded[3] >>> 2,
                ((expanded[3] & 3) << 4) | (expanded[4] >>> 4),
                ((expanded[4] & 15) << 2) | (expanded[5] >>> 6),
                expanded[5] & 63
        };
        int substituted = 0;
        for (int i = 0; i < 8; i++) {
            int index = (six[i] & 0x20) | ((six[i] & 0x1f) >>> 1) | ((six[i] & 1) << 4);
            substituted |= SBOXES[i][index] << (28 - i * 4);
        }
        int[] permutation = {15, 6, 19, 20, 28, 11, 27, 16, 0, 14, 22, 25, 4, 17, 30, 9, 1, 7, 23, 13, 31, 26, 2, 8, 18, 12, 29, 5, 21, 10, 3, 24};
        int output = 0;
        for (int i = 0; i < 32; i++) output |= bitNumberIntLeft(substituted, permutation[i], i);
        return output;
    }

    private static int bitNumber(byte[] input, int offset, int bit, int target) {
        int index = offset + bit / 32 * 4 + 3 - bit % 32 / 8;
        return ((input[index] >>> (7 - bit % 8)) & 1) << target;
    }

    private static byte bitNumberIntRight(int input, int bit, int target) {
        return (byte) (((input >>> (31 - bit)) & 1) << target);
    }

    private static int bitNumberIntLeft(int input, int bit, int target) {
        return ((input << bit) & 0x80000000) >>> target;
    }

}
