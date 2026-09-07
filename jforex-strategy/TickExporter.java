// ⚠️ This file is kept for reference / use inside the JForex platform.
// It is NOT compiled by the Maven build (it lives outside src/).

import com.dukascopy.api.*;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class TickExporter implements IStrategy {

    private static final Instrument INSTRUMENT = Instrument.EURUSD;
    private static final String FROM_STR = "2010-01-01 00:00:00";
    private static final String TO_STR   = "2020-01-01 00:00:00";
    private static final String OUT_FILE = "C:/temp/EURUSD_ticks.csv";
    private static final long CHUNK_MS = 6L * 60 * 60 * 1000;

    private IHistory history;
    private IConsole console;

    @Override
    public void onStart(IContext context) throws JFException {
        history = context.getHistory();
        console = context.getConsole();

        PrintWriter out = null;
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            parser.setTimeZone(TimeZone.getTimeZone("GMT"));
            long from = parser.parse(FROM_STR).getTime();
            long to   = parser.parse(TO_STR).getTime();

            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            fmt.setTimeZone(TimeZone.getTimeZone("GMT"));

            out = new PrintWriter(new FileWriter(OUT_FILE), true);
            out.println("GmtTime,Bid,Ask,BidVolume,AskVolume");

            long cursor = from;
            long lastTickTime = -1;
            long total = 0;

            while (cursor < to) {
                long chunkEnd = Math.min(cursor + CHUNK_MS, to);
                List<ITick> ticks = history.getTicks(INSTRUMENT, cursor, chunkEnd);

                for (ITick t : ticks) {
                    if (t.getTime() <= lastTickTime) continue;
                    lastTickTime = t.getTime();
                    out.println(fmt.format(new Date(t.getTime())) + ","
                            + t.getBid() + "," + t.getAsk() + ","
                            + t.getBidVolume() + "," + t.getAskVolume());
                    total++;
                }
                console.getOut().println("chunk: " + fmt.format(new Date(chunkEnd))
                        + " | ticks: " + ticks.size() + " | total: " + total);
                cursor = chunkEnd;
                Thread.sleep(500);
            }
            console.getOut().println("FINISHED. total ticks = " + total);
        } catch (Exception e) {
            console.getErr().println("Error: " + e);
        } finally {
            if (out != null) out.close();
        }
        context.stop();
    }

    @Override public void onTick(Instrument instrument, ITick tick) {}
    @Override public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) {}
    @Override public void onMessage(IMessage message) {}
    @Override public void onAccount(IAccount account) {}
    @Override public void onStop() {}
}
