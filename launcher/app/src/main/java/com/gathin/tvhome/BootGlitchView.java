package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.Random;

/**
 * HELL CITY boot glitch: about 0.6 s of torn colour bars over a black screen that thins out to show Home,
 * played when Home first appears and when the TV wakes. Draws only while playing; GONE the rest of the time.
 */
final class BootGlitchView extends View {
    private static final int FRAMES = 14;
    private static final long FRAME_MS = 42;
    private static final int[] COLORS = {0xFF5FE3E8, 0xFFE3242B, 0xFFDCA229, 0xFFF2F2F2};

    private final Paint paint = new Paint();
    private final Random random = new Random();
    private int frame = -1;

    private final Runnable step = new Runnable() {
        @Override public void run() {
            frame++;
            if (frame >= FRAMES) {
                frame = -1;
                setVisibility(GONE);
                return;
            }
            invalidate();
            postDelayed(this, FRAME_MS);
        }
    };

    BootGlitchView(Context ctx) {
        super(ctx);
        setFocusable(false);
        setVisibility(GONE);
    }

    void play() {
        removeCallbacks(step);
        frame = 0;
        setVisibility(VISIBLE);
        invalidate();
        postDelayed(step, FRAME_MS);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(step);
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        if (frame < 0) return;
        int w = getWidth(), h = getHeight();
        float t = frame / (float) FRAMES;
        // Black curtain that thins out, with a stutter on two frames.
        int curtain = (int) (255 * Math.max(0f, 1f - t * 1.35f));
        if (frame == 3 || frame == 7) curtain = Math.min(255, curtain + 90);
        paint.setColor(0xFF000000);
        paint.setAlpha(curtain);
        c.drawRect(0, 0, w, h, paint);
        // Torn horizontal bars, fewer and fainter as it settles.
        int bars = Math.max(2, (int) (16 * (1f - t)));
        for (int i = 0; i < bars; i++) {
            float y = random.nextInt(h);
            float bh = 2 + random.nextInt(Math.max(3, h / 40));
            float x = random.nextInt(w / 3) - w / 6f;
            float bw = w * (0.3f + random.nextFloat() * 0.9f);
            paint.setColor(COLORS[random.nextInt(COLORS.length)]);
            paint.setAlpha((int) (200 * (1f - t)));
            c.drawRect(x, y, x + bw, y + bh, paint);
        }
        // Scanlines across the whole screen while it is still dark.
        paint.setColor(0xFF000000);
        paint.setAlpha((int) (90 * (1f - t)));
        for (int y = 0; y < h; y += 4) c.drawRect(0, y, w, y + 1, paint);
    }
}
