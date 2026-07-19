package mod.hey.studios.util;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import kellinwood.logging.LogManager;
import kellinwood.logging.Logger;
import mod.jbk.util.LogUtil;
import pro.sketchware.utility.FileUtil;

public class SystemLogPrinter {

    private static final String PATH = FileUtil.getExternalStorageDir().concat("/.sketchware/debug.txt");
    private static PrintStream ps;

    public static void start() {
        start(PATH);
    }

    public static void start(String path) {
        // Remove logging in Kellinwood's zipsigner
        LogManager.setLoggerFactory(category -> new Logger() {
            @Override
            public boolean isErrorEnabled() {
                return false;
            }

            @Override
            public void error(String message) {
            }

            @Override
            public void error(String message, Throwable t) {
            }

            @Override
            public boolean isWarnEnabled() {
                return false;
            }

            @Override
            public void warn(String message) {
            }

            @Override
            public void warn(String message, Throwable t) {
            }

            @Override
            public boolean isInfoEnabled() {
                return false;
            }

            @Override
            public void info(String message) {
            }

            @Override
            public void info(String message, Throwable t) {
            }

            @Override
            public boolean isDebugEnabled() {
                return false;
            }

            @Override
            public void debug(String message) {
            }

            @Override
            public void debug(String message, Throwable t) {
            }
        });

        // Reset the log file
        FileUtil.writeFile(path, "");

        PrintStream newPs;
        try {
            // Use FileOutputStream instead of FileWriter
            newPs = new PrintStream(new FileOutputStream(path, true), true);
        } catch (IOException e) {
            LogUtil.e("SystemLogPrinter", "IOException while creating PrintStream to " + path, e);
            return;
        }

        // Swap the shared reference and grab the old one to close, all under a short
        // lock — never do the actual close() (disk I/O) while holding the lock, and
        // never on whichever thread happens to call start()/stop(). This matters
        // because ProjectBuilder's constructor calls start() every time it's built,
        // including from ManageLibraryActivity on the UI thread, while a build task
        // may be calling start()/stop() concurrently on a background thread. Closing
        // synchronously here previously caused the UI thread to block waiting for the
        // lock/I/O, surfacing as an ANR ("Close or Wait").
        PrintStream oldPs;
        synchronized (SystemLogPrinter.class) {
            oldPs = ps;
            ps = newPs;
            System.setOut(newPs);
            System.setErr(newPs);
        }
        closeAsync(oldPs);
    }

    public static void stop() {
        PrintStream oldPs;
        synchronized (SystemLogPrinter.class) {
            oldPs = ps;
            ps = null;
        }
        closeAsync(oldPs);
    }

    private static void closeAsync(PrintStream streamToClose) {
        if (streamToClose == null) {
            return;
        }
        new Thread(streamToClose::close, "SystemLogPrinter-close").start();
    }
}
