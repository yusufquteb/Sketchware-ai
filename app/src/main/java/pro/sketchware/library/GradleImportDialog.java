package pro.sketchware.library;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Proposal 22 — Import dependencies from a build.gradle snippet.
 * Fully programmatic UI — no layout XML required.
 */
public class GradleImportDialog extends DialogFragment {

    public interface ImportListener {
        void onImportConfirmed(List<String> coordinates);
    }

    private ImportListener listener;

    static class ParsedDep {
        final String coordinate;
        final String display;
        boolean selected = true;

        ParsedDep(String coord) {
            coordinate = coord;
            String[] parts = coord.split(":");
            display = parts.length >= 3
                    ? parts[1] + "  " + parts[2]
                    : parts.length == 2 ? parts[1] : coord;
        }
    }

    private static final Pattern IMPL_QUOTED =
            Pattern.compile("(?:implementation|api|compileOnly|runtimeOnly|testImplementation)\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern IMPL_KTS =
            Pattern.compile("(?:implementation|api|compileOnly|runtimeOnly)\\(\"([^\"]+)\"\\)");

    public static GradleImportDialog newInstance() { return new GradleImportDialog(); }

    public void setImportListener(ImportListener l) { this.listener = l; }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        int pad = (int)(16 * dp);
        int pad8 = (int)(8 * dp);

        // Root layout
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        // Input area
        TextView hint = new TextView(ctx);
        hint.setText("Paste your dependencies { } block:");
        hint.setPadding(0, 0, 0, pad8);
        root.addView(hint);

        EditText etInput = new EditText(ctx);
        etInput.setHint("implementation 'com.example:library:1.0.0'");
        etInput.setMinLines(4);
        etInput.setMaxLines(10);
        etInput.setGravity(android.view.Gravity.TOP);
        root.addView(etInput);

        MaterialButton btnParse = new MaterialButton(ctx);
        btnParse.setText("Parse");
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = pad8;
        root.addView(btnParse, btnParams);

        // Results area
        TextView tvEmpty = new TextView(ctx);
        tvEmpty.setText("No dependencies found. Try pasting a valid build.gradle block.");
        tvEmpty.setVisibility(View.GONE);
        tvEmpty.setPadding(0, pad8, 0, 0);
        root.addView(tvEmpty);

        ScrollView scroll = new ScrollView(ctx);
        scroll.setVisibility(View.GONE);
        LinearLayout checkList = new LinearLayout(ctx);
        checkList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(checkList);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(200 * dp));
        scrollParams.topMargin = pad8;
        root.addView(scroll, scrollParams);

        final List<ParsedDep> parsed = new ArrayList<>();

        btnParse.setOnClickListener(v -> {
            String text = etInput.getText().toString();
            parsed.clear();
            checkList.removeAllViews();
            parsed.addAll(parseDependencies(text));

            if (parsed.isEmpty()) {
                tvEmpty.setVisibility(View.VISIBLE);
                scroll.setVisibility(View.GONE);
            } else {
                tvEmpty.setVisibility(View.GONE);
                scroll.setVisibility(View.VISIBLE);
                for (ParsedDep dep : parsed) {
                    CheckBox cb = new CheckBox(ctx);
                    cb.setText(dep.display + "\n" + dep.coordinate);
                    cb.setChecked(true);
                    cb.setPadding(0, pad8 / 2, 0, pad8 / 2);
                    cb.setOnCheckedChangeListener((btn, checked) -> dep.selected = checked);
                    checkList.addView(cb);
                }
            }
        });

        return new MaterialAlertDialogBuilder(ctx)
                .setTitle("Import from build.gradle")
                .setView(root)
                .setPositiveButton("Download Selected", (d, w) -> {
                    List<String> selected = new ArrayList<>();
                    for (ParsedDep dep : parsed) {
                        if (dep.selected) selected.add(dep.coordinate);
                    }
                    if (listener != null && !selected.isEmpty()) {
                        listener.onImportConfirmed(selected);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    static List<ParsedDep> parseDependencies(String gradle) {
        List<ParsedDep> result = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        addMatches(IMPL_QUOTED, gradle, seen);
        addMatches(IMPL_KTS, gradle, seen);
        for (String coord : seen) {
            if (coord.startsWith(":") || !coord.contains(":")) continue;
            result.add(new ParsedDep(coord));
        }
        return result;
    }

    private static void addMatches(Pattern p, String text, java.util.Set<String> out) {
        Matcher m = p.matcher(text);
        while (m.find()) out.add(m.group(1).trim());
    }
}
