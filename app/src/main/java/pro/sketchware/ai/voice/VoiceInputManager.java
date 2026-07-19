package pro.sketchware.ai.voice;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import pro.sketchware.ai.utils.AiLog;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Locale;

/**
 * VoiceInputManager — production-grade voice input with:
 * <ul>
 *   <li><b>Listening state UI</b> — callbacks for start/stop/error states</li>
 *   <li><b>Live (partial) transcription</b> — real-time text updates as the user speaks</li>
 *   <li><b>Multi-language support</b> — English, Arabic, or auto-detect (system locale)</li>
 *   <li><b>Permission check</b> — clean error reporting if RECORD_AUDIO is missing</li>
 *   <li><b>Automatic stop on silence</b> — handled by the system SpeechRecognizer</li>
 * </ul>
 *
 * <p>All callbacks are delivered on the <b>main thread</b>.
 *
 * <p>Usage:
 * <pre>
 *   VoiceInputManager voice = new VoiceInputManager(context, Language.ARABIC);
 *   voice.setCallback(new VoiceInputManager.Callback() {
 *       public void onListening()            { showListeningIndicator(); }
 *       public void onPartialResult(String t){ updateInputField(t); }
 *       public void onFinalResult(String t)  { submitText(t); }
 *       public void onError(String e)        { showError(e); }
 *       public void onStopped()              { hideListeningIndicator(); }
 *   });
 *   voice.start();
 *   // …later:
 *   voice.stop();
 *   voice.destroy(); // in onDestroy()
 * </pre>
 */
public final class VoiceInputManager {

    private static final String TAG = "VoiceInputManager";

    // ── Language ──────────────────────────────────────────────────────────────

    public enum Language {
        /** English (United States) */
        ENGLISH("en-US"),
        /** Arabic (Saudi Arabia) */
        ARABIC("ar-SA"),
        /** System locale — let Android decide */
        AUTO(null);

        final String bcp47Tag;
        Language(String tag) { this.bcp47Tag = tag; }

        /** Returns the BCP-47 language tag to pass to SpeechRecognizer. */
        public String getBcp47() {
            return bcp47Tag != null ? bcp47Tag : Locale.getDefault().toLanguageTag();
        }
    }

    // ── Callback ──────────────────────────────────────────────────────────────

    public interface Callback {
        /** SpeechRecognizer is active and waiting for audio input. */
        void onListening();

        /**
         * Partial transcription result — called repeatedly as the user speaks.
         * Update the input field in real-time with this text.
         */
        void onPartialResult(String partialText);

        /**
         * Final transcription result — called when the user stops speaking.
         * This is the text to submit.
         */
        void onFinalResult(String finalText);

        /** An error occurred (e.g. no speech, network error, permission denied). */
        void onError(String errorMessage);

        /** Recognition stopped (regardless of success or error). */
        void onStopped();
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final Context          context;
    private       Language         language;
    private       Callback         callback;
    private       SpeechRecognizer recognizer;
    private final Handler          mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean       listening   = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public VoiceInputManager(Context context, Language language) {
        this.context  = context.getApplicationContext();
        this.language = language != null ? language : Language.AUTO;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Sets the event callback. Must be called before {@link #start()}. */
    public void setCallback(Callback cb) {
        this.callback = cb;
    }

    /** Changes the recognition language. Takes effect on the next {@link #start()} call. */
    public void setLanguage(Language lang) {
        this.language = lang != null ? lang : Language.AUTO;
    }

    /** Returns the currently configured language. */
    public Language getLanguage() {
        return language;
    }

    /** Returns true if recognition is currently active. */
    public boolean isListening() {
        return listening;
    }

    /**
     * Checks if voice input is available and RECORD_AUDIO permission is granted.
     *
     * @return null if available; a human-readable error string if not
     */
    public String checkAvailability() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return "Voice recognition is not available on this device.";
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            return "Microphone permission (RECORD_AUDIO) is required for voice input. "
                    + "Please grant it in app settings.";
        }
        return null; // all good
    }

    /**
     * Starts voice recognition.
     * Safe to call from any thread — posts all work to the main thread.
     * If already listening, stops first then restarts.
     */
    public void start() {
        mainHandler.post(() -> {
            String check = checkAvailability();
            if (check != null) {
                if (callback != null) callback.onError(check);
                return;
            }
            if (listening) stopInternal();
            startInternal();
        });
    }

    /**
     * Stops voice recognition gracefully.
     * Safe to call from any thread.
     */
    public void stop() {
        mainHandler.post(this::stopInternal);
    }

    /**
     * Releases all resources. Call from {@code Activity.onDestroy()} or {@code Fragment.onDestroyView()}.
     */
    public void destroy() {
        mainHandler.post(() -> {
            if (recognizer != null) {
                recognizer.destroy();
                recognizer = null;
            }
            listening = false;
        });
    }

    // ── Private — must only run on the main thread ────────────────────────────

    private void startInternal() {
        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {
                listening = true;
                AiLog.d(TAG, "Listening [" + language.getBcp47() + "]…");
                if (callback != null) callback.onListening();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                // Live transcription — update input field in real-time
                ArrayList<String> matches = partialResults.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty() && callback != null) {
                    callback.onPartialResult(matches.get(0));
                }
            }

            @Override
            public void onResults(Bundle results) {
                listening = false;
                ArrayList<String> matches = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                String text = (matches != null && !matches.isEmpty()) ? matches.get(0) : "";
                AiLog.d(TAG, "Final: " + text);
                if (callback != null) {
                    if (!text.isEmpty()) callback.onFinalResult(text);
                    callback.onStopped();
                }
            }

            @Override
            public void onError(int error) {
                listening = false;
                String msg = errorCodeToMessage(error);
                Log.w(TAG, "RecognitionError code=" + error + ": " + msg);
                if (callback != null) {
                    callback.onError(msg);
                    callback.onStopped();
                }
            }

            // ── Required interface stubs ──────────────────────────────────

            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,            language.getBcp47());
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.getBcp47());
        // Enable partial results for live transcription
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        // Silence detection: stop after ~2 s of silence
        intent.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);
        intent.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                1500L);

        // NOTE: RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_INDEPENDENT_RESULTS does NOT
        // exist in the Android SDK — removed (was causing compilation error).

        recognizer.startListening(intent);
    }

    private void stopInternal() {
        if (recognizer != null) {
            recognizer.stopListening();
            recognizer.destroy();
            recognizer = null;
        }
        if (listening) {
            listening = false;
            if (callback != null) callback.onStopped();
        }
    }

    // ── Error code → human-readable message ──────────────────────────────────

    private static String errorCodeToMessage(int code) {
        switch (code) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Audio recording error. Check that your microphone works.";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Client-side recognition error. Please try again.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Microphone permission denied. Grant RECORD_AUDIO in app settings.";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Network error during recognition. Check your internet connection.";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Network timeout. Try again when connection is stable.";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "No speech detected. Speak clearly and try again.";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Voice recognizer is busy. Wait a moment and try again.";
            case SpeechRecognizer.ERROR_SERVER:
                return "Server-side recognition error. Try again later.";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "Listening timed out — no speech was detected.";
            case SpeechRecognizer.ERROR_TOO_MANY_REQUESTS:
                return "Too many voice recognition requests. Wait a moment.";
            default:
                return "Voice recognition error (code " + code + "). Please try again.";
        }
    }
}
