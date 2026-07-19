package pro.sketchware.compiler;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

/**
 * Client for the isolated ECJ compiler service.
 *
 * Keeps the service bound across builds (compiler daemon pattern): the first
 * compile() call binds ECJCompilerService and stores the resulting Messenger in
 * a static field.  Subsequent calls reuse that Messenger directly, skipping the
 * bindService() round-trip and letting the service keep its JVM state warm in
 * memory between builds.
 *
 * If the service dies (OOM, Android kills it, etc.) onServiceDisconnected()
 * clears the singleton so the next compile() call will rebind transparently.
 */
public class ECJCompilerClient {

    private static final String TAG = "ECJCompilerClient";

    private static volatile Messenger sServiceMessenger = null;

    public interface Listener {
        void onProgress(String message);
        void onSuccess(String output);
        void onError(String errors, String output);
        void onOOM();
    }

    public static void compile(Context context, String[] ecjArgs, Listener listener) {
        Messenger existing = sServiceMessenger;
        if (existing != null) {
            sendCompile(existing, ecjArgs, listener);
        } else {
            new DaemonConnection(context, ecjArgs, listener).bind();
        }
    }

    private static void sendCompile(Messenger serviceMessenger, String[] ecjArgs, Listener listener) {
        try {
            Message message = Message.obtain(null, ECJCompilerService.MSG_COMPILE);
            Bundle data = new Bundle();
            data.putStringArray(ECJCompilerService.KEY_ARGS, ecjArgs);
            message.setData(data);
            message.replyTo = new Messenger(new ResultHandler(listener));
            serviceMessenger.send(message);
        } catch (Exception e) {
            listener.onError("Failed to talk to isolated compiler: " + e.getMessage(), "");
        }
    }

    private static class DaemonConnection implements ServiceConnection {
        private final Context appContext;
        private final String[] args;
        private final Listener listener;

        DaemonConnection(Context context, String[] args, Listener listener) {
            this.appContext = context.getApplicationContext();
            this.args = args;
            this.listener = listener;
        }

        void bind() {
            Intent intent = new Intent(appContext, ECJCompilerService.class);
            if (!appContext.bindService(intent, this, Context.BIND_AUTO_CREATE)) {
                listener.onError("Could not start isolated Java compiler service.", "");
            }
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Messenger messenger = new Messenger(service);
            sServiceMessenger = messenger;
            sendCompile(messenger, args, listener);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            sServiceMessenger = null;
            listener.onError("Isolated compiler service disconnected unexpectedly.", "");
        }
    }

    private static class ResultHandler extends Handler {
        private final Listener listener;

        ResultHandler(Listener listener) {
            super(Looper.getMainLooper());
            this.listener = listener;
        }

        @Override
        public void handleMessage(Message msg) {
            Bundle data = msg.getData();
            switch (msg.what) {
                case ECJCompilerService.MSG_PROGRESS:
                    listener.onProgress(data.getString(ECJCompilerService.KEY_PROGRESS, ""));
                    break;
                case ECJCompilerService.MSG_COMPILE_OK:
                    listener.onSuccess(data.getString(ECJCompilerService.KEY_OUTPUT, ""));
                    break;
                case ECJCompilerService.MSG_COMPILE_ERROR:
                    listener.onError(
                            data.getString(ECJCompilerService.KEY_ERRORS, "Unknown error"),
                            data.getString(ECJCompilerService.KEY_OUTPUT, ""));
                    break;
                case ECJCompilerService.MSG_OOM:
                    listener.onOOM();
                    break;
                default:
                    Log.w(TAG, "Unknown compiler message: " + msg.what);
                    break;
            }
        }
    }
}
