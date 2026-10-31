package dev.pathbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnimTest {
    @Test
    void easingsStartAtZeroAndEndAtOne() {
        for (Anim.Easing e : Anim.Easing.values()) {
            assertEquals(0, e.apply(0), 1e-9, e.name());
            assertEquals(1, e.apply(1), 1e-9, e.name());
            assertEquals(0, e.apply(-3), 1e-9, e.name() + " clamps below 0");
            assertEquals(1, e.apply(7), 1e-9, e.name() + " clamps above 1");
        }
    }

    @Test
    void easeOutIsAheadOfLinearAndBackOvershoots() {
        assertTrue(Anim.Easing.EASE_OUT.apply(0.5) > 0.5);
        assertTrue(Anim.Easing.EASE_IN_OUT.apply(0.25) < 0.25);
        assertEquals(0.5, Anim.Easing.EASE_IN_OUT.apply(0.5), 1e-9);
        double max = 0;
        for (int i = 0; i <= 100; i++) max = Math.max(max, Anim.Easing.EASE_OUT_BACK.apply(i / 100.0));
        assertTrue(max > 1 && max < 1.15, "back easing overshoots a little: " + max);
    }

    @Test
    void easingNamesFromLayoutJson() {
        assertEquals(Anim.Easing.EASE_OUT_BACK, Anim.Easing.byName("ease_out_back"));
        assertEquals(Anim.Easing.LINEAR, Anim.Easing.byName(" Linear "));
        assertEquals(Anim.Easing.EASE_OUT, Anim.Easing.byName("nonsense"), "unknown names fall back to ease_out");
    }

    @Test
    void progressHonoursDelayAndDuration() {
        assertEquals(0, Anim.progress(1000, 1000, 100, 200), 1e-9);
        assertEquals(0, Anim.progress(1100, 1000, 100, 200), 1e-9);
        assertEquals(0.5, Anim.progress(1200, 1000, 100, 200), 1e-9);
        assertEquals(1, Anim.progress(5000, 1000, 100, 200), 1e-9);
        assertEquals(1, Anim.progress(1100, 1000, 100, 0), 1e-9, "zero duration is instant after the delay");
    }

    @Test
    void approachIsFrameRateIndependent() {
        double one = Anim.approach(0, 10, 100, 50);
        double many = 0;
        for (int i = 0; i < 10; i++) many = Anim.approach(many, 10, 10, 50);
        assertEquals(one, many, 1e-9, "ten 10 ms frames equal one 100 ms frame");
        assertEquals(5, Anim.approach(0, 10, 50, 50), 1e-9, "half the distance after one half-life");
        assertEquals(10, Anim.approach(0, 10, 16, 0), 1e-9, "no smoothing jumps to the target");
        assertEquals(10, Anim.approach(9.9995, 10, 16, 50), 1e-9, "snaps when close");
    }

    @Test
    void dipAndPulse() {
        assertEquals(0, Anim.dip(0, 0, 120), 1e-9);
        assertEquals(1, Anim.dip(60, 0, 120), 1e-9);
        assertEquals(0, Anim.dip(120, 0, 120), 1e-9);
        for (long t = 0; t < 3000; t += 37) {
            double p = Anim.pulse(t, 1200, 0.6, 1.0);
            assertTrue(p >= 0.6 - 1e-9 && p <= 1.0 + 1e-9);
        }
    }

    @Test
    void alphaKeepsTextVisible() {
        assertEquals(0x80FF0000, Anim.withAlpha(0xFFFF0000, 128 / 255.0));
        assertEquals(0x05FF0000, Anim.withAlpha(0xFFFF0000, 0), "never fully transparent text");
        assertEquals(0xFF123456, Anim.withAlpha(0xFF123456, 1));
    }
}
