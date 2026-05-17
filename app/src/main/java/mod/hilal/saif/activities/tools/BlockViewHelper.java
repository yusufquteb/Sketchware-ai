package mod.hilal.saif.activities.tools;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.Base64;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.besome.sketch.beans.BlockBean;
import pro.sketchware.R;

import a.a.a.kq;

/**
 * BlockViewHelper — النسخة النهائية
 * ═══════════════════════════════════
 *
 * يعتمد بالكامل على النظام الموجود في المشروع:
 *
 * 1. الأشكال — من الـ 9-patch PNGs الأصلية الموجودة في drawable-xhdpi:
 *    ┌─────────────────────────┬────────────────────────┐
 *    │ BlockBean.type          │ Drawable               │
 *    ├─────────────────────────┼────────────────────────┤
 *    │ " " أو "regular"        │ R.drawable.block_ori   │  ← Command (notch فوق + bump تحت)
 *    │ "b"                     │ R.drawable.block_boolean│ ← Boolean (سداسي مدبب)
 *    │ "c" أو "e"              │ R.drawable.if_else     │  ← C-Shape / Event (قبعة)
 *    │ "d"                     │ R.drawable.block_num   │  ← Number reporter (بيضاوي)
 *    │ "f"                     │ R.drawable.block_stop  │  ← Stop/Break (بدون bump تحت)
 *    │ "s" أو أي شيء آخر      │ R.drawable.block_string│  ← String (مستطيل مستدير)
 *    └─────────────────────────┴────────────────────────┘
 *
 * 2. الألوان — من kq.a(context, opCode, blockType) الموجودة في kq.java:
 *    يُطبَّق اللون بـ PorterDuffColorFilter(color, MULTIPLY) على الـ 9-patch
 *    بنفس الطريقة المستخدمة في BlocksManagerDetailsActivity وLogicEditorActivity
 *
 * 3. الـ Base64 decoder لأسماء البلوكات المشفّرة
 *
 * الاستخدام في الـ Adapter (onBindViewHolder):
 *   BlockViewHelper.applyTo(context, specTextView, blockBean);
 *
 * ملاحظة: لا يلزم إنشاء أي Custom View أو 9-patch جديد.
 * جميع الـ drawables موجودة في res/drawable-xhdpi.
 */
public class BlockViewHelper {

    /**
     * يُطبّق الشكل الصحيح واللون الصحيح على TextView المسؤول عن عرض البلوك.
     *
     * @param context  Context
     * @param specView الـ TextView الذي يحمل spec البلوك (له background من 9-patch)
     * @param block    بيانات البلوك
     */
    public static void applyTo(@NonNull Context context,
                                @NonNull TextView specView,
                                @NonNull BlockBean block) {
        // 1. تعيين الـ 9-patch المناسب
        int drawableRes = getDrawableFor(block.type);
        specView.setBackgroundResource(drawableRes);

        // 2. الحصول على اللون من kq.java (نفس الطريقة الأصلية)
        int color = kq.a(context, block.opCode != null ? block.opCode : "", block.type != null ? block.type : " ");

        // 3. تطبيق اللون بـ PorterDuff MULTIPLY (نفس الطريقة الأصلية)
        specView.getBackground().setColorFilter(
                new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
        );

        // 4. تعيين نص البلوك (مع فك Base64 إذا لزم)
        if (block.spec != null && !block.spec.isEmpty()) {
            specView.setText(block.spec);
        } else if (block.opCode != null) {
            specView.setText(decodeBlockName(block.opCode));
        }
    }

    /**
     * يُعيد رقم الـ drawable بناءً على BlockBean.type.
     * مبني مباشرةً على switch الموجود في BlocksManagerDetailsActivity.java.
     */
    @DrawableRes
    public static int getDrawableFor(String blockType) {
        if (blockType == null) return R.drawable.block_ori;
        switch (blockType) {
            case " ":
            case "regular":
                return R.drawable.block_ori;       // Command — notch فوق + bump تحت

            case "b":
                return R.drawable.block_boolean;   // Boolean — سداسي مدبب الطرفين

            case "c":
            case "e":
                return R.drawable.if_else;         // C-Shape (if/repeat) أو Event (hat)

            case "d":
                return R.drawable.block_num;       // Number reporter — بيضاوي ممدود

            case "f":
                return R.drawable.block_stop;      // Stop/Break — بدون bump سفلي

            default:
                return R.drawable.block_string;    // String / fallback — مستطيل مستدير
        }
    }

    /**
     * يُعيد اللون بناءً على opCode و type.
     * مجرد wrapper على kq.a() لتسهيل الاستخدام من مكان واحد.
     */
    public static int getColorFor(@NonNull Context context,
                                   String opCode, String blockType) {
        return kq.a(
                context,
                opCode != null ? opCode : "",
                blockType != null ? blockType : " "
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Base64 decoder لأسماء البلوكات المشفرة
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * يفك تشفير Base64 من اسم البلوك إن وُجد.
     * مثال: "T24gYWN0aXZpdHkgY3JlYXRl" → "On activity create"
     *
     * إذا لم يكن Base64 صحيحاً، يُعيد الاسم كـ camelCase → Title Case.
     */
    public static String decodeBlockName(String rawName) {
        if (rawName == null || rawName.isEmpty()) return "";

        // Base64 strings: طويلة، تحتوي حروف/أرقام/+ و /، وقد تنتهي بـ =
        if (rawName.length() > 8 && rawName.matches("[A-Za-z0-9+/]+=*")) {
            try {
                byte[] decoded = Base64.decode(rawName, Base64.DEFAULT);
                String result = new String(decoded, "UTF-8").trim();
                if (isPrintable(result) && !result.isEmpty()) {
                    return result;
                }
            } catch (Exception ignored) {}
        }

        // تحويل camelCase → Title Case
        // "setTextColor" → "Set Text Color"
        return camelToLabel(rawName);
    }

    private static boolean isPrintable(String s) {
        for (char c : s.toCharArray()) {
            if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') return false;
        }
        return true;
    }

    private static String camelToLabel(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(Character.toUpperCase(s.charAt(0)));
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) sb.append(' ');
            sb.append(c);
        }
        return sb.toString();
    }
}
