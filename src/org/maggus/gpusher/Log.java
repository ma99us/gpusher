package org.maggus.gpusher;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Created by Mike on 2017-06-20.
 */
public class Log {
    private static PrintStream out = System.out;        // #debug
    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH.mm.ss");
    private static LogListener listener;

    public static void setLogListener(LogListener lsnr) {
        listener = lsnr;
    }

    public static void log(String text) {
        log(Level.info, text);
    }

    public static void log(Level level, String text) {
        out.println(text);
        if (listener != null)
            listener.onLog(level, text);
    }

    public static String getTimestamp() {
        return sdf.format(Calendar.getInstance().getTime());
    }

    public static enum Level {info, warn, err}

    public static interface LogListener {
        void onLog(Level lvl, String message);
    }
}
