package pro.sketchware.compiler;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import org.eclipse.jdt.internal.compiler.batch.Main;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ECJCompilerService extends Service {

    private static final String TAG = "ECJCompilerService";

    public static final int MSG_COMPILE = 1;
    public static final int MSG_PROGRESS = 10;
    public static final int MSG_COMPILE_OK = 11;
    public static final int MSG_COMPILE_ERROR = 12;
    public static final int MSG_OOM = 13;

    public static final String KEY_ARGS = "args";
    public static final String KEY_OUTPUT = "output";
    public static final String KEY_ERRORS = "errors";
    public static final String KEY_PROGRESS = "progress";

    /**
     * Compilation timeout in milliseconds — 60 minutes to support very large source trees.
     */
    private static final long COMPILE_TIMEOUT_MS = 60 * 60 * 1000L;

    /**
     * Thread pool that runs ECJ compilations concurrently.
     *
     * Previously, CompilerHandler ran on the main looper and called
     * compileThread.join() inside handleMessage(), which blocked the looper for the
     * entire duration of every compilation.  Any second request queued up and was not
     * dispatched until the first finished — so "parallel ECJ" was effectively serial.
     *
     * Using a pool lets handleMessage() return immediately after submitting the work,
     * so the looper stays unblocked and all requests are dispatched without delay.
     * Each submitted task blocks only its own pool thread (not the looper), and
     * multiple compilations run at the same time on separate threads.
     */
    private ExecutorService compilationPool;

    /**
     * Dedicated thread for the IPC message dispatcher.  Using the main looper would
     * expose the process to ANR if a task ever blocked (it shouldn't, but let's be safe).
     */
    private HandlerThread dispatcherThread;
    private Messenger serviceMessenger;

    @Override
    public void onCreate() {
        super.onCreate();
        int cores = Runtime.getRuntime().availableProcessors();
        compilationPool = Executors.newFixedThreadPool(Math.max(2, cores - 1));
        dispatcherThread = new HandlerThread("ecj-dispatcher");
        dispatcherThread.start();
        serviceMessenger = new Messenger(new CompilerHandler(dispatcherThread.getLooper()));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return serviceMessenger.getBinder();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (compilationPool != null) {
            compilationPool.shutdownNow();
        }
        if (dispatcherThread != null) {
            dispatcherThread.quitSafely();
        }
    }

    private class CompilerHandler extends Handler {
        CompilerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what != MSG_COMPILE || msg.replyTo == null) {
                return;
            }
            String[] args = msg.getData() != null ? msg.getData().getStringArray(KEY_ARGS) : null;
            if (args == null) {
                sendError(msg.replyTo, "No compiler args provided", "");
                return;
            }
            final Messenger replyTo = msg.replyTo;
            final String[] finalArgs = args;
            // Submit to the pool and return immediately — the looper is never blocked.
            compilationPool.submit(() -> compile(finalArgs, replyTo));
        }
    }

    private static void compile(String[] userArgs, Messenger replyTo) {
        sendProgress(replyTo, "Starting isolated Java compiler…");
        Runtime.getRuntime().gc();

        List<String> args = new ArrayList<>();
        args.add("-g:none");
        args.add("-maxProblems");
        args.add("100");
        args.add("-encoding");
        args.add("UTF-8");
        for (String arg : userArgs) {
            args.add(arg);
        }

        StringWriter outWriter = new StringWriter();
        StringWriter errWriter = new StringWriter();

        final String[] finalArgs = args.toArray(new String[0]);
        final boolean[] done = {false};

        Thread compileThread = new Thread(() -> {
            try {
                boolean success = new Main(
                        new PrintWriter(outWriter),
                        new PrintWriter(errWriter),
                        false, null, null)
                        .compile(finalArgs);
                synchronized (done) {
                    if (!done[0]) {
                        done[0] = true;
                        if (success) {
                            Bundle bundle = new Bundle();
                            bundle.putString(KEY_OUTPUT, outWriter.toString());
                            send(replyTo, MSG_COMPILE_OK, bundle);
                        } else {
                            sendError(replyTo,
                                    errWriter.toString().isEmpty() ? outWriter.toString() : errWriter.toString(),
                                    outWriter.toString());
                        }
                    }
                }
            } catch (OutOfMemoryError oom) {
                synchronized (done) {
                    if (!done[0]) {
                        done[0] = true;
                        Bundle bundle = new Bundle();
                        bundle.putString(KEY_ERRORS, "Java compilation ran out of memory in the isolated compiler process.");
                        send(replyTo, MSG_OOM, bundle);
                    }
                }
            } catch (Throwable throwable) {
                synchronized (done) {
                    if (!done[0]) {
                        done[0] = true;
                        Log.e(TAG, "Compilation failed", throwable);
                        sendError(replyTo,
                                throwable.getMessage() != null ? throwable.getMessage() : throwable.toString(),
                                outWriter.toString());
                    }
                }
            }
        }, "ecj-compiler");

        compileThread.setDaemon(true);
        compileThread.start();

        try {
            compileThread.join(COMPILE_TIMEOUT_MS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        synchronized (done) {
            if (!done[0]) {
                done[0] = true;
                compileThread.interrupt();
                sendError(replyTo,
                        "Compilation timed out after " + (COMPILE_TIMEOUT_MS / 60_000) + " minutes.\n"
                        + "Tip: make sure Parallel ECJ is enabled in Build Settings, or reduce the number of large images in your project.",
                        outWriter.toString());
            }
        }
    }

    private static void sendProgress(Messenger replyTo, String progress) {
        Bundle bundle = new Bundle();
        bundle.putString(KEY_PROGRESS, progress);
        send(replyTo, MSG_PROGRESS, bundle);
    }

    private static void sendError(Messenger replyTo, String errors, String output) {
        Bundle bundle = new Bundle();
        bundle.putString(KEY_ERRORS, errors);
        bundle.putString(KEY_OUTPUT, output);
        send(replyTo, MSG_COMPILE_ERROR, bundle);
    }

    private static void send(Messenger replyTo, int what, Bundle bundle) {
        try {
            Message message = Message.obtain(null, what);
            message.setData(bundle);
            replyTo.send(message);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to reply to ECJ client", e);
        }
    }
}
