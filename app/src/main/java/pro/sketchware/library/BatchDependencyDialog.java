package pro.sketchware.library;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BatchDependencyDialog
 * ═════════════════════
 * يعرض كل المكتبات الناقصة دفعة واحدة في dialog واحد بدلاً من
 * dialogs متتالية. يمنح المستخدم خيارات:
 *   • إصلاح الكل (تطبيق كل البدائل المتاحة)
 *   • فتح المشروع على أي حال (تجاهل الكل)
 *   • إلغاء
 *
 * كل مكتبة في القائمة تظهر مع:
 *   - اسمها المطلوب
 *   - البديل المتاح (إن وُجد) مع تصنيف التغيير
 *   - زر Skip فردي
 */
public class BatchDependencyDialog {

    private final Context context;
    private final String scId;
    private final List<LibraryConflictChecker.Conflict> conflicts;
    /** خريطة القرارات لكل conflict: true = تطبيق البديل، false = تجاهل */
    private final Map<Integer, Boolean> userChoices = new LinkedHashMap<>();

    public interface OnCompleteListener {
        /** يُستدعى عند اختيار المستخدم فتح المشروع (بعد تطبيق ما اختاره) */
        void onComplete();
        /** يُستدعى عند إلغاء العملية كلياً */
        void onCancel();
    }

    public BatchDependencyDialog(Context context, String scId,
                                  List<LibraryConflictChecker.Conflict> conflicts) {
        this.context   = context;
        this.scId      = scId;
        this.conflicts = new ArrayList<>(conflicts);
        // بالافتراضي: تطبيق كل البدائل المتاحة
        for (int i = 0; i < conflicts.size(); i++) {
            userChoices.put(i, conflicts.get(i).hasSubstitute());
        }
    }

    public void show(OnCompleteListener listener) {
        // بناء RecyclerView للعرض
        RecyclerView rv = new RecyclerView(context);
        rv.setLayoutManager(new LinearLayoutManager(context));
        rv.setAdapter(new ConflictListAdapter());
        rv.setPadding(0, 8, 0, 8);

        String title = "مكتبات ناقصة (" + conflicts.size() + ")";

        new MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(rv)
            .setPositiveButton("✅ إصلاح المحدد وفتح", (d, w) -> {
                applySelectedChoices();
                listener.onComplete();
            })
            .setNeutralButton("⚠️ فتح بدون إصلاح", (d, w) -> listener.onComplete())
            .setNegativeButton("إلغاء", (d, w) -> listener.onCancel())
            .setCancelable(false)
            .show();
    }

    private void applySelectedChoices() {
        for (int i = 0; i < conflicts.size(); i++) {
            Boolean apply = userChoices.get(i);
            if (apply != null && apply && conflicts.get(i).hasSubstitute()) {
                try {
                    LibraryConflictChecker.applyResolution(scId, conflicts.get(i));
                } catch (Exception ignored) {}
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Adapter داخلي لعرض قائمة المكتبات
    // ─────────────────────────────────────────────────────────────────────────

    private class ConflictListAdapter
            extends RecyclerView.Adapter<ConflictListAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // بناء layout برمجياً لتجنب الحاجة إلى XML layout file خارجي
            View item = buildItemView(parent.getContext());
            return new VH(item);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            LibraryConflictChecker.Conflict c = conflicts.get(position);

            holder.tvIndex.setText((position + 1) + "/" + conflicts.size());
            holder.tvRequired.setText("📦  " + c.requiredName);

            if (c.hasSubstitute()) {
                holder.tvStatus.setText("✅ بديل متاح: " + c.availableName);
                holder.tvStatus.setTextColor(0xFF3FB950);
                holder.chipChange.setText(c.describeChange());
                holder.chipChange.setVisibility(View.VISIBLE);

                Boolean currentChoice = userChoices.get(position);
                holder.btnToggle.setText(
                    Boolean.TRUE.equals(currentChoice) ? "تطبيق ✔" : "تجاهل ⏭");

                holder.btnToggle.setOnClickListener(v -> {
                    Boolean cur = userChoices.get(position);
                    boolean next = !Boolean.TRUE.equals(cur);
                    userChoices.put(position, next);
                    holder.btnToggle.setText(next ? "تطبيق ✔" : "تجاهل ⏭");
                });
            } else {
                holder.tvStatus.setText("❌ لا يوجد بديل على الجهاز");
                holder.tvStatus.setTextColor(0xFFF85149);
                holder.chipChange.setVisibility(View.GONE);
                holder.btnToggle.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() { return conflicts.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvIndex, tvRequired, tvStatus;
            Chip chipChange;
            MaterialButton btnToggle;

            VH(View item) {
                super(item);
                tvIndex    = item.findViewWithTag("tvIndex");
                tvRequired = item.findViewWithTag("tvRequired");
                tvStatus   = item.findViewWithTag("tvStatus");
                chipChange = item.findViewWithTag("chipChange");
                btnToggle  = item.findViewWithTag("btnToggle");
            }
        }

        /**
         * بناء layout كل item برمجياً.
         * الهيكل:
         *   LinearLayout (vertical, padding 12dp)
         *     ├ TextView  [index]      — "1/4"
         *     ├ TextView  [required]   — "📦 okhttp3:okhttp"
         *     ├ TextView  [status]     — "✅ بديل متاح: ..."
         *     ├ Chip      [change]     — "v3.12 → v4.9"
         *     └ MaterialButton [toggle]— "تطبيق ✔" / "تجاهل ⏭"
         */
        private View buildItemView(Context ctx) {
            int dp4  = dp(ctx, 4);
            int dp8  = dp(ctx, 8);
            int dp12 = dp(ctx, 12);
            int dp16 = dp(ctx, 16);

            android.widget.LinearLayout root = new android.widget.LinearLayout(ctx);
            root.setOrientation(android.widget.LinearLayout.VERTICAL);
            root.setPadding(dp16, dp12, dp16, dp8);

            // Divider line at top (except first)
            View divider = new View(ctx);
            divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0x1FFFFFFF);
            root.addView(divider);

            TextView tvIndex = new TextView(ctx);
            tvIndex.setTag("tvIndex");
            tvIndex.setTextSize(10f);
            tvIndex.setTextColor(0xFF8B949E);
            tvIndex.setPadding(0, dp4, 0, dp4);
            root.addView(tvIndex);

            TextView tvRequired = new TextView(ctx);
            tvRequired.setTag("tvRequired");
            tvRequired.setTextSize(13f);
            tvRequired.setTextColor(0xFFE6EDF3);
            tvRequired.setTypeface(android.graphics.Typeface.MONOSPACE);
            tvRequired.setPadding(0, 0, 0, dp4);
            root.addView(tvRequired);

            TextView tvStatus = new TextView(ctx);
            tvStatus.setTag("tvStatus");
            tvStatus.setTextSize(12f);
            tvStatus.setPadding(0, 0, 0, dp4);
            root.addView(tvStatus);

            Chip chipChange = new Chip(ctx);
            chipChange.setTag("chipChange");
            chipChange.setTextSize(11f);
            chipChange.setChipBackgroundColorResource(
                com.google.android.material.R.color.m3_chip_background_color);
            root.addView(chipChange);

            MaterialButton btnToggle = new MaterialButton(ctx,
                null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btnToggle.setTag("btnToggle");
            btnToggle.setTextSize(12f);
            android.widget.LinearLayout.LayoutParams lp =
                new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp4;
            btnToggle.setLayoutParams(lp);
            root.addView(btnToggle);

            return root;
        }

        private int dp(Context ctx, int value) {
            return Math.round(value * ctx.getResources().getDisplayMetrics().density);
        }
    }
}
