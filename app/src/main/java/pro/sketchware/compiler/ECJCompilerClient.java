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

public class ECJCompilerClient {

    private static final String TAG = "ECJCompilerClient";

    public interface Listener {
        void onProgress(String message);
        void onSuccess(String output);
        void onError(String errors, String output);
        void onOOM();
    }

    public static void compile(Context context, String[] ecjArgs, Listener listener) {
        new CompilerConnection(context, ecjArgs, listener).bind();
    }

    private static class CompilerConnection implements ServiceConnection {
        private final Context context;
        private final String[] args;
        private final Listener listener;
        private Messenger serviceMessenger;

        CompilerConnection(Context context, String[] args, Listener listener) {
            this.context = context.getApplicationContext();
            this.args = args;
            this.listener = listener;
        }

        void bind() {
            Intent intent = new Intent(context, ECJCompilerService.class);
            if (!context.bindService(intent, this, Context.BIND_AUTO_CREATE)) {
                listener.onError("Could not start isolated Java compiler service.", "");
            }
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            serviceMessenger = new Messenger(service);
            try {
                Message message = Message.obtain(null, ECJCompilerService.MSG_COMPILE);
                Bundle data = new Bundle();
                data.putStringArray(ECJCompilerService.KEY_ARGS, args);
                message.setData(data);
                message.replyTo = new Messenger(new ResultHandler(context, this, listener));
                serviceMessenger.send(message);
            } catch (Exception e) {
                listener.onError("Failed to talk to isolated compiler: " + e.getMessage(), "");
                unbind();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            listener.onError("Isolated compiler service disconnected unexpectedly.", "");
        }

        void unbind() {
            try {
                context.unbindService(this);
            } catch (Exception ignored) {
            }
        }
    }

    private static class ResultHandler extends Handler {
        private final CompilerConnection connection;
        private final Listener listener;

        ResultHandler(Context context, CompilerConnection connection, Listener listener) {
            super(Looper.getMainLooper());
            this.connection = connection;
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
                    connection.unbind();
                    break;
                case ECJCompilerService.MSG_COMPILE_ERROR:
                    listener.onError(data.getString(ECJCompilerService.KEY_ERRORS, "Unknown error"),
                            data.getString(ECJCompilerService.KEY_OUTPUT, ""));
                    connection.unbind();
                    break;
                case ECJCompilerService.MSG_OOM:
                    listener.onOOM();
                    connection.unbind();
                    break;
                default:
                    Log.w(TAG, "Unknown compiler message: " + msg.what);
                    connection.unbind();
                    break;
            }
        }
    }
}
