package a.a.a;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import pro.sketchware.R;

@SuppressLint("StaticFieldLeak")
public abstract class MA {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(new ThreadFactory() {
        private int index = 1;

        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "SketchwareTask-" + index++);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    });

    public enum Status {
        PENDING,
        RUNNING,
        FINISHED
    }

    public final Context a;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean completionDispatched = new AtomicBoolean(false);
    private volatile Status status = Status.PENDING;
    private volatile Future<?> future;
    private Runnable scheduledStarter;

    public MA(Context context) {
        a = context;
    }

    public abstract void a();

    public abstract void a(String errorMessage);

    public abstract void b() throws By;

    public final synchronized MA execute() {
        return schedule(0L);
    }

    public final synchronized MA schedule(long delayMillis) {
        if (status != Status.PENDING || scheduledStarter != null) {
            throw new IllegalStateException("Task has already been started");
        }

        scheduledStarter = () -> {
            synchronized (MA.this) {
                if (scheduledStarter == null || cancelled.get()) {
                    dispatchCancelled();
                    return;
                }
                scheduledStarter = null;
                status = Status.RUNNING;
            }

            mainHandler.post(this::onPreExecute);
            future = EXECUTOR.submit(() -> {
                String result = "";
                try {
                    if (!isCancelled()) {
                        b();
                    }
                } catch (Exception e) {
                    Log.e("MA", e.getMessage(), e);
                    if (e instanceof By) {
                        result = e.getMessage();
                    } else {
                        result = a.getString(R.string.common_error_an_error_occurred) + "[" + e.getMessage() + "]";
                    }
                }

                final String finalResult = result == null ? "" : result;
                mainHandler.post(() -> {
                    if (isCancelled()) {
                        dispatchCancelled();
                        return;
                    }
                    if (!completionDispatched.compareAndSet(false, true)) {
                        return;
                    }
                    status = Status.FINISHED;
                    if (finalResult.isEmpty()) {
                        a();
                    } else {
                        a(finalResult);
                        bB.b(a, finalResult, 1).show();
                    }
                });
            });
        };

        if (delayMillis > 0L) {
            mainHandler.postDelayed(scheduledStarter, delayMillis);
        } else {
            scheduledStarter.run();
        }
        return this;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        cancelled.set(true);
        Runnable localStarter;
        synchronized (this) {
            localStarter = scheduledStarter;
            scheduledStarter = null;
        }
        if (localStarter != null) {
            mainHandler.removeCallbacks(localStarter);
        }
        Future<?> localFuture = future;
        if (localFuture != null) {
            localFuture.cancel(mayInterruptIfRunning);
        }
        dispatchCancelled();
        return true;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public Status getStatus() {
        return status;
    }

    protected void onCancelled() {
    }

    protected void onPreExecute() {
    }

    protected void onProgressUpdate(String... values) {
    }

    protected final void publishProgress(String... values) {
        if (isCancelled()) {
            return;
        }
        mainHandler.post(() -> {
            if (!isCancelled()) {
                onProgressUpdate(values);
            }
        });
    }

    private void dispatchCancelled() {
        if (!completionDispatched.compareAndSet(false, true)) {
            return;
        }
        status = Status.FINISHED;
        mainHandler.post(this::onCancelled);
    }
}
