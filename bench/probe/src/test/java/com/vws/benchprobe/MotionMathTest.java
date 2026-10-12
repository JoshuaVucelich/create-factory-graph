package com.vws.benchprobe;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MotionMathTest {
    @Test
    void smoothBearingStaysWithTheClient() {
        int n = 180;
        float[] angle = new float[n];
        float[] deg = new float[n];
        double[] x = new double[n];
        double[] y = new double[n];
        double[] z = new double[n];
        float a = 0f;
        for (int i = 0; i < n; i++) {
            deg[i] = 9.6f;
            angle[i] = a;
            double r = Math.toRadians(a);
            x[i] = Math.cos(r);
            z[i] = Math.sin(r);
            y[i] = 70;
            a = MotionMath.wrap360(a + 9.6f);
        }
        MotionMath.Score smooth = MotionMath.bearing(angle, deg, x, y, z, 0, 0);
        for (int i = 1; i < n; i += 2) angle[i] = angle[i - 1];
        for (int i = 0; i < n; i++) {
            double r = Math.toRadians(angle[i]);
            x[i] = Math.cos(r);
            z[i] = Math.sin(r);
        }
        MotionMath.Score stalled = MotionMath.bearing(angle, deg, x, y, z, 0, 0);
        assertTrue(smooth.desyncRms < 0.05, "smooth desync " + smooth.desyncRms);
        assertTrue(stalled.desyncRms > smooth.desyncRms * 5, "stalled " + stalled.desyncRms + " vs smooth " + smooth.desyncRms);
        assertTrue(stalled.jitterExcessRms > smooth.jitterExcessRms, "stalled excess " + stalled.jitterExcessRms);
    }

    @Test
    void pistonFacingModifierTracksAndASkipDoesNot() {
        int n = 180;
        float step = 0.0625f;
        float[] offset = new float[n];
        float[] linear = new float[n];
        double[] x = new double[n];
        double[] y = new double[n];
        double[] z = new double[n];
        for (int i = 0; i < n; i++) {
            linear[i] = step;
            offset[i] = i * step;
            y[i] = offset[i];
        }
        MotionMath.Score smooth = MotionMath.piston(offset, linear, x, y, z, 0, 1, 0);
        assertTrue(smooth.desyncRms < 0.02, "smooth " + smooth.desyncRms);
        for (int i = 1; i < n; i += 2) offset[i] = offset[i - 1];
        for (int i = 0; i < n; i++) y[i] = offset[i];
        MotionMath.Score stalled = MotionMath.piston(offset, linear, x, y, z, 0, 1, 0);
        assertTrue(stalled.desyncRms > smooth.desyncRms * 5, "stalled " + stalled.desyncRms);
    }

    @Test
    void skippedBeltTicksLeaveTheClientAhead() {
        int n = 40;
        float[] pos = new float[n];
        float[] move = new float[n];
        float p = 1f;
        for (int i = 0; i < n; i++) {
            move[i] = 0.0667f;
            pos[i] = p;
            if ((i & 1) == 0) p += 0.0667f;
        }
        double full = MotionMath.beltDesyncRms(copyAdvance(n, 0.0667f), move);
        double skipped = MotionMath.beltDesyncRms(pos, move);
        assertTrue(full < 1e-3, "full " + full);
        assertTrue(skipped > 0.2, "skipped " + skipped);
        float[] swapped = copyAdvance(n, 0.0667f);
        swapped[n / 2] = swapped[n / 2] - 6f;
        for (int i = n / 2 + 1; i < n; i++) swapped[i] = swapped[n / 2] + (i - n / 2) * 0.0667f;
        double afterSwap = MotionMath.beltDesyncRms(swapped, move);
        assertTrue(afterSwap < 1e-3, "swap " + afterSwap);
        float[] stuck = new float[n];
        java.util.Arrays.fill(stuck, 12f);
        double jammed = MotionMath.beltDesyncRms(stuck, move);
        assertTrue(jammed < 0.05, "jam " + jammed);
    }

    private static float[] copyAdvance(int n, float step) {
        float[] pos = new float[n];
        for (int i = 0; i < n; i++) pos[i] = 1f + i * step;
        return pos;
    }
}
