package com.downloader;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Standalone Dukascopy tick-data downloader.
 *
 * Usage:
 *   java -jar tick-exporter.jar [INSTRUMENT] [FROM] [TO] [OUT_FILE] [POINT_VALUE]
 *
 * Example:
 *   java -jar tick-exporter.jar EURUSD 2024-01-01 2024-01-02 ticks.csv 100000
 *
 * Defaults match the original JForex strategy (but shortened for demo).
 */
public class TickDownloader {

    // ============ Defaults (overridable via args) ============
    private static String INSTRUMENT  = "EURUSD";
    private static String FROM_STR    = "2024-01-01 00:00:00";
    private static String TO_STR      = "2024-01-02 00:00:00";
    private static String OUT_FILE    = "ticks.csv";
    private static int POINT_VALUE    = 100_000;   // 5-decimal pairs
    // =========================================================

    private static final String BASE_URL =
            "https://datafeed.dukascopy.com/datafeed/";
    private static final int MAX_RETRIES = 3;

    public static void main(String[] args) throws Exception {

        if (args.length >= 1) INSTRUMENT  = args[0];
        if (args.length >= 2) FROM_STR    = args[1] + " 00:00:00";
        if (args.length >= 3) TO_STR      = args[2] + " 00:00:00";
        if (args.length >= 4) OUT_FILE    = args[3];
        if (args.length >= 5) POINT_VALUE = Integer.parseInt(args[4]);

        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        parser.setTimeZone(TimeZone.getTimeZone("GMT"));

        long from = parser.parse(FROM_STR).getTime();
        long to   = parser.parse(TO_STR).getTime();

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        fmt.setTimeZone(TimeZone.getTimeZone("GMT"));

        long totalTicks = 0;

        // ---- ساخت پوشه در صورت عدم وجود ----
        File file = new File(OUT_FILE);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        try (PrintWriter out = new PrintWriter(
                new BufferedWriter(new FileWriter(file), 65536))) {

            out.println("GmtTime,Bid,Ask,BidVolume,AskVolume");
            // ... بقیه کد بدون تغییر

            // Iterate hour by hour
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
            cal.setTimeInMillis(from);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            long cursor = cal.getTimeInMillis();
            long lastTickTime = -1;

            while (cursor < to) {
                long hourStart = cursor;
                cursor += 3_600_000L; // +1 hour

                // Build URL:  .../EURUSD/2024/00/01/00h_ticks.bi5
                Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
                c.setTimeInMillis(hourStart);
                int year  = c.get(Calendar.YEAR);
                int month = c.get(Calendar.MONTH);       // 0-based!
                int day   = c.get(Calendar.DAY_OF_MONTH);
                int hour  = c.get(Calendar.HOUR_OF_DAY);

                String url = String.format(
                        "%s%s/%04d/%02d/%02d/%02dh_ticks.bi5",
                        BASE_URL, INSTRUMENT, year, month, day, hour);

                byte[] data = downloadWithRetry(url);
                if (data == null) continue; // no data for this hour

                List<Bi5Decoder.Tick> ticks =
                        Bi5Decoder.decode(data, hourStart, POINT_VALUE);

                for (Bi5Decoder.Tick t : ticks) {
                    if (t.time < from || t.time >= to) continue;
                    if (t.time <= lastTickTime) continue;
                    lastTickTime = t.time;

                    out.printf(Locale.US, "%s,%.5f,%.5f,%.2f,%.2f%n",
                            fmt.format(new Date(t.time)),
                            t.bid, t.ask,
                            t.bidVolume, t.askVolume);
                    totalTicks++;
                }

                System.out.printf("  %s  | ticks this hour: %d | total: %d%n",
                        fmt.format(new Date(hourStart)),
                        ticks.size(), totalTicks);
            }
        }

        System.out.println("✅ FINISHED — total ticks = " + totalTicks);
        System.out.println("📁 Output: " + new File(OUT_FILE).getAbsolutePath());
    }

    // ---------- HTTP helper with retries ----------
    private static byte[] downloadWithRetry(String urlStr) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn =
                        (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(15_000);
                conn.setRequestMethod("GET");

                int code = conn.getResponseCode();
                if (code == 404) return null; // no data (weekend/holiday)
                if (code != 200) {
                    System.err.println("HTTP " + code + " for " + urlStr);
                    continue;
                }

                try (InputStream in = conn.getInputStream();
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) baos.write(buf, 0, n);
                    return baos.toByteArray();
                }
            } catch (Exception e) {
                System.err.println("Attempt " + attempt + " failed: " + e);
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(2000L * attempt); }
                    catch (InterruptedException ignored) {}
                }
            }
        }
        return null;
    }
}
