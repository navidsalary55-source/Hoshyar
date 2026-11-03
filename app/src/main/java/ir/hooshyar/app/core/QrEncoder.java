package ir.hooshyar.app.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** تولیدکننده‌ی QR کد (حالت بایت/UTF-8، نسخه‌ی ۱ تا ۴۰). ecl: 0=L، 1=M، 2=Q، 3=H */
public final class QrEncoder {
    private static final int[][] ECC_PER_BLOCK = {
        {-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},
        {-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28},
        {-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},
        {-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},
    };
    private static final int[][] NUM_BLOCKS = {
        {-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25},
        {-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49},
        {-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68},
        {-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81},
    };
    private static final int[] FORMAT_BITS = {1, 0, 3, 2};

    private final int ver, size, ecl;
    private final boolean[][] modules, isFunction;

    public static boolean[][] encode(String text, int ecl) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int ver = -1;
        for (int v = 1; v <= 40; v++) {
            int cc = v <= 9 ? 8 : 16;
            int need = 4 + cc + data.length * 8;
            if (data.length < (1 << cc) && need <= numDataCodewords(v, ecl) * 8) { ver = v; break; }
        }
        if (ver < 0) throw new IllegalArgumentException("متن برای QR خیلی طولانی است");
        int cc = ver <= 9 ? 8 : 16;
        List<Boolean> bits = new ArrayList<>();
        appendBits(bits, 4, 4);
        appendBits(bits, data.length, cc);
        for (byte b : data) appendBits(bits, b & 0xFF, 8);
        int cap = numDataCodewords(ver, ecl) * 8;
        appendBits(bits, 0, Math.min(4, cap - bits.size()));
        appendBits(bits, 0, (8 - bits.size() % 8) % 8);
        for (int pad = 0xEC; bits.size() < cap; pad ^= 0xEC ^ 0x11) appendBits(bits, pad, 8);
        byte[] cw = new byte[bits.size() / 8];
        for (int i = 0; i < bits.size(); i++) if (bits.get(i)) cw[i >>> 3] |= 1 << (7 - (i & 7));
        return new QrEncoder(ver, ecl, cw).modules;
    }

    private static void appendBits(List<Boolean> l, int val, int len) {
        for (int i = len - 1; i >= 0; i--) l.add(((val >>> i) & 1) != 0);
    }

    private QrEncoder(int ver, int ecl, byte[] data) {
        this.ver = ver; this.ecl = ecl; this.size = ver * 4 + 17;
        modules = new boolean[size][size]; isFunction = new boolean[size][size];
        drawFunctionPatterns();
        byte[] all = addEccAndInterleave(data);
        drawCodewords(all);
        int best = 0; long bestPen = Long.MAX_VALUE;
        for (int m = 0; m < 8; m++) {
            applyMask(m); drawFormatBits(m);
            long p = penalty();
            if (p < bestPen) { bestPen = p; best = m; }
            applyMask(m);
        }
        applyMask(best); drawFormatBits(best);
    }

    private void drawFunctionPatterns() {
        for (int i = 0; i < size; i++) { setFunction(6, i, i % 2 == 0); setFunction(i, 6, i % 2 == 0); }
        drawFinder(3, 3); drawFinder(size - 4, 3); drawFinder(3, size - 4);
        int[] pos = alignPositions();
        int n = pos.length;
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue;
            drawAlign(pos[i], pos[j]);
        }
        drawFormatBits(0);
        drawVersion();
    }

    private void drawFinder(int x, int y) {
        for (int dy = -4; dy <= 4; dy++) for (int dx = -4; dx <= 4; dx++) {
            int dist = Math.max(Math.abs(dx), Math.abs(dy));
            int xx = x + dx, yy = y + dy;
            if (xx >= 0 && xx < size && yy >= 0 && yy < size) setFunction(xx, yy, dist != 2 && dist != 4);
        }
    }

    private void drawAlign(int x, int y) {
        for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) setFunction(x + dx, y + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
    }

    private int[] alignPositions() {
        if (ver == 1) return new int[0];
        int n = ver / 7 + 2;
        int step = (ver == 32) ? 26 : (ver * 4 + n * 2 + 1) / (n * 2 - 2) * 2;
        int[] r = new int[n];
        r[0] = 6;
        for (int i = n - 1, pos = size - 7; i >= 1; i--, pos -= step) r[i] = pos;
        return r;
    }

    private void drawFormatBits(int mask) {
        int data = FORMAT_BITS[ecl] << 3 | mask;
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = (data << 10 | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) setFunction(8, i, bit(bits, i));
        setFunction(8, 7, bit(bits, 6)); setFunction(8, 8, bit(bits, 7)); setFunction(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) setFunction(14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) setFunction(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) setFunction(8, size - 15 + i, bit(bits, i));
        setFunction(8, size - 8, true);
    }

    private void drawVersion() {
        if (ver < 7) return;
        int rem = ver;
        for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        int bits = ver << 12 | rem;
        for (int i = 0; i < 18; i++) {
            boolean c = bit(bits, i);
            int a = size - 11 + i % 3, b = i / 3;
            setFunction(a, b, c); setFunction(b, a, c);
        }
    }

    private static boolean bit(int x, int i) { return ((x >>> i) & 1) != 0; }

    private void setFunction(int x, int y, boolean dark) { modules[y][x] = dark; isFunction[y][x] = true; }

    private static int rawModules(int ver) {
        int r = (16 * ver + 128) * ver + 64;
        if (ver >= 2) { int n = ver / 7 + 2; r -= (25 * n - 10) * n - 55; if (ver >= 7) r -= 36; }
        return r;
    }

    private static int numDataCodewords(int ver, int ecl) {
        return rawModules(ver) / 8 - ECC_PER_BLOCK[ecl][ver] * NUM_BLOCKS[ecl][ver];
    }

    private byte[] addEccAndInterleave(byte[] data) {
        int nb = NUM_BLOCKS[ecl][ver], eccLen = ECC_PER_BLOCK[ecl][ver];
        int raw = rawModules(ver) / 8;
        int nShort = nb - raw % nb, shortLen = raw / nb;
        byte[][] blocks = new byte[nb][];
        byte[] div = rsDivisor(eccLen);
        for (int i = 0, k = 0; i < nb; i++) {
            int dl = shortLen - eccLen + (i < nShort ? 0 : 1);
            byte[] dat = new byte[dl];
            System.arraycopy(data, k, dat, 0, dl); k += dl;
            byte[] ecc = rsRemainder(dat, div);
            byte[] blk = new byte[shortLen + 1];
            System.arraycopy(dat, 0, blk, 0, dl);
            System.arraycopy(ecc, 0, blk, i < nShort ? dl + 1 : dl, eccLen);
            blocks[i] = blk;
        }
        byte[] out = new byte[raw];
        int idx = 0;
        for (int i = 0; i < shortLen + 1; i++) for (int j = 0; j < nb; j++) {
            if (i != shortLen - eccLen || j >= nShort) out[idx++] = blocks[j][i];
        }
        return out;
    }

    private static byte[] rsDivisor(int degree) {
        byte[] r = new byte[degree];
        r[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < r.length; j++) {
                r[j] = (byte) mul(r[j] & 0xFF, root);
                if (j + 1 < r.length) r[j] ^= r[j + 1];
            }
            root = mul(root, 0x02);
        }
        return r;
    }

    private static byte[] rsRemainder(byte[] data, byte[] div) {
        byte[] r = new byte[div.length];
        for (byte b : data) {
            int f = (b ^ r[0]) & 0xFF;
            System.arraycopy(r, 1, r, 0, r.length - 1);
            r[r.length - 1] = 0;
            for (int i = 0; i < r.length; i++) r[i] ^= mul(div[i] & 0xFF, f);
        }
        return r;
    }

    private static int mul(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) { z = (z << 1) ^ ((z >>> 7) * 0x11D); z ^= ((y >>> i) & 1) * x; }
        return z;
    }

    private void drawCodewords(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) for (int j = 0; j < 2; j++) {
                int x = right - j;
                boolean up = ((right + 1) & 2) == 0;
                int y = up ? size - 1 - vert : vert;
                if (!isFunction[y][x] && i < data.length * 8) { modules[y][x] = bit(data[i >>> 3], 7 - (i & 7)); i++; }
            }
        }
    }

    private void applyMask(int m) {
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            boolean inv;
            switch (m) {
                case 0: inv = (x + y) % 2 == 0; break;
                case 1: inv = y % 2 == 0; break;
                case 2: inv = x % 3 == 0; break;
                case 3: inv = (x + y) % 3 == 0; break;
                case 4: inv = (x / 3 + y / 2) % 2 == 0; break;
                case 5: inv = x * y % 2 + x * y % 3 == 0; break;
                case 6: inv = (x * y % 2 + x * y % 3) % 2 == 0; break;
                default: inv = ((x + y) % 2 + x * y % 3) % 2 == 0; break;
            }
            modules[y][x] ^= inv & !isFunction[y][x];
        }
    }

    private long penalty() {
        long r = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int a = 0; a < size; a++) {
                int run = 1;
                for (int b = 1; b < size; b++) {
                    boolean cur = pass == 0 ? modules[a][b] : modules[b][a];
                    boolean prev = pass == 0 ? modules[a][b - 1] : modules[b - 1][a];
                    if (cur == prev) { run++; if (run == 5) r += 3; else if (run > 5) r++; } else run = 1;
                }
            }
        }
        for (int y = 0; y < size - 1; y++) for (int x = 0; x < size - 1; x++) {
            boolean c = modules[y][x];
            if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) r += 3;
        }
        int dark = 0;
        for (boolean[] row : modules) for (boolean c : row) if (c) dark++;
        int total = size * size;
        int k = (int) ((Math.abs(dark * 20L - total * 10L) + total - 1) / total) - 1;
        r += k * 10L;
        return r;
    }
}
