package com.downloader;

import org.tukaani.xz.LZMAInputStream;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes Dukascopy .bi5 tick files.
 *
 * bi5 format (per tick = 20 bytes, Big-Endian):
 *   int32  timeMs      – milliseconds since the start of the hour
 *   int32  askPrice    – raw ask  (divide by pointValue)
 *   int32  bidPrice    – raw bid  (divide by pointValue)
 *   float  askVolume
 *   float  bidVolume
 */
public class Bi5Decoder {

    public static class Tick {
        public final long time;
        public final double bid;
        public final double ask;
        public final float bidVolume;
        public final float askVolume;

        public Tick(long time, double bid, double ask,
                    float bidVolume, float askVolume) {
            this.time = time;
            this.bid = bid;
            this.ask = ask;
            this.bidVolume = bidVolume;
            this.askVolume = askVolume;
        }
    }

    /**
     * @param bi5Data       raw (compressed) bytes of the .bi5 file
     * @param hourStartMs   epoch-ms of the beginning of that hour (GMT)
     * @param pointValue    e.g. 100_000 for EURUSD (5 decimals)
     */
    public static List<Tick> decode(byte[] bi5Data,
                                    long hourStartMs,
                                    int pointValue) throws IOException {
        List<Tick> ticks = new ArrayList<>();
        if (bi5Data == null || bi5Data.length == 0) return ticks;

        try (LZMAInputStream lzma =
                     new LZMAInputStream(new ByteArrayInputStream(bi5Data))) {

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = lzma.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }

            ByteBuffer bb = ByteBuffer.wrap(baos.toByteArray());
            bb.order(ByteOrder.BIG_ENDIAN);

            while (bb.remaining() >= 20) {
                int timeMs   = bb.getInt();
                int askRaw   = bb.getInt();
                int bidRaw   = bb.getInt();
                float askVol = bb.getFloat();
                float bidVol = bb.getFloat();

                ticks.add(new Tick(
                        hourStartMs + timeMs,
                        (double) bidRaw / pointValue,
                        (double) askRaw / pointValue,
                        bidVol,
                        askVol
                ));
            }
        }
        return ticks;
    }
}
