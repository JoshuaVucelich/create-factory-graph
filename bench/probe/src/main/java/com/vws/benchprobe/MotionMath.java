package com.vws.benchprobe;

/**
 * Create's client does not copy a contraption angle or piston offset from the server packet.
 * It keeps integrating locally and bleeds the error in. These formulas follow
 * MechanicalBearingBlockEntity and LinearActuatorBlockEntity in Create 6.0.10:
 * bearings lazy-sync every 3 ticks, clientAngleDiff is halved each client tick and added
 * as diff/3 to angular speed; pistons multiply clientOffsetDiff by 0.75 and add diff/2
 * to linear speed. ServerSpeedProvider is 1, which is a client that is keeping up with
 * the server tick.
 */
public final class MotionMath {
    public static final int LAZY_SYNC_TICKS = 3;

    public static final class Score {
        public double desyncRms;
        public double jitterRms;
        public double jitterExcessRms;
        public double serverJitterRms;
        public double angleStepStddev;
        /** Absolute distance the server scalar moved across the window (degrees or blocks). */
        public double travel;
        public int samples;

        public static Score empty() {
            return new Score();
        }
    }

    public static float wrap360(float angle) {
        float w = angle % 360f;
        if (w < 0f) w += 360f;
        return w;
    }

    /** Shortest signed degrees from {@code from} to {@code to}, in (-180, 180]. */
    public static float shortest(float from, float to) {
        float d = wrap360(to) - wrap360(from);
        if (d > 180f) d -= 360f;
        if (d <= -180f) d += 360f;
        return d;
    }

    /**
     * @param serverAngle degrees, one sample per server tick
     * @param degPerTick  angular speed the client integrates (Create's convertToAngular)
     * @param sx,sy,sz    server world position of an off-axis point
     * @param pivotX,pivotZ horizontal pivot (the contraption anchor)
     */
    public static Score bearing(float[] serverAngle, float[] degPerTick,
                                double[] sx, double[] sy, double[] sz,
                                double pivotX, double pivotZ) {
        int n = serverAngle.length;
        if (n < 3 || degPerTick.length < n || sx.length < n) return Score.empty();
        double[] cx = new double[n];
        double[] cy = new double[n];
        double[] cz = new double[n];
        float client = serverAngle[0];
        float diff = 0f;
        cx[0] = sx[0];
        cy[0] = sy[0];
        cz[0] = sz[0];
        for (int t = 1; t < n; t++) {
            diff /= 2f;
            client = wrap360(client + degPerTick[t] + diff / 3f);
            rotateY(sx[t], sy[t], sz[t], pivotX, pivotZ, client - serverAngle[t], cx, cy, cz, t);
            if (t % LAZY_SYNC_TICKS == 0) diff = shortest(client, serverAngle[t]);
        }
        return score(sx, sy, sz, cx, cy, cz, serverAngle);
    }

    /**
     * Piston / linear actuator. {@code linearPerTick} is Create's movement in blocks
     * after {@code MechanicalPistonBlockEntity}'s facing modifier
     * ({@code -convertToLinear(speed) * axisDirection}), not raw RPM.
     * {@code fx,fy,fz} is the unit axis. Client offset follows read() + tick().
     */
    public static Score piston(float[] serverOffset, float[] linearPerTick,
                               double[] sx, double[] sy, double[] sz,
                               double fx, double fy, double fz) {
        int n = serverOffset.length;
        if (n < 3 || linearPerTick.length < n || sx.length < n) return Score.empty();
        double[] cx = new double[n];
        double[] cy = new double[n];
        double[] cz = new double[n];
        float client = serverOffset[0];
        float diff = 0f;
        cx[0] = sx[0];
        cy[0] = sy[0];
        cz[0] = sz[0];
        for (int t = 1; t < n; t++) {
            diff *= 0.75f;
            float linear = clamp(linearPerTick[t], -0.49f, 0.49f);
            client += linear + diff / 2f;
            double err = client - serverOffset[t];
            cx[t] = sx[t] + fx * err;
            cy[t] = sy[t] + fy * err;
            cz[t] = sz[t] + fz * err;
            if (t % LAZY_SYNC_TICKS == 0) diff = serverOffset[t] - client;
        }
        return score(sx, sy, sz, cx, cy, cz, serverOffset);
    }

    /**
     * Belt items. The client advances by {@code movementPerTick} every tick while the
     * item is moving. A skipped server inventory tick leaves {@code serverPos} behind
     * that integral. An item that sits still for several ticks is jammed: the client
     * is jammed too, so the ideal snaps back and those ticks are not rubber-banding.
     * A step larger than half a block is a different item and resets the ideal.
     */
    public static double beltDesyncRms(float[] serverPos, float[] movementPerTick) {
        int n = Math.min(serverPos.length, movementPerTick.length);
        if (n < 2) return 0;
        double ideal = serverPos[0];
        double acc = 0;
        int used = 0;
        int stopped = 0;
        for (int t = 1; t < n; t++) {
            float step = serverPos[t] - serverPos[t - 1];
            float expected = movementPerTick[t];
            if (Math.abs(step) > 0.5f) {
                ideal = serverPos[t];
                stopped = 0;
                continue;
            }
            if (Math.abs(step) < 1.0e-4f) {
                stopped++;
                if (stopped >= 3) {
                    ideal = serverPos[t];
                    used++;
                    continue;
                }
            } else {
                stopped = 0;
            }
            ideal += expected;
            double d = ideal - serverPos[t];
            acc += d * d;
            used++;
        }
        return used == 0 ? 0 : Math.sqrt(acc / used);
    }

    public static Score score(double[] sx, double[] sy, double[] sz,
                              double[] cx, double[] cy, double[] cz,
                              float[] steps) {
        int n = sx.length;
        Score out = new Score();
        if (n < 3) return out;
        double desync = 0;
        double jit = 0;
        double serverJit = 0;
        double jx = 0, jy = 0, jz = 0;
        int jn = 0;
        for (int t = 0; t < n; t++) {
            double dx = cx[t] - sx[t];
            double dy = cy[t] - sy[t];
            double dz = cz[t] - sz[t];
            desync += dx * dx + dy * dy + dz * dz;
        }
        double[] ex = new double[n];
        double[] ey = new double[n];
        double[] ez = new double[n];
        for (int t = 2; t < n; t++) {
            double exi = cx[t] - 2 * cx[t - 1] + cx[t - 2];
            double eyi = cy[t] - 2 * cy[t - 1] + cy[t - 2];
            double ezi = cz[t] - 2 * cz[t - 1] + cz[t - 2];
            ex[t] = exi;
            ey[t] = eyi;
            ez[t] = ezi;
            jit += exi * exi + eyi * eyi + ezi * ezi;
            jx += exi;
            jy += eyi;
            jz += ezi;
            double sxi = sx[t] - 2 * sx[t - 1] + sx[t - 2];
            double syi = sy[t] - 2 * sy[t - 1] + sy[t - 2];
            double szi = sz[t] - 2 * sz[t - 1] + sz[t - 2];
            serverJit += sxi * sxi + syi * syi + szi * szi;
            jn++;
        }
        double excess = 0;
        if (jn > 0) {
            jx /= jn;
            jy /= jn;
            jz /= jn;
            for (int t = 2; t < n; t++) {
                double dx = ex[t] - jx;
                double dy = ey[t] - jy;
                double dz = ez[t] - jz;
                excess += dx * dx + dy * dy + dz * dz;
            }
        }
        out.samples = n;
        out.desyncRms = Math.sqrt(desync / n);
        out.jitterRms = jn == 0 ? 0 : Math.sqrt(jit / jn);
        out.jitterExcessRms = jn == 0 ? 0 : Math.sqrt(excess / jn);
        out.serverJitterRms = jn == 0 ? 0 : Math.sqrt(serverJit / jn);
        out.angleStepStddev = stepStddev(steps);
        return out;
    }

    /** Pool two scores that already store sums via samples. Recomputes from RMS by weight. */
    public static Score pool(Score a, Score b) {
        if (a.samples == 0) return b;
        if (b.samples == 0) return a;
        Score s = new Score();
        int n = a.samples + b.samples;
        s.samples = n;
        s.desyncRms = Math.sqrt((sq(a.desyncRms) * a.samples + sq(b.desyncRms) * b.samples) / n);
        int ja = Math.max(0, a.samples - 2);
        int jb = Math.max(0, b.samples - 2);
        int jn = ja + jb;
        if (jn > 0) {
            s.jitterRms = Math.sqrt((sq(a.jitterRms) * ja + sq(b.jitterRms) * jb) / jn);
            s.jitterExcessRms = Math.sqrt((sq(a.jitterExcessRms) * ja + sq(b.jitterExcessRms) * jb) / jn);
            s.serverJitterRms = Math.sqrt((sq(a.serverJitterRms) * ja + sq(b.serverJitterRms) * jb) / jn);
        }
        s.angleStepStddev = Math.sqrt((sq(a.angleStepStddev) * Math.max(1, a.samples - 1)
                + sq(b.angleStepStddev) * Math.max(1, b.samples - 1))
                / (Math.max(1, a.samples - 1) + Math.max(1, b.samples - 1)));
        s.travel = (a.travel * a.samples + b.travel * b.samples) / n;
        return s;
    }

    public static double stepStddev(float[] steps) {
        int n = steps.length;
        if (n < 2) return 0;
        double mean = 0;
        int c = 0;
        float prev = steps[0];
        double[] d = new double[n - 1];
        for (int i = 1; i < n; i++) {
            float cur = steps[i];
            float delta = cur - prev;
            if (Math.abs(delta) > 180f) delta = shortest(prev, cur);
            d[c] = delta;
            mean += delta;
            prev = cur;
            c++;
        }
        if (c == 0) return 0;
        mean /= c;
        double acc = 0;
        for (int i = 0; i < c; i++) {
            double e = d[i] - mean;
            acc += e * e;
        }
        return Math.sqrt(acc / c);
    }

    public static final class TickStats {
        public double mean;
        public double p99;
        public double stddev;
        public int n;

        public static TickStats of(long[] nanos, int from, int to) {
            TickStats s = new TickStats();
            int n = Math.max(0, to - from);
            s.n = n;
            if (n == 0) return s;
            double[] ms = new double[n];
            double sum = 0;
            for (int i = 0; i < n; i++) {
                ms[i] = nanos[from + i] / 1_000_000d;
                sum += ms[i];
            }
            s.mean = sum / n;
            double acc = 0;
            for (int i = 0; i < n; i++) {
                double e = ms[i] - s.mean;
                acc += e * e;
            }
            s.stddev = Math.sqrt(acc / n);
            java.util.Arrays.sort(ms);
            int idx = (int) Math.ceil(0.99 * n) - 1;
            if (idx < 0) idx = 0;
            if (idx >= n) idx = n - 1;
            s.p99 = ms[idx];
            return s;
        }
    }

    private static void rotateY(double x, double y, double z, double pivotX, double pivotZ, float degrees,
                                double[] cx, double[] cy, double[] cz, int t) {
        double r = Math.toRadians(degrees);
        double cos = Math.cos(r);
        double sin = Math.sin(r);
        double dx = x - pivotX;
        double dz = z - pivotZ;
        cx[t] = pivotX + dx * cos - dz * sin;
        cy[t] = y;
        cz[t] = pivotZ + dx * sin + dz * cos;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double sq(double v) {
        return v * v;
    }

    private MotionMath() {}
}
