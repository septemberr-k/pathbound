package dev.pathbound;

import java.util.Locale;

/** Time-based animation math for the chooser; pure Java so JUnit can test it without a game. */
public final class Anim {
    private Anim() {
    }

    /** Easing curves by name, as layout.json writes them. */
    public enum Easing {
        LINEAR, EASE_OUT, EASE_IN_OUT, EASE_OUT_BACK;

        public static Easing byName(String name) {
            try {
                return valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return EASE_OUT;
            }
        }

        /** t in [0, 1] (clamped) to eased progress; EASE_OUT_BACK overshoots a little above 1 before settling. */
        public double apply(double t) {
            t = clamp01(t);
            return switch (this) {
                case LINEAR -> t;
                case EASE_OUT -> 1 - Math.pow(1 - t, 3);
                case EASE_IN_OUT -> t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
                case EASE_OUT_BACK -> {
                    double c1 = 1.70158, c3 = c1 + 1;
                    yield 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
                }
            };
        }
    }

    public static double clamp01(double t) {
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }

    /** Linear progress of an animation that starts delayMs after start and lasts durationMs; 1 when duration is 0. */
    public static double progress(long nowMs, long startMs, long delayMs, long durationMs) {
        if (durationMs <= 0) return nowMs >= startMs + delayMs ? 1 : 0;
        return clamp01((double) (nowMs - startMs - delayMs) / durationMs);
    }

    /**
     * Moves current towards target, frame-rate independent: after halfLifeMs half the remaining distance is gone,
     * whatever the frame times were. halfLifeMs &lt;= 0 jumps straight to the target.
     */
    public static double approach(double current, double target, double dtMs, double halfLifeMs) {
        if (halfLifeMs <= 0 || dtMs < 0) return target;
        double k = Math.pow(0.5, dtMs / halfLifeMs);
        double v = target + (current - target) * k;
        return Math.abs(v - target) < 1e-3 ? target : v;
    }

    /** A dip that goes down and back up once over durationMs: 0 at both ends, 1 in the middle. */
    public static double dip(long nowMs, long startMs, long durationMs) {
        double t = progress(nowMs, startMs, 0, durationMs);
        return Math.sin(Math.PI * t);
    }

    /** A slow pulse between lo and hi with the given period. */
    public static double pulse(long nowMs, long periodMs, double lo, double hi) {
        if (periodMs <= 0) return hi;
        double s = 0.5 + 0.5 * Math.sin(2 * Math.PI * (nowMs % periodMs) / periodMs);
        return lo + (hi - lo) * s;
    }

    /** Scales the alpha byte of an ARGB colour; keeps it at least 5 because the font skips nearly transparent text. */
    public static int withAlpha(int argb, double alpha) {
        int a = (int) Math.round((argb >>> 24) * clamp01(alpha));
        return Math.max(a, 5) << 24 | argb & 0x00FFFFFF;
    }
}
