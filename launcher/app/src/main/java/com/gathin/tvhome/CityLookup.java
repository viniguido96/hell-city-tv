package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * The city the TV is in, for the HELL CITY header, from the public IP (no location permission, no GPS on a TV).
 * The last answer is kept, so Home shows it at once; a new lookup runs at most every 6 hours, or a minute after
 * a failed one (no network yet at boot). Two free services, the second only when the first does not answer.
 */
final class CityLookup {
    interface Listener { void onCity(String city); }

    private static final String PREFS = "hell_city", KEY_CITY = "city", KEY_AT = "at";
    private static final long FRESH_MS = 6 * 3600_000L, RETRY_MS = 60_000L;
    private static final String[][] SERVICES = {
            {"https://ipwho.is/?fields=city", "city"},
            {"https://get.geojs.io/v1/ip/geo.json", "city"},
    };
    private static long lastTry;
    private static boolean running;

    private CityLookup() {}

    /** The last city found, or null before the first answer. */
    static String cached(Context ctx) {
        return prefs(ctx).getString(KEY_CITY, null);
    }

    /** Looks the city up in the background when the last answer is old; the listener runs on the main thread. */
    static void refresh(Context ctx, Listener listener) {
        SharedPreferences p = prefs(ctx);
        long now = System.currentTimeMillis();
        if (running || now - p.getLong(KEY_AT, 0) < FRESH_MS || now - lastTry < RETRY_MS) return;
        running = true;
        lastTry = now;
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String city = null;
            for (String[] s : SERVICES) {
                city = fetch(s[0], s[1]);
                if (city != null) break;
            }
            final String found = city;
            main.post(() -> {
                running = false;
                if (found == null) return;
                p.edit().putString(KEY_CITY, found).putLong(KEY_AT, System.currentTimeMillis()).apply();
                listener.onCity(found);
            });
        }, "city-lookup").start();
    }

    private static String fetch(String url, String key) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            c.setRequestProperty("Accept", "application/json");
            if (c.getResponseCode() != 200) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] buf = new byte[2048];
                for (int n; (n = in.read(buf)) > 0 && out.size() < 16_384; ) out.write(buf, 0, n);
            }
            String city = new JSONObject(out.toString("UTF-8")).optString(key, "").trim();
            return city.isEmpty() ? null : city;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
