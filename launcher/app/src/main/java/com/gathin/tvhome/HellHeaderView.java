package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

/**
 * HELL CITY header (hell_header): the parts of the top of Home that change. The day block, the date line with
 * the weekday in Japanese, the NET_LINK box with the Wi-Fi name and a seven-segment clock with a red/cyan
 * offset. Everything that never changes (title, katakana, pill, frame) is baked into the theme's wallpaper.
 * Drawn in the wallpaper's 1920x1080 coordinates and scaled to the view, so the two line up on any density.
 * It redraws once a minute, on the system time tick; nothing animates.
 */
final class HellHeaderView extends View {
    static final float DESIGN_W = 1920f, DESIGN_H = 300f;
    private static final float CLOCK_SCALE = 0.7f, CLOCK_PIVOT_X = 1824f, CLOCK_PIVOT_Y = 70f;

    private static final int AMBER = 0xFFDCA229, INK = 0xFF0C0C0C, MUTED = 0xFF8A7A48;
    private static final int RED = 0xFFE3242B, GLITCH_RED = 0xFFFF2A4F, GLITCH_CYAN = 0xFF28E6F0;
    private static final String[] JP_WEEKDAY = {"日曜日", "月曜日", "火曜日", "水曜日", "木曜日", "金曜日", "土曜日"};
    // Segments a..g of each digit, as in a seven-segment display.
    private static final String[] SEGMENTS = {"abcdef", "bc", "abged", "abgcd", "fgbc", "afgcd", "afgedc", "abc", "abcdefg", "abcfgd"};

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path seg = new Path();
    private final Typeface bold, medium;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, d 'de' MMMM", new Locale("pt", "BR"));
    private String network;
    // Glitch bursts: every 15-25 s while Home is on screen, about 0.8 s of frames, then still again.
    private static final int BURST_FRAMES = 12;
    private static final long BURST_FRAME_MS = 65;
    private final Random random = new Random();
    private final RectF[] slices = {new RectF(), new RectF(), new RectF()};
    private int burstFrame = -1;
    private final Runnable burst = new Runnable() {
        @Override public void run() {
            burstFrame++;
            if (burstFrame >= BURST_FRAMES) {
                burstFrame = -1;
                scheduleBurst();
            } else {
                postDelayed(this, BURST_FRAME_MS);
            }
            invalidate();
        }
    };

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { invalidate(); }
    };

    private void scheduleBurst() {
        removeCallbacks(burst);
        postDelayed(burst, 15_000 + random.nextInt(10_000));
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            scheduleBurst();
        } else {
            removeCallbacks(burst);
            burstFrame = -1;
        }
    }

    HellHeaderView(Context ctx) {
        super(ctx);
        bold = ctx.getResources().getFont(R.font.barlow_condensed_bold);
        medium = ctx.getResources().getFont(R.font.barlow_condensed_medium);
        stroke.setStyle(Paint.Style.STROKE);
        setFocusable(false);
        setWillNotDraw(false);
    }

    /** The network line: the Wi-Fi name (or the link type), or null while offline. */
    void setNetwork(String name) {
        String n = name == null ? null : name.toUpperCase(Locale.ROOT);
        if (n == null ? network != null : !n.equals(network)) {
            network = n;
            invalidate();
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_DATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) getContext().registerReceiver(tick, f, Context.RECEIVER_NOT_EXPORTED);
        else getContext().registerReceiver(tick, f);
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(burst);
        try {
            getContext().unregisterReceiver(tick);
        } catch (IllegalArgumentException ignored) {
        }
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        float k = getWidth() / DESIGN_W;
        canvas.save();
        canvas.scale(k, k);
        Calendar now = Calendar.getInstance();
        drawDate(canvas, now);
        drawNetwork(canvas);
        // The clock group at 70% of its design size, pinned at its top-right corner.
        canvas.save();
        canvas.scale(CLOCK_SCALE, CLOCK_SCALE, CLOCK_PIVOT_X, CLOCK_PIVOT_Y);
        drawClock(canvas, String.format(Locale.ROOT, "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)));
        canvas.restore();
        canvas.restore();
    }

    private void drawDate(Canvas c, Calendar now) {
        fill.setColor(AMBER);
        c.drawRect(96, 84, 188, 176, fill);
        text.setTypeface(bold);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(64);
        text.setLetterSpacing(0);
        text.setColor(INK);
        c.drawText(String.valueOf(now.get(Calendar.DAY_OF_MONTH)), 142, 153, text);
        text.setTypeface(medium);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(28);
        text.setLetterSpacing(0.06f);
        text.setColor(MUTED);
        String line = dateFormat.format(now.getTime()).toUpperCase(new Locale("pt", "BR"))
                + " · " + JP_WEEKDAY[now.get(Calendar.DAY_OF_WEEK) - 1];
        c.drawText(line, 213, 162, text);
    }

    private void drawNetwork(Canvas c) {
        stroke.setColor(AMBER);
        stroke.setStrokeWidth(1.5f);
        c.drawRect(96, 208, 596, 248, stroke);
        text.setTypeface(bold);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(24);
        text.setLetterSpacing(0.08f);
        text.setColor(AMBER);
        String label = network == null ? "NET_LINK OFFLINE // NO SIGNAL" : "NET_LINK CONNECTED // " + network;
        float room = 596 - 112 - 12;
        if (text.measureText(label) > room) {
            int n = text.breakText(label, true, room - text.measureText("…"), null);
            label = label.substring(0, n) + "…";
        }
        c.drawText(label, 112, 237, text);
    }

    /** Seven-segment digits with cut ends, slanted, with red and cyan copies offset behind them. During a
     *  glitch burst the copies spread further and two horizontal slices of the digits jump sideways. */
    private void drawClock(Canvas c, String time) {
        final float x = 1430, y = 70, w = 64, h = 120, gap = 22;
        boolean glitching = burstFrame >= 0;
        float spread = glitching ? 12 + random.nextInt(16) : 5;
        c.save();
        c.translate(y * 0.176f, 0);
        c.skew(-0.176f, 0);
        clockLayer(c, time, x - spread, y, w, h, gap, GLITCH_RED, 190);
        clockLayer(c, time, x + spread, y, w, h, gap, GLITCH_CYAN, 165);
        if (glitching) {
            float left = x - 40, right = x + 520;
            for (RectF r : slices) {
                float top = y - 10 + random.nextInt((int) h + 20);
                r.set(left, top, right, top + 10 + random.nextInt(28));
            }
            c.save();
            for (RectF r : slices) c.clipOutRect(r);
            clockLayer(c, time, x, y, w, h, gap, RED, 255);
            c.restore();
            for (int i = 0; i < slices.length; i++) {
                c.save();
                c.clipRect(slices[i]);
                clockLayer(c, time, x + (random.nextBoolean() ? 1 : -1) * (20 + random.nextInt(45)), y, w, h, gap,
                        i == 1 ? GLITCH_CYAN : RED, 255);
                c.restore();
            }
        } else {
            clockLayer(c, time, x, y, w, h, gap, RED, 255);
        }
        c.restore();
        int digits = 0;
        for (int i = 0; i < time.length(); i++) if (time.charAt(i) != ':') digits++;
        float width = digits * (w + gap) + 30;
        fill.setColor(GLITCH_CYAN);
        fill.setAlpha(180);
        c.drawRect(x + width * 0.45f, y + h + 14, x + width * 0.80f, y + h + 17, fill);
        fill.setAlpha(255);
    }

    private void clockLayer(Canvas c, String time, float x, float y, float w, float h, float gap, int color, int alpha) {
        float cx = x;
        for (int i = 0; i < time.length(); i++) {
            char ch = time.charAt(i);
            if (ch == ':') {
                fill.setColor(color);
                fill.setAlpha(alpha);
                c.drawRect(cx + 4, y + h * 0.28f, cx + 17, y + h * 0.28f + 13, fill);
                c.drawRect(cx + 4, y + h * 0.66f, cx + 17, y + h * 0.66f + 13, fill);
                cx += 30;
            } else if (ch >= '0' && ch <= '9') {
                digit(c, cx, y, w, h, SEGMENTS[ch - '0'], color, alpha);
                cx += w + gap;
            }
        }
    }

    private void digit(Canvas c, float x, float y, float w, float h, String on, int color, int alpha) {
        final float t = 15;
        String all = "afbgecd";
        for (int i = 0; i < all.length(); i++) {
            char s = all.charAt(i);
            boolean lit = on.indexOf(s) >= 0;
            if (!lit && alpha < 255) continue;   // the ghost of unlit segments only once, on the main layer
            fill.setColor(color);
            fill.setAlpha(lit ? alpha : 12);
            switch (s) {
                case 'a': hseg(x + w / 2, y, w, t); break;
                case 'g': hseg(x + w / 2, y + h / 2, w, t); break;
                case 'd': hseg(x + w / 2, y + h, w, t); break;
                case 'f': vseg(x, y + h / 4, h, t); break;
                case 'b': vseg(x + w, y + h / 4, h, t); break;
                case 'e': vseg(x, y + 3 * h / 4, h, t); break;
                default:  vseg(x + w, y + 3 * h / 4, h, t); break;
            }
            c.drawPath(seg, fill);
        }
    }

    private void hseg(float cx, float cy, float w, float t) {
        float l = w - t * 0.5f;
        seg.rewind();
        seg.moveTo(cx - l / 2, cy);
        seg.lineTo(cx - l / 2 + t / 2, cy - t / 2);
        seg.lineTo(cx + l / 2 - t / 2, cy - t / 2);
        seg.lineTo(cx + l / 2, cy);
        seg.lineTo(cx + l / 2 - t / 2, cy + t / 2);
        seg.lineTo(cx - l / 2 + t / 2, cy + t / 2);
        seg.close();
    }

    private void vseg(float cx, float cy, float h, float t) {
        float l = h / 2 - t * 0.5f;
        seg.rewind();
        seg.moveTo(cx, cy - l / 2);
        seg.lineTo(cx + t / 2, cy - l / 2 + t / 2);
        seg.lineTo(cx + t / 2, cy + l / 2 - t / 2);
        seg.lineTo(cx, cy + l / 2);
        seg.lineTo(cx - t / 2, cy + l / 2 - t / 2);
        seg.lineTo(cx - t / 2, cy - l / 2 + t / 2);
        seg.close();
    }

    static int heightFor(int width) {
        return Math.round(width * DESIGN_H / DESIGN_W);
    }
}
