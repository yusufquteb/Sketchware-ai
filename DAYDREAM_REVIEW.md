# مراجعة نسختنا مقابل Sketchware-DayDream (extensions/anbui/daydream)

مقارنة معمّقة لقسم `extensions/anbui/daydream` (93 ملف، ~9800 سطر) من
[AnBui2004/Sketchware-DayDream](https://github.com/AnBui2004/Sketchware-DayDream)
مقابل الأكواد المشتقة منه في نسختنا.

## الخلاصة التنفيذية
نسختنا **أفضل فعلياً** في نواة Git (fallback للفروع، hard-reset، refspec صريح، timeouts على
الشبكة) وفي معالجة الأخطاء (`GitResult` بدل `boolean`) وفي إدارة الخيوط (`ExecutorService` بدل
21 موضع `new Thread()` خام بلا وعي بدورة حياة الـ Activity في DayDream). لكن اكتُشفت **فجوة
وظيفية حقيقية واحدة عالية الأثر** أُصلحت في هذه الجلسة، وعدة فرص تحسين مؤجلة.

## ✅ أُصلح في هذه الجلسة — نسخة احتياطية ناقصة في شاشة Project Lifecycle
`ProjectLifecycleActivity` (شاشة مسجّلة ومتاحة من Project Tools Hub) كانت تستخدم
`ProjectBackupCore`/`ProjectRestoreCore` اللذين **يضغطان/يفكّان مجلد `.sketchware/data/{scId}`
فقط** — أي نسخة احتياطية **ناقصة**: بلا موارد المشروع (خطوط/أيقونات/صور/أصوات)، بلا المكتبات
المحلية، بلا واصف المشروع (project descriptor). استعادة هذه النسخة تُنتج مشروعاً مكسوراً.

المفارقة: التطبيق **عنده بالفعل** نظام نسخ احتياطي كامل وناضج
(`mod/hey/studios/project/backup/BackupRestoreManager` + `BackupFactory`) يجمع كل المسارات
الخمسة الحقيقية، يوفّر خيارات (المكتبات المحلية/الكتل المخصّصة)، تشفير AES، ومنتقي ملفات `.swb`
للاستعادة مع تحذير عند احتواء الأرشيف على `local_libs` — وهو المستخدم فعلاً من قائمة المشاريع.

**الإصلاح**: إعادة توجيه زرّي النسخ/الاستعادة في `ProjectLifecycleActivity` إلى
`BackupRestoreManager`، وحذف `ProjectBackupCore.java` و`ProjectRestoreCore.java` (كانا مستخدمين
من هذه الشاشة فقط). النتيجة: الشاشة تنتج الآن نسخة `.swb` كاملة وقابلة للاستعادة فعلاً.

## فرص تحسين مؤجّلة (موثّقة، تحتاج جلسات مخصّصة)

مرتبة بالأولوية. كلها ميزات أكبر تحتاج تصميم/اختبار منفصل، فلم تُنفّذ في هذه الجولة تفادياً
للنقل الأعمى:

1. **تجهيز/تصفية ملفات قبل دفع Git + إخفاء الأسرار** (DayDream: `git/GitPushUtils.java`،
   `GitApplyUtils.java`). حالياً `GitRepositoryCore.push()` يعمل `git add .` على المجلد كاملاً —
   **قد يسرّب مفاتيح API إلى المستودع البعيد** إن لم تُستبعد يدوياً. DayDream يخفي ملف الأسرار
   و`secrets.xml` قبل الدفع ويتيح "دفع المشروع فقط / الكود المُصدَّر فقط". **أعلى أولوية أمنية.**

2. **Quick Look — معاينة مستودع GitHub دون استيراد كامل** (DayDream: `git/GitQuickLook.kt`).
   ملفنا `pro/sketchware/git/GitQuickLook.java` يحمل نفس الاسم لكن يفعل شيئاً مختلفاً تماماً
   (مجرد `recentCommits()`). ميزة UX غير موجودة فعلياً رغم تطابق الاسم المضلِّل.

3. **سحب وإفلات ملف نسخة احتياطية لاستعادته** (DayDream: `project/RestoreProject.java`) — تحسين
   UX منخفض الجهد.

4. **فحص تعارض تلقائي (checkDiff) عند فتح مشروع مربوط بـ Git** (DayDream:
   `git/DayDreamGitTools.checkDiff`) — يمنع تعارضات صامتة في العمل الجماعي. الآلية
   (`GitRepositoryCore.hasRemoteChanges`) موجودة عندنا لكن تُستدعى فقط من شاشات الدفع اليدوية.

5. **مفاتيح إعدادات ناقصة**: Content protection (`FLAG_SECURE`)، تعطيل طلب الأذونات التلقائي،
   Retrofit2 toggle. النمط جاهز (`LibraryExtrasSettings` + `DaydreamToolsSection`) — تحتاج فقط
   ربطاً فعلياً بتوليد المانفست/الكود ليكون لها أثر.

6. **مولّدات كود Kotlin** (DayDream: `java/generator/*.kt` — Firebase/ListView/ArrayList/
   ObjectAnimator/View) و`DRManifestManager`/`DRGradleManager` — طبقات غير موجودة عندنا،
   ميزات جديدة كبيرة.

## ما هو أفضل عندنا بالفعل (لا يُنقل)
- **Git core**: `GitRepositoryCore` أقوى من `GitFeaturesCore` (fallback فروع، hard-reset،
  refspec، timeouts).
- **معالجة الأخطاء**: `GitResult.ok/fail(message, exception)` مقابل `boolean`+`Log.e` المسطّح.
- **الخيوط**: `ExecutorService` مقابل `new Thread()` خام في 21 موضعاً بلا فحص
  `isFinishing()`/`isDestroyed()` في DayDream.
- **إعدادات المكتبات الإضافية**: مربوطة بمصدر بيانات أنظف (`LibraryExtrasSettings`) بدل ملف
  `DataDayDream.json` منفصل.
