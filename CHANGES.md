# Sketchware-ai — Phase 1 — جلسة التحقق والتصحيح (2026-07-13)

هذا الأرشيف يحوي فقط الملف الذي تغيّر فعليًا في هذه الجلسة. انسخه فوق نسختك
الحالية (استبدال) ثم أعد البناء.

---

## البند 2 — تصحيح: فلترة NOTE لم تكن تعمل رغم توثيقها

**الملف:** `app/src/main/java/pro/sketchware/utility/diagnostics/CompileErrorCapture.java`

**المشكلة المكتشفة:** الباتش السابق (البند 2 الأصلي) وثّق ونفّذ `filterErrorsOnly()`
على أساس أنها تحذف أسطر WARNING **و** NOTE من حمولة الذكاء الاصطناعي. تم التحقق
فعليًا (باختبار نمط الـ Regex مباشرة، وليس بالقراءة فقط) أن `CompileErrorCapture
.parse()` الأصلية كانت تتعرّف فقط على `error:` و`warning:` — ولا يوجد أي نمط
يتعرّف على أسطر `Note:` الحقيقية الصادرة من `javac` (بصيغتيها: المرتبطة بموقع
`file:line: Note: ...`، والمجردة `Note: Recompile with -Xlint...`). نتيجة ذلك:
أسطر NOTE كانت تُعتبر "غير مُتعرَّف عليها" فتُبقى كما هي في حمولة الذكاء الاصطناعي
بدل حذفها — عكس ما وصفه التوثيق تمامًا.

**التعديل:** إضافة نمطين جديدين في `CompileErrorCapture.parse()`:
- `JAVAC_NOTE`: يطابق أسطر NOTE المرتبطة بموقع (`file:line[:col]: Note: msg`).
- `BARE_NOTE`: يطابق أسطر NOTE المجردة بلا موقع (`Note: msg`).

كلاهما يُصنَّف الآن كـ `CompileDiagnostic.Severity.NOTE` (كانت هذه القيمة موجودة
في الـ enum مسبقًا وغير مُستخدَمة إطلاقًا — دليل إضافي على أن الدعم كان مخطَّطًا
له ولم يُستكمل). لا تغيير على `filterErrorsOnly()` نفسها في `CompileLogActivity`
— كانت صحيحة بالفعل، المشكلة كانت فقط في عدم التعرّف على أسطر NOTE من الأساس.

**التحقق:** تم اختبار الأنماط الأربعة (JAVAC, JAVAC_NOTE, KOTLIN, BARE_NOTE) ضد
أمثلة حقيقية من صيغ `javac`/`kotlinc` القياسية؛ كل حالة (error, warning, note
بشكلَيها، سطر ملخّص غير-diagnostic مثل "1 error") سلكت المسار المتوقَّع بدقة،
بدون أي تعارض بين الأنماط الأربعة.

**لم يُختبَر بعد:** تصريف Gradle فعلي، أو تشغيل حقيقي على الجهاز يولّد سجل
NOTE حقيقي من مشروع مستخدم فعلي. يُنصَح بتجربة سيناريو بناء لمشروع فيه تحذيرات
`-Xlint:unchecked` (السبب الأكثر شيوعًا لظهور NOTE من javac) والتأكد أن حمولة
الذكاء الاصطناعي (Error Log → Analyse/Fix tools) لم تعد تحوي أسطر Note.

---

## البند 3 — تأكيد: `buildToolPrompt()` سليم، لا تعديل مطلوب

**الملف:** `app/src/main/java/pro/sketchware/ai/bottomsheet/AiProjectBottomSheet.java`
(غير مُضمَّن هنا — لا تعديل عليه)

تمت مراجعة `buildToolPrompt()` سطرًا بسطر والتأكد أنها فعليًا (لا التوثيق فقط)
تعديل نص/بيانات خالص عبر `switch` على أسماء الأدوات، بلا أي لمسة لمنطق
الـ View/drag/keyboard الحساس الموثَّق في تعليق `DIAG_TAG` (البند 6، ملف مختلف
تمامًا من ناحية المخاطر). لا تغيير مطلوب.

---

## البند 5 (Notifications) — تشخيص كامل: لا عيب في الكود

**الملف:** `app/src/main/java/pro/sketchware/activities/settings/PermissionsActivity.java`
(غير مُضمَّن هنا — لا تعديل عليه، رجع مطابقًا تمامًا للأصل بعد إزالة Log تشخيصي مؤقت)

**البلاغ الأصلي:** الضغط على صف Notifications في onboarding لا ينتج عنه أي رد فعل
مرئي (لا ديالوج نظام، لا شاشة Settings)، رغم أن حالة الإذن في إعدادات النظام تبقى
"لم تُطلب بعد" (not requested).

**التحقق المُنجَز:**
- Logcat مع Log تشخيصي مؤقت أثبت أن الحدث يصل للـ View، والدالة الصحيحة تُستدعى،
  و`requestPermissions(POST_NOTIFICATIONS)` تُستدعى فعليًا وترجع بنجاح خلال أقل
  من 100ms، بدون أي استثناء.
- تم استبعاد: مشكلة إقلاع/ANR (التطبيق كان مستقرًا تمامًا وقت الضغطة، باقي
  الصفوف تستجيب طبيعيًا)، تثبيت فوق نسخة قديمة (uninstall كامل تم)، صلاحية غير
  معلَنة في المانيفست (معلَنة بشكل صحيح)، رفض سابق يمنع إعادة إظهار الديالوج
  (حالة النظام "not requested"، وليست "denied").

**الخلاصة:** الكود يستدعي `requestPermissions()` بشكل صحيح تمامًا وفق كل الشروط
المطلوبة. عدم ظهور ديالوج النظام رغم ذلك هو سلوك خاص بـ `PermissionController`
على بيئة التشغيل (تم تكراره في onboarding تحديدًا)، وليس خللًا قابلًا للإصلاح من
داخل `PermissionsActivity.java`. لا تعديل كود مبرَّر لهذا البند.

---

## البنود 4، 6، 7

تم تنفيذها والتحقق منها في جلسات سابقة (خارج نطاق هذه الجلسة).

---

## جلسة إضافية — إضافة Drag Handle مرئي لـ AI BottomSheet

**الملف المعدَّل:** `app/src/main/res/layout/design_ai_bottom_sheet.xml`
(هذا الـ layout هو الجذر الذي يُنفَّخ فعليًا داخل
`AiProjectBottomSheet.attachToParent()` عبر
`LayoutInflater.from(context).inflate(R.layout.design_ai_bottom_sheet, ...)`
— تحقَّقت من ذلك في `AiProjectBottomSheet.java` قبل التعديل، ولم أفترض ذلك من
التوثيق فقط.)

### الوضع قبل التعديل
`ai_sheet_handle_row` كان `LinearLayout` أفقيًا واحدًا يحوي مباشرة: pill 1
(ملف/نشاط)، pill 2 (المزوّد/الموديل)، pill 3 (إعدادات/مسح/إغلاق)، وزر
`ai_btn_undo_layout`. لم يكن هناك أي `View` مخصَّص لشريط سحب مرئي (drag
handle) — رغم أن `AiProjectBottomSheet.setupDragAndSwipe()` يستخدم هذا الصف
بالكامل (`sheetRoot.findViewById(R.id.ai_sheet_handle_row)`) كمنطقة استقبال
للسحب والـ tap-to-toggle، بلا أي إشارة بصرية توضّح للمستخدم أن الصف قابل
للسحب.

### التعديل بالضبط
1. حُوِّل `ai_sheet_handle_row` من `orientation="horizontal"` إلى
   `orientation="vertical"`، مع الإبقاء على نفس الـ `id` بلا أي تغيير (حتى لا
   ينكسر أي استدعاء `findViewById` في الجافا).
2. أُضيف كأول عنصر داخله `View` جديد بمعرّف `ai_sheet_drag_handle`
   (32dp × 4dp، توسيط أفقي، خلفية `@drawable/bg_bottom_sheet_handle`) —
   استُخدم Drawable **موجود بالفعل** في المشروع (نفس المستخدَم في
   `bottom_sheet_message_actions.xml`)، فلم يُنشَأ Drawable جديد.
3. الصف الأفقي الأصلي (الـ 3 pills + زر undo) أُبقي **بلا أي تغيير في بنيته
   الداخلية أو ترتيب عناصره**، وفُقِّف فقط داخل `LinearLayout` أفقي جديد أصبح
   الابن الثاني لـ `ai_sheet_handle_row` (تحت شريط السحب مباشرة).

### لماذا يحل المطلوب
يعطي إشارة بصرية واضحة (شريط رمادي في المنتصف) مطابقة لنمط bottom sheets
القياسي في Material Design، متسقة مع الصورة المرجعية المطلوبة، بدون المساس
بمنطق السحب/الإفلات أو أي معرّف View يعتمد عليه الكود القائم.

### تحقق تم إجراؤه فعليًا (لا افتراض)
- قارنتُ كل معرّفات `R.id.*` المستخدَمة في `AiProjectBottomSheet.java` مقابل
  كل `android:id` الموجودة فعليًا في الملف بعد التعديل (عبر سكربت مقارنة) —
  **لا يوجد أي معرّف ناقص**.
- تحقَّقت من صحة تركيب XML (`xml.etree.ElementTree.parse`) بعد كل خطوة تعديل
  — لا أخطاء إغلاق وسوم.
- راجعتُ يدويًا أن `ai_btn_undo_layout` بقي **ابنًا مباشرًا للصف الأفقي
  الداخلي** (وليس للـ `ai_sheet_handle_row` الخارجي الجديد) — لو بقي سيبًا
  للحاوية الخارجية لظهر كصف منفصل تحت الـ pills بدل أن يكون ملاصقًا لهم أفقيًا
  كما في التصميم الأصلي.

### ما يحتاج اختبار فعلي على الجهاز (لم يُختبَر بعد)
- **الشكل البصري:** ظهور شريط السحب في الأعلى بنفس موضع/حجم الصورة المرجعية،
  ومطابقته للون `?attr/colorOutlineVariant` في الثيمين الفاتح والداكن.
- **السحب (Swipe):** أن `setupDragAndSwipe()` (fling للأعلى/الأسفل، ومنطق
  `toggle()` عند tap) ما زال يعمل بلا تغيير — التعديل لم يمسّ منطق الجافا، لكن
  تغيير الـ orientation قد يُغيّر ارتفاع الصف الكلي (`handleRow.getHeight()`
  إن استُخدم في أي حساب موضعي)، وهذا **لم يُتحقَّق منه على جهاز فعلي**.
- **محاذاة الصف الأفقي:** أن الـ 3 pills وزر `ai_btn_undo_layout` ما زالوا
  مصفوفين أفقيًا بنفس الشكل السابق تمامًا (بدون أي انزياح أو التفاف غير
  متوقَّع) بعد إضافة مستوى التعشيش الجديد.

---

## جلسة إضافية 2 — إصلاح عدم ظهور Drag Handle (تباين لوني)

**البلاغ:** بعد بناء APK فعلي من نفس ملفات الجلسة السابقة، شريط السحب
المُضاف لم يظهر بصريًا على الجهاز، رغم وجوده في الـ XML.

### التشخيص (تم فعليًا، لا افتراض)
راجعت قيم الثيم الفعلية في `values-night/themes_accent.xml`:
- `colorSurface` (خلفية الصف): قيم مثل `#0F172A` / `#111827` — غامقة جدًا.
- `colorOutlineVariant` (اللون المستخدَم سابقًا لشريط السحب عبر
  `bg_bottom_sheet_handle.xml` المشترك): قيم مثل `#3C4847`–`#4E4439` —
  قريبة جدًا من لون الخلفية الغامقة، فالتباين ضعيف والشريط (4dp ارتفاع)
  يصبح غير مُلاحَظ عمليًا على شاشة موبايل.

بالمقارنة، `colorOnSurfaceVariant` في نفس الثيم الغامق: قيم مثل
`#BCC8C8`–`#D2C4B7` (فاتحة جدًا) — تباين قوي وواضح فوق `colorSurface` الغامق.

### الملفات المعدَّلة
1. **جديد:** `app/src/main/res/drawable/bg_ai_sheet_drag_handle.xml`
   — drawable مخصَّص (وليس تعديلًا على `bg_bottom_sheet_handle.xml`
   المشترك، لتفادي التأثير على `dialog_ai_ui_generator.xml`،
   `dialog_keystore_credentials.xml`، و`bottom_sheet_message_actions.xml`
   التي تستخدمه أيضًا). نفس الشكل (`corners radius=2dp`) لكن اللون
   `?attr/colorOnSurfaceVariant` بدل `?attr/colorOutlineVariant`.
2. **معدَّل:** `app/src/main/res/layout/design_ai_bottom_sheet.xml`
   — الـ `View` بمعرّف `ai_sheet_drag_handle` أصبح يشير إلى
   `@drawable/bg_ai_sheet_drag_handle` بدل `@drawable/bg_bottom_sheet_handle`،
   مع زيادة العرض من 32dp إلى 36dp لمزيد من الوضوح البصري. لا تغيير على
   الـ `id` أو موضع العنصر في الشجرة.

### ملاحظة توضيحية (خارج نطاق هذا الإصلاح)
البلاغ الثاني في نفس الرسالة (اختفاء أجزاء من نص أزرار "Generate UI" /
"Add feature") تبيَّن أنه ناتج عن فتح الـ drawer الجانبي فيضيّق المساحة
المتاحة، وليس عيبًا في التصميم نفسه — تم توضيحه من صاحب البلاغ، فلا حاجة
لتعديل كود بخصوصه في هذه الجلسة.

### تحقق تم إجراؤه فعليًا
- قارنت القيم الست للثيمات المتعددة (`values-night/themes_accent.xml`)
  لكل من `colorSurface`، `colorOutlineVariant`، `colorOnSurfaceVariant` —
  الفرق في السطوع بين الخيار الجديد والقديم واضح رقميًا في كل الثيمات
  الستة، وليس فقط الثيم الافتراضي.
- تحقَّقت أن `bg_bottom_sheet_handle.xml` الأصلي لم يُمَس، فالملفات الثلاثة
  الأخرى التي تستخدمه ستبقى بسلوكها الحالي دون أي تأثير جانبي.
- تحقَّقت من صحة تركيب XML لكلا الملفين (`design_ai_bottom_sheet.xml`
  و`bg_ai_sheet_drag_handle.xml`) بعد التعديل.

### يحتاج اختبار فعلي على الجهاز (لم يُختبَر بعد)
- ظهور شريط السحب بوضوح في كل الثيمات المتاحة (الست جميعًا)، وليس فقط
  الثيم الحالي المستخدَم وقت أخذ لقطة الشاشة.
- التأكد أن التباين مقبول بصريًا في الثيم الفاتح أيضًا (`values/themes_accent.xml`
  فيه `colorOnSurfaceVariant` غامق مثل `#41484D`–`#4E4439` فوق
  `colorSurface` فاتح مثل `#F8FAFC`/`#FAFAFA` — تباين متوقَّع جيد، لكن لم
  يُختبَر بصريًا).

---

# Sketchware-ai — Phase 2 + Phase 3 — جلسة التنفيذ المدمجة (2026-07-13)

هذه الجلسة نفّذت طلبين معًا: (أ) طبقة تنسيق فوق نظام الأدوات الحالي
(AgentOrchestrator)، و(ب) أربعة إصلاحات/إضافات محددة اكتُشف أنها فجوات حقيقية
بعد فحص فعلي للكود، وليس افتراضًا من وصف المهمة.

## ⚠ تصحيحات مهمة على افتراضات الطلب الأصلي (اكتُشفت أثناء الفحص الفعلي)

قبل قراءة التفاصيل، هذه النقاط تخالف ما افترضه وصف المهمة، وتم التحقق منها
بقراءة الكود الفعلي وليس تخمينًا:

1. **`AgentExecutor` ليس مجرد "أدوات تُستدعى يدويًا"** — هو بالفعل حلقة agent
   كاملة تقودها الـ LLM (تقرر النموذج أي أداة تستدعي، تُنفَّذ، تُعاد النتيجة له،
   يقرر الخطوة التالية، حتى 200 تكرار كحد أقصى). `AgentOrchestrator` الجديد
   **لا يستبدل ولا يستدعي** هذه الحلقة — هو نمط منفصل "خطّط مرة واحدة ثم نفّذ
   بالترتيب". راجع تعليق الكلاس في `AgentOrchestrator.java` للتفاصيل الكاملة.

2. **`create_project` و`create_activity` موجودان بالفعل ومسجَّلان** — عكس ما
   افترضه البند الأول من برومبت 3 ("لا يوجد مسار إنشاء من الصفر"). الفجوة
   الحقيقية أضيق بكثير: `create_activity` يسجّل الـ `ProjectFileBean` بشكل
   صحيح، لكنه **لا يكتب شيئًا** في أقسام `view`/`logic` المشفَّرة ولا ينشئ ملف
   XML للتخطيط — فالنشاط المُنشأ لا يظهر فعليًا قابلاً للتحرير في محرر
   التصميم. تم إصلاح هذه الفجوة الأضيق فقط (انظر أدناه)، ولم يُعَد بناء أدوات
   موجودة أصلاً.

3. **`org.eclipse.jgit` موجودة بالفعل كـ dependency**، وهناك حزمة كاملة
   `pro.sketchware.git` (GitRepositoryCore, GitPatchApplier, GitQuickLook)
   + شاشة `GitWorkflowActivity` غير متعلقة بالـ AI — كلها لعمليات **Git
   البعيدة** (تتطلب `GitConfig` مع رابط remote). لا يوجد أي غلاف لعمليات Git
   **المحلية البحتة** (init بدون remote، status، add، commit) — وهذه هي
   الفجوة الحقيقية التي عولجت.

4. **`GitHubCompareTool`/`GitHubSearchTool` موجودان بالفعل** ومسجَّلان (في
   `AI_GitHub_Analyzer.java`) — هذه أدوات GitHub API (بحث/مقارنة عن بعد)،
   منفصلة تمامًا عن Git المحلي المطلوب في البند 2 من برومبت 3.

## Phase 2 — AgentOrchestrator (طبقة تنسيق جديدة، لم تُختبَر على جهاز)

**ملفات جديدة:**
- `app/src/main/java/pro/sketchware/ai/orchestrator/PlanStep.java`
- `app/src/main/java/pro/sketchware/ai/orchestrator/ExecutionPlan.java`
- `app/src/main/java/pro/sketchware/ai/orchestrator/AgentOrchestrator.java`

**ماذا يفعل:** يستقبل طلب المستخدم كنص، يرسل استدعاء LLM واحد (غير streaming
من ناحية الاستخدام، لكن عبر نفس `AiApiClient` streaming الموجود — لا يوجد
عميل API متزامن بديل في المشروع) يطلب خطة كاملة كـ JSON، يحلّلها، ثم ينفّذ كل
خطوة بالترتيب عبر استدعاء `ToolRegistry` مباشرة (بنفس التحقق من الـ schema
والـ runtime validation اللذين يطبّقهما `AgentExecutor.executeTool()`).

**فجوة معروفة ومُعلَنة صراحة (لم تُغلَق في هذه الجلسة):** بوابة الموافقة
(`ApprovalManager`) ونظام اللقطات (`ProjectSnapshotManager`) **غير متصلين**
بهذا المسار الجديد، لأنهما يعتمدان على `ApprovalCallback` مرتبط بواجهة
مستخدم، وربط الواجهة كان خارج نطاق هذه المرحلة صراحة. معنى ذلك: خطوة بمستوى
خطورة CRITICAL ستُنفَّذ عبر الـ orchestrator **بدون** طلب موافقة، بخلاف
تنفيذها عبر `AgentExecutor` مع `setApprovalCallback()` مفعَّل. يجب إغلاق هذه
الفجوة قبل ربط `AgentOrchestrator` بأي واجهة مستخدم فعلية.

**لم يُربَط بأي واجهة مستخدم** — هذا خارج نطاق هذه المرحلة كما طلب البرومبت.

## Phase 3 — أربعة إصلاحات

### 1) سد الفجوة الحقيقية في إنشاء نشاط من الصفر

**ملف جديد:** `app/src/main/java/pro/sketchware/ai/tools/project/ActivityContentSeedingTools.java`
**أداة جديدة مسجَّلة:** `seed_blank_activity_content`

يُستدعى بعد `create_activity` مباشرة؛ يكتب قسم `view` فارغًا، قسم `logic`
بحدث `onCreate` فارغ، وملف XML تخطيط فارغ — فقط للأقسام غير الموجودة أصلاً
(آمن للاستدعاء المتكرر، لا يكتب فوق محتوى موجود). راجع تعليق الكلاس للتفاصيل
الكاملة عن كيفية التحقق من الصيغة (`@ActivityName.java_onCreate` من توثيق
`BlockLogicReader`، ونمط `seedDefaultResources()` الموجود في `ProjectTools`).

**⚠ لم يُختبَر فعليًا بفتح محرر التصميم على جهاز حقيقي** — تحقَّق أن النشاط
يظهر ويُحرَّر بشكل صحيح قبل الاعتماد عليه في مشروع حقيقي.

### 2) أدوات Git محلية حقيقية

**ملف جديد:** `app/src/main/java/pro/sketchware/ai/tools/git/LocalGitTools.java`
**أدوات جديدة مسجَّلة:** `git_init`، `git_status`، `git_add`، `git_commit`

تستخدم JGit مباشرة (`org.eclipse.jgit.api.Git`) بنفس النمط المُتحقَّق منه في
`GitWorkflowActivity` الموجودة (نفس أسماء دوال `Status` مثل `getAdded()`،
`getChanged()`، إلخ). لا push/pull/clone/remote — هذه موجودة أصلاً في
`pro.sketchware.git` لمن يحتاج remote.

### 3) ميزانية التوكِن (Token Budget)

**ملف جديد:** `app/src/main/java/pro/sketchware/ai/engine/budget/TokenBudgetChecker.java`
**تعديل:** `AiPreferences.java` — أُضيف مفتاح `ai_max_payload_tokens` مع
`getMaxPayloadTokens()`/`setMaxPayloadTokens()`.

تقدير تقريبي (4 أحرف ≈ توكن واحد — لا يوجد tokenizer حقيقي مضمَّن في
المشروع لأي مزوّد، وإضافة واحد كان خارج النطاق). **لم يُربَط فعليًا بمسار
الإرسال في `AgentExecutor`** — راجع تعليق الكلاس لنقطة الربط الدقيقة
(سطر واحد قبل `sendChatRequest` في حلقة `AgentExecutor.execute()`)، تُركت
للجلسة القادمة عمدًا لتفادي المساس بمنطق حساس كبير الحجم دون إذن صريح إضافي.

### 4) ربط محرك الـ diff بمسار الـ Blocks

**ملف جديد:** `app/src/main/java/pro/sketchware/ai/engine/diff/BlockDiffSupport.java`
**تعديل:** `BlockApiTools.java` — `AddBlockTool`/`ModifyBlockTool`/`DeleteBlockTool`
الثلاثة: أُضيف `getRiskLevel() → MEDIUM` (كانت تفتقد أي override فتُصنَّف
افتراضيًا LOW رغم أنها تُعدِّل ملفات المشروع — خطأ تصنيف حقيقي تم اكتشافه
واصلاحه)، وأُضيف التقاط نص "قبل" و"بعد" وإرفاق ملخص diff بنتيجة كل أداة.
**تعديل إضافي:** `BlockLogicReader.java` — أُضيفت دالة `readDecryptedPublic()`
(غلاف عام لدالة `readDecrypted()` الموجودة أصلاً package-private، بدون أي
تغيير في المنطق) لأن `BlockDiffSupport` في حزمة مختلفة.

**⚠ هذا ليس بوابة موافقة قبل التنفيذ** — لأن `BlockLogicWriter.write()` يكتب
على القرص فورًا داخليًا بدون أي طريقة مكشوفة لحساب المحتوى الجديد دون حفظه.
فالـ diff يُحسَب **بعد** التنفيذ فعليًا ويُرفَق كملخص "ماذا حدث"، وليس تأكيدًا
مسبقًا "هل تريد أن يحدث هذا؟". راجع تعليق الكلاس للتفاصيل الكاملة ولماذا
هذا قيد حقيقي في التصميم الحالي وليس اختصارًا.

## تحقق تم إجراؤه فعليًا (لكل التغييرات أعلاه)
- فُحص الكود المرفوع فعليًا (3353 ملف، 120 م.ب.) قبل كتابة أي سطر — لم يُفترَض
  شيء من وصف البرومبتين دون تحقق.
- تمت مطابقة كل توقيع دالة استُخدم (constructors، static methods، إلخ) مقابل
  الكود الفعلي المرفوع، سطرًا بسطر، شاملة: `ProjectFileBean`، `jC`،
  `SketchwareFileDecryptor`/`Encryptor`، `FilePathUtil`، `FileUtil`،
  `AiApiClient`، `StreamingResponseHandler`، `ToolContext`، `ToolRegistry`،
  `ToolCallValidator`، `RuntimeToolValidator`، `DiffEngine`، `Status` (JGit).
- تم اكتشاف وتصحيح خطأ ارتكبته أثناء هذه الجلسة نفسها: كتبتُ أداة كاملة
  (`ActivityCreationTools.CreateActivityFromScratchTool`) قبل أن أكتشف أن
  `ActivityTools.CreateActivityTool` تقوم بنفس العمل الجوهري أصلاً — تم حذف
  الملف المكرر واستبداله بالإصلاح الأضيق الصحيح (`ActivityContentSeedingTools`).
  مذكور هنا بشفافية.
- تحقَّقت من عدم وجود تعارض بين أسماء الأدوات الجديدة والموجودة (لا تكرار).
  ملاحظة جانبية غير متعلقة بهذه الجلسة: يوجد تكرار مسبق لاسم `scan_dependencies`
  بين `LibraryDiscoveryTools.java` و`DevTools.java` — موجود من قبل، لم يُصلَح
  هنا (خارج النطاق).

## يحتاج اختبار فعلي على الجهاز (لم يُختبَر بعد — لا بيئة بناء متاحة في هذه الجلسة)
- `seed_blank_activity_content`: فتح محرر التصميم/المنطق على نشاط جديد
  للتأكد أنه يظهر ويُحرَّر بشكل صحيح.
- `git_init`/`git_status`/`git_add`/`git_commit`: تشغيل فعلي على مشروع حقيقي،
  والتأكد أن `.git` لا يتعارض مع أي عملية بناء (gitignore قد يحتاج تحديث
  ليستثني ملفات البناء).
- `AgentOrchestrator`: لم يُختبَر استدعاء LLM فعلي لتوليد خطة، ولا تنفيذ خطة
  متعددة الخطوات فعليًا.
- `BlockDiffSupport`: التأكد أن ملخص الـ diff المُرفَق بنتيجة `add_block`/
  `modify_block`/`delete_block` يظهر بشكل مقروء في واجهة الدردشة.

---

# Sketchware-ai — إصلاح خطأ readAllBytes() على API 29 (2026-07-14)

## السبب الحقيقي (مؤكَّد بقراءة الكود الفعلي + مصدر r8 الرسمي)

`DexCompiler.java` (المسار اللي بيبني مشروع **المستخدم** — منفصل تمامًا عن Gradle
build الخاص بتطبيق Sketchware-ai نفسه) كان بيستدعي D8 مباشرة بدون أي تكوين
desugared-library خالص. الفحص الكامل أثبت:

- لا يوجد أي استخدام مباشر لـ `InputStream.readAllBytes()` في كود المشروع نفسه،
  ولا في أي jar مرفق (`app/libs/*.jar`)، ولا في stub jars (`core-lambda-stubs.jar`,
  `sketchware-compile-stubs.jar`) — تم فحصها كلها بايتيًا (bytecode strings).
- `coreLibraryDesugaring` المفعَّل في `app/build.gradle` بيغطي **كود تطبيق
  Sketchware-ai نفسه فقط** (شامل ECJ/R8 كـ Gradle dependencies، لأنهم بيتJحوَّلوا
  ضمن نفس الـ dex عند البناء) — لكنه **لا يغطي** أي dex عملية D8 منفصلة بيستدعيها
  الكود برمجيًا زي اللي في `DexCompiler.compileDexFiles()`.
- `DexCompiler` بيعمل dex فقط لملفات `.class` الخاصة بمشروع **المستخدم** (اللي
  ECJ بيcompilها)، فأي استدعاء لـ Java 9+ API (زي `readAllBytes()`، المُضافة في
  API 33) موجود في كود المستخدم نفسه أو في أي مكتبة ربطها بمشروعه، هيتD8 بدون أي
  تحويل desugaring، وهيكرش وقت التشغيل على أي جهاز تحت API 33.

## المحاولة السابقة الفاشلة ولماذا فشلت

كان فيه تعليق موثَّق في نفس الملف بيقول إن محاولة سابقة لإصلاح المشكلة فشلت في
الترجمة لأن `com.android.tools.r8.StringResource` مش متاحة كـ public API. تم
التحقق من مصدر r8 الرسمي (`BaseCompilerCommand.java`) وتبيَّن إن فيه **overload
تاني** بياخد `String` مباشرة (`addDesugaredLibraryConfiguration(String)`) —
موجود فعلاً كـ public method على `BaseCompilerCommand.Builder` (الأب اللي
`D8Command.Builder` بيرث منه)، ومفيش داعي لاستخدام `StringResource` خالص.

## الإصلاح

1. **`app/build.gradle`**: إضافة Gradle task جديد (`stageDesugaredLibraryConfigAsset`)
   بيحل (resolve) نفس مكتبة `com.android.tools:desugar_jdk_libs_configuration:2.1.5`
   وقت البناء، ويستخرج ملف الـ JSON بتاعها، ويحطه كـ asset في
   `app/src/main/assets/desugar_jdk_libs_configuration.json`. مربوط بـ
   `preBuild.dependsOn` بنفس نمط `createMockGoogleServices` الموجود مسبقًا. الملف
   الناتج **لا يُحفَظ في git** (مضاف لـ `.gitignore`) لأنه بيتولَّد تلقائيًا كل بناء
   ودايمًا هيطابق نسخة `desugar_jdk_libs` الفعلية المستخدمة.

2. **`DexCompiler.java`**: بيقرأ الـ asset ده وقت التشغيل، ويمرره كـ `String` لـ
   `D8Command.Builder#addDesugaredLibraryConfiguration(String)` — لكن **فقط لو**
   الـ `minSdk` المُعرَّف في إعدادات مشروع المستخدم أقل من 26 (الحد اللي مكتبة
   desugar_jdk_libs بتغطيه)؛ فوق كده الجهاز أصلاً معاه التطبيقات الحقيقية ومفيش
   داعي للـ desugaring. لو الـ asset مفقود لأي سبب، الكود **لا يفشل البناء** —
   بيكمل بنفس السلوك القديم (بدون desugaring) مع تحذير `toastError` للمطوّر.

## تحقق تم إجراؤه فعليًا
- تم تتبع سلسلة الاستدعاء كاملة من `DexCompiler` → `programFiles`/`libraryFiles`
  لتأكيد إن D8 بيدexس فقط ملفات المستخدم المُترجَمة، مش أي jar من Sketchware نفسه.
- تم فحص bytecode كل الـ jars المرفقة (`app/libs/*.jar` و`assets/libs/*.jar`)
  بحثًا عن `readAllBytes` — صفر نتائج في كل واحد منها.
- تم التحقق من `AndroidManifest.xml` إن `ECJCompilerService` بيشتغل في process
  منفصل (`:compiler`) لكن بدون أي classloader مخصص — يستخدم نفس الـ dex
  المُطبَّق عليه الـ desugaring، فمش هو مصدر المشكلة كما افترض التعليق القديم.
- تم التحقق من `BaseCompilerCommand.java` **مباشرة من مصدر r8 الرسمي على
  r8.googlesource.com** (النسخة الحالية على `main`) لتأكيد وجود
  `addDesugaredLibraryConfiguration(String)` كـ public method، بدل الاعتماد على
  افتراض أو توثيق غير مباشر.
- تم التحقق من نمط قراءة الـ assets المستخدم (`SketchApplication.getContext()`)
  مطابق تمامًا لنفس النمط المستخدم فعليًا في `ProjectBuilder.java` (نفس الملف
  اللي بيستدعي `DexCompiler`).

## يحتاج اختبار فعلي على الجهاز (لم يُختبَر بعد — لا بيئة بناء Gradle متاحة هنا)
- **الأهم:** تشغيل `./gradlew assembleAndroid26Debug` (أو أي variant) والتأكد إن
  `stageDesugaredLibraryConfigAsset` بينفّذ بنجاح ويطلع ملف JSON صحيح فعليًا —
  لم يُتحقَّق أن مسار الـ jar الداخلي لـ `desugar_jdk_libs_configuration:2.1.5`
  فعلاً بيحتوي ملف `.json` واحد بالشكل المتوقَّع؛ منطق الـ task مصمَّم ليتعامل
  مع أول ملف `.json` يلاقيه، لكن لو الهيكل الداخلي للـ artifact مختلف عن
  المتوقَّع، الـ task هيفشل بخطأ واضح (`GradleException`) بدل فشل صامت.
- بناء مشروع مستخدم يحتوي كود بيستخدم `readAllBytes()` (أو أي Java 9+ API تاني)
  على محاكي API 29 فعليًا، والتأكد إن الكرش اختفى.
- التأكد إن تفعيل الـ desugaring على مسار D8 الخاص بمشروع المستخدم مش بيبطّئ
  عملية البناء بشكل ملحوظ أو بيسبب تعارضات مع مكتبات تانية في مشاريع المستخدمين.

## Phase 4 — حلقة التصحيح الذاتي (Self-Correction Loop)

### تحقق تم قبل أي كود
- `AgentOrchestrator.executeStep()` (Phase 2) كان بالفعل يوقف التنفيذ فورًا عند
  أول `ToolResult` فاشل — تم التأكد من هذا بقراءة الكود الفعلي (التعليق
  `"No self-correction loop in this phase — stop and report"` كان موجودًا حرفيًا
  فوق استدعاء `onStepFailed`).
- فلترة `CompileDiagnostic.Severity` **موجودة وتعمل**، لكنها private method باسم
  `filterErrorsOnly()` داخل `CompileLogActivity.java` (مش method مستقلة قابلة
  لإعادة الاستخدام مباشرة من كلاس تاني)، وتعتمد على
  `pro.sketchware.utility.diagnostics.CompileErrorCapture.parse()` —
  **تنبيه مهم**: يوجد كلاس تاني بنفس الاسم `CompileErrorCapture` لكن في package
  مختلف (`pro.sketchware.util`) وله غرض مختلف تمامًا (يقرأ من `CompileErrorSaver`،
  مش parsing). تم التأكد من الفرق قبل الاستخدام لتفادي استيراد الكلاس الغلط.
- الأداة الفعلية المسؤولة عن الحصول على سجل الأخطاء للـ AI مش اسمها
  `GetErrorLogTool` كما افترض البرومبت — اسمها الحقيقي `CompileTools.GetCompileLogsTool`
  (اسم الأداة `get_compile_logs`)، وعندها implementation خاص بيها لفلترة الأخطاء
  (`extractRootCause()`) — **مختلف عن** `filterErrorsOnly()` بتاعة
  `CompileLogActivity`. الاثنان مستقلان تمامًا ولم يتم توحيدهما في هذه المرحلة
  (توحيدهما تغيير أكبر خارج نطاق "أغلق الحلقة" المطلوب هنا).
- لا يوجد `WriteLogicTool` في المشروع. الأدوات الفعلية للتعديل هي
  `FileTools.PatchFileTool` (`patch_file`)، `FileTools.WriteFileTool` (`write_file`)،
  `FileTools.AppendCodeTool` (`append_code`)، و `FileTools.InsertCodeAtLineTool`
  (`insert_code_at_line`) — دول اللي استُخدِموا في حلقة التصحيح.
- `BuildRepairTool.AnalyzeBuildErrorTool` (Phase 3) موجودة بالفعل وبتعمل تصنيف
  حتمي (regex-based, لا LLM) للأخطاء وترتيبها في مراحل — تم استخدامها كمُدخَل
  إضافي (سياق) لطلب الـ LLM في حلقة التصحيح، بدل تكرار نفس منطق التصنيف.
- `BuildTools.BuildProjectTool` (`build_project`) موجودة وموصوفة صراحة إنها
  لازم تُتبَع بـ `get_compile_logs` عند الفشل — نفس التسلسل المُستخدَم هنا.

### الحد الأقصى لمحاولات التصحيح
**القيمة الافتراضية: 3 محاولات** (`SelfCorrectionLoop.DEFAULT_MAX_CORRECTION_ATTEMPTS`)،
قابلة للتهيئة عبر constructor.

**لماذا 3، وليس رقم تاني:**
- محاولة واحدة مش كفاية غالبًا: خطأ بيتصحح ممكن يكشف خطأ تاني كان "مستخبي" وراه
  (زي ما `BuildRepairTool` نفسها بتفترض بترتيبها المرحلي: STAGE 1 قبل STAGE 4+).
- بدون حد أقصى (infinite loop): احتمال إن الموديل يدور بين نفس الخطأ ذهابًا وإيابًا
  بدون تقدم فعلي، وبيستهلك API cost/وقت بدون أي مؤشر توقف واضح للمستخدم.
- 3 محاولات = مساحة كافية لـ "صحّح A → يظهر B → صحّح B" (محاولتين) + محاولة
  احتياطية واحدة لتصحيح لم ينجح بالكامل من أول مرة — مع إبقاء أسوأ سيناريو
  (latency/cost) محدود بثلاث دورات بناء كاملة. كل محاولة هنا = full rebuild كامل
  (ECJ + D8 + AAPT2) لأن incremental compilation اتشالت بالكامل (حسب الملاحظات
  في سياق المشروع) — يعني كل محاولة مكلفة، فمفيش داعي لرقم أكبر بدون سبب واضح.

### آلية اكتشاف الـ Crash (بالتحديد، لا TODO)
`RunAndVerifyOnDeviceTool` بيستخدم **logcat-based detection**، مش process
monitoring، للسبب التالي: التطبيق المُثبَّت (مشروع المستخدم) بيشتغل كـ
process/UID منفصل تمامًا عن Sketchware-ai نفسه، فمفيش طريقة لمراقبة الـ PID
بتاعه مباشرة بدون root (وحتى مع root، دورة crash-restart قصيرة ممكن تدّي نتيجة
ملتبسة: "مش شغال" — هل معناها ماشتغلش أصلاً، ولا كرش وانقفل بسرعة؟).

**الخطوات بالتحديد:**
1. قبل الـ launch مباشرة، بياخد timestamp من ساعة الجهاز بصيغة `logcat -T`.
2. بيعمل launch للـ activity الرئيسية عبر `PackageManager.getLaunchIntentForPackage()`.
3. بينتظر فترة قابلة للتهيئة (افتراضي 6 ثواني، حد أقصى 30).
4. بيجيب فقط الأسطر اللي ظهرت بعد الـ timestamp ده عبر
   `logcat -d -v time -T '<timestamp>'` (نفس نمط `LogcatManager`/`LogcatFilterTool`
   الموجودين بالفعل ومُستخدَمين فعليًا في المشروع — لم يُخترَع نمط جديد).
5. بيدوّر على سطر فيه `"FATAL EXCEPTION"` (marker قياسي من
   `Thread.UncaughtExceptionHandler` بتاع Android لأي كراش Java/Kotlin غير
   ملتقَط)، وبيتأكد إن نافذة الأسطر حوالين الـ marker ده فيها اسم الـ package
   بتاع المشروع — عشان ميتلخبطش مع كراش تطبيق تاني شغال في نفس اللحظة.
6. لو الفحص نفسه رمى Exception (مش نفس حاجة "مفيش كراش") — بيترجع
   `status: "crash_check_failed"` بدل ما يبلّغ كذبًا إن التشغيل نضيف.

**قيد التثبيت (مهم):** الأداة دي بتستخدم مسار الـ root فقط (نفس آلية
`DesignActivity.installBuiltApk()` الموجودة، `pm install -S` عبر shell) لأنه
المسار الوحيد اللي بيرجّع نتيجة قابلة للتحقق برمجيًا بدون تدخل بشري. مسار
عدم الـ root الموجود فعلاً في `DesignActivity` (`ACTION_VIEW` intent) بيفتح
واجهة تثبيت النظام وبيحتاج ضغطة مستخدم — مينفعش يتستخدم في حلقة تلقائية، فلو
الجهاز مش root، الأداة بترجع فشل صريح بدل ما تتظاهر بالنجاح.

### الملفات الجديدة/المعدَّلة
1. **`AgentOrchestrator.java`** (معدَّل): عند فشل خطوة `build_project` تحديدًا
   (مش أي خطوة تانية)، بيتنادى `SelfCorrectionLoop` بدل التوقف الفوري. أي فشل
   في أداة تانية غير `build_project` لسه بيوقف الخطة فورًا **بدون تغيير** —
   ده قرار متعمَّد ومكتوب صراحة في الكود والـ javadoc، مش نسيان.
2. **`SelfCorrectionLoop.java`** (جديد): تنفيذ حلقة build → get_compile_logs
   (+ analyze_build_error كسياق إضافي) → طلب patch من الـ LLM → تطبيقه عبر
   `patch_file`/`write_file`/`append_code`/`insert_code_at_line` → rebuild →
   تكرار حتى `DEFAULT_MAX_CORRECTION_ATTEMPTS`.
3. **`RunAndVerifyOnDeviceTool.java`** (جديد): تسجيل باسم أداة
   `run_and_verify_on_device`، risk level `CRITICAL`. نطاقها محدود صراحة:
   crash-on-launch فقط، مفيش أي behavioral/UI testing.
4. **`ToolRegistry.java`** (معدَّل): تسجيل `RunAndVerifyOnDeviceTool` ضمن قسم
   Build & Compile.

### قيود صريحة — لم يُختبَر فعليًا (لا يوجد جهاز Android متصل بالبيئة)
- **لا شيء في هذه المرحلة تم اختباره على جهاز حقيقي.** البيئة اللي كُتِب فيها
  هذا الكود مفيهاش أي جهاز/محاكي Android متصل، فمفيش build_project حقيقي، ولا
  install، ولا logcat فعلي اتنفذوا للتأكد من سلوكهم الفعلي.
- تحديدًا لم يُختبَر:
  - إن `SelfCorrectionLoop` فعلًا بيصحح خطأ حقيقي عبر LLM ويعيد البناء بنجاح.
  - إن الحد الأقصى (3 محاولات) بيوقف نظيف فعلًا عند فشل متكرر حقيقي (مش
    simulation).
  - إن `pm install -S` عبر `su -c` بيرجع exit code صحيح على أجهزة/ROMs مختلفة —
    الكود بيقلد نفس الأمر الموجود في `DesignActivity` لكن بدون libsu library
    (استخدمت `Runtime.exec(["su","-c",...])` مباشرة بدل الاعتماد على مكتبة
    `Shell` من libsu، لتفادي إضافة dependency جديدة لأداة مستقلة — ده فرق
    تقني عن الكود الأصلي يستاهل اختبار خاص بيه).
  - إن `logcat -T '<timestamp>'` بالصيغة المُستخدَمة (`MM-dd HH:mm:ss.SSS`)
    مقبولة فعليًا على كل نسخ Android المستهدَفة — الصيغة دي موثَّقة في
    logcat لكن لم تُختبَر فعليًا هنا.
  - إن اكتشاف الكراش (البحث عن `"FATAL EXCEPTION"` + اسم الـ package في نفس
    النافذة) بيرجّع false positive/negative في سيناريوهات حقيقية (زي: تطبيق
    تاني بنفس جزء من الاسم، أو كراش native/NDK مش بيطلّع "FATAL EXCEPTION"
    بنفس الصيغة).
  - أداء الأداة لما مفيش root متاح — تم اختبار المنطق قراءةً فقط، مش تشغيلًا.

---
**تنويه صريح: هذه المرحلة تحتاج اختبارًا ميدانيًا كاملًا على جهاز حقيقي قبل أي
اعتماد إنتاجي.** بالتحديد، لازم يُختبَر:
1. سيناريو بناء ناجح من أول محاولة (self-correction ماينفّذش أصلًا).
2. سيناريو خطأ بسيط (زي `@id/` بدل `@+id/`) يتصحح تلقائيًا بنجاح خلال محاولة
   أو اتنين.
3. سيناريو فشل متكرر (خطأ الموديل مش عارف يصلحه) يوصل للحد الأقصى (3) ويتوقف
   بشكل نظيف مع تقرير واضح للمستخدم، بدون infinite loop وبدون كراش في التطبيق
   نفسه (Sketchware-ai).

## Phase 4 — إضافة لاحقة: ربط Snapshot/Rollback بحلقة التصحيح

بعد التسليم الأول لـ Phase 4، تم سد فجوة كانت موثَّقة صراحة كنقطة مفتوحة:
`SelfCorrectionLoop` كانت بتطبّق الـ patches مباشرة من غير أي حماية rollback.

### تحقق تم قبل الربط
- تأكدت إن `ProjectSnapshotManager.createSnapshot()` بتنسخ فعليًا
  `ToolContext.getProjectDataDir(scId)` (يعني `.sketchware/data/{scId}/`).
- تأكدت إن `FilePathUtil.getPathJava/getPathResource/getPathAssets` كلهم بيرجعوا
  مسارات تحت `.sketchware/data/{scId}/files/...` — يعني **جوه** نفس الفولدر اللي
  الـ snapshot بينسخه. بالتالي snapshot واحد قبل أول محاولة كافي يغطي كل حاجة
  ممكن `patch_file`/`write_file`/`append_code`/`insert_code_at_line` تلمسها.
- تأكدت إن `RestoreSnapshotTool` (الأداة الجاهزة) موصوفة كـ CRITICAL و"Requires
  explicit user approval" — بما إن الـ approval gating نفسه لسه مش متفعّل في
  `AgentOrchestrator` (فجوة موثَّقة من Phase 2)، الحلقة بتستخدم
  `ProjectSnapshotManager` **مباشرة** (مش عبر أداة `restore_snapshot`) كآلية
  حماية داخلية تديرها الحلقة نفسها، مش كأداة تُعرَض للمستخدم/الموديل.

### السلوك الجديد
- **Snapshot واحد فقط** (baseline) بيتاخد قبل أول محاولة تصحيح — مش snapshot
  جديد كل محاولة.
- قبل أي محاولة **إعادة** (attempt > 1)، المشروع بيترجع لنفس الـ baseline قبل
  ما الموديل يقترح patch جديد. السبب: من غير ده، محاولة 2 هتصحح فوق تصحيح
  محاولة 1 الغلط، يعني الأخطاء ممكن تتراكم بدل ما كل محاولة تبدأ من نقطة نضيفة
  معروفة.
- لو الـ snapshot نفسه فشل (مساحة تخزين خلصت مثلًا)، الحلقة **مش بتوقف** —
  بتكمل بدون حماية rollback (نفس السلوك القديم) مع تحذير واضح عبر
  `onSnapshotEvent`.
- الـ baseline snapshot **بيفضل موجود** بعد ما الحلقة تخلص (نجاح أو فشل) —
  مش بيتمسح تلقائيًا — عشان لو النتيجة النهائية مش مرضية، المستخدم يقدر يرجعها
  يدويًا عبر `restore_snapshot`.

### لسه محتاج اختبار فعلي
- إن الـ rollback فعليًا بيرجّع الملفات صح ومفيش ملفات متبقية من محاولة فاشلة.
- سلوك الحلقة لو الـ storage امتلا فعليًا (snapshot creation failure path).

## Phase 4 — إضافة ثانية: ربط الموافقة (Approval Gating) + تشغيل تلقائي لـ run_and_verify_on_device

بعد الإضافة الأولى (snapshot/rollback)، تم سد فجوتين كانتا موثَّقتين صراحة
كنقاط مفتوحة في `AgentOrchestrator`:
1. `executeStep()` كان بينفّذ أي أداة (حتى CRITICAL) مباشرة من غير المرور على
   `ToolValidator`/`ApprovalManager` أصلاً — رغم إن الآلية دي جاهزة وشغالة في
   `AgentExecutor` (المسار اليدوي عبر BottomSheet).
2. `RunAndVerifyOnDeviceTool` (من الإضافة السابقة لـ Phase 4) كانت مسجَّلة في
   `ToolRegistry` بس مش مربوطة بأي استدعاء تلقائي — تحتاج تُطلَب صراحة كخطوة
   في الـ plan.

### تحقق تم قبل الربط
- تأكدت إن `AgentExecutor` بيستخدم فعليًا `ToolValidator` (لحل الـ risk level)
  و`ApprovalManager` (لعرض طلب الموافقة عبر `ApprovalCallback`) بنفس النمط في
  `executeTool()` — نفس الكلاسين استُخدِموا هنا حرفيًا، مفيش آلية جديدة اتخترعت.
- تأكدت إن `ApprovalManager.requestApproval()` عندها سلوك جاهز لو
  `callback == null`: بترجع `DENIED` مباشرة (مش استثناء، مش auto-approve) —
  السلوك ده اتُستخدِم كما هو بدل ما يتعاد تصميمه.
- تأكدت إن `RiskLevel.CRITICAL` (زي `run_and_verify_on_device`) دايمًا
  بتتطلب موافقة في أي `ApprovalMode` (بما فيها `AUTONOMOUS`).

### قرارات صُرِّح بها للمستخدم بدل افتراضها (2 قرارات، ردّ عليهم المستخدم صراحة)
1. **لو مفيش UI موافقة مربوطة (`callback == null`)، إيه سلوك خطوة CRITICAL؟**
   القرار: إضافة overload جديد لـ `executeUserRequest(...)` يقبل
   `ApprovalCallback` اختياري (يوازي `setApprovalCallback()` بتاعة
   `AgentExecutor`). لو محدش مرّر callback، السلوك الافتراضي = **رفض
   (DENIED)** — نفس سلوك `ApprovalManager` الجاهز، من غير أي إعادة تصميم أو
   auto-approve. التوقيع القديم (6 معاملات) لسه شغّال زي ما هو (delegate
   لـ overload الجديد بـ `approvalCallback = null`) — مفيش كسر لأي كود قائم.
2. **هل `run_and_verify_on_device` تتربط تلقائيًا بعد نجاح `build_project`؟**
   القرار: **نعم، تلقائيًا** — بعد أي نجاح لـ `build_project` (سواء مباشر أو
   بعد نجاح `SelfCorrectionLoop`)، تتنفذ `run_and_verify_on_device` كخطوة
   إضافية غير مخطَّطة، من غير ما الموديل يحتاج يطلبها صراحة في الـ plan.

### السلوك الجديد بالتفصيل
- **`executeStep()`**: بعد الـ validation الموجود (schema + runtime)، بيتنادى
  `ToolValidator.validate()` لحل الـ risk level، وبعده:
  - لو `requiresSnapshot` (MEDIUM/CRITICAL): ياخد snapshot قبل التنفيذ (نفس
    نمط `AgentExecutor.executeTool()`).
  - لو `requiresApproval` (MEDIUM/CRITICAL في `ApprovalMode.BALANCED`، القيمة
    الافتراضية المستخدَمة هنا زي `AgentExecutor`): بيتنادى
    `approvalManager.requestApproval()`. لو `DENIED`/`CANCELLED`/`TIMEOUT`،
    الخطوة بترجع فشل واضح (مش بتتنفذ) — الرسالة بتوضّح صراحة لو السبب هو عدم
    وجود UI موافقة مربوطة.
- **`runAutoVerify()`** (جديد، مُستدعاة من `executeUserRequest`'s step loop):
  بعد أي نجاح لخطوة `build_project`، بتبني `PlanStep` جديدة
  (`run_and_verify_on_device`, `sc_id` من خطوة البناء) وتُنفَّذها عبر نفس
  `executeStep()` — يعني **لسه approval-gated بنفس القواعد** (CRITICAL يحتاج
  موافقة، ولو مفيش callback هترفض تلقائيًا). فشل أو رفض الـ verify **مش
  بيفشّل الخطة كلها** — دي فحص أمان إضافي فوق بناء نجح فعلاً، مش شرط أساسي
  للخطة، فمفيش داعي يوقف باقي الخطوات.
- **Callbacks جديدة في `Callback` interface**: `onVerifyStarted(scId)` و
  `onVerifyCompleted(scId, result)` — كلاهما `default` (فارغة)، فمفيش كسر لأي
  implementation قائم لـ `Callback`.

### الملفات المعدَّلة
1. **`AgentOrchestrator.java`** (معدَّل):
   - إضافة `ToolValidator`/`ApprovalManager`/`ApprovalCallback` كـ fields.
   - `setApprovalCallback(ApprovalCallback)` — method جديدة (public)، توازي
     نفس الاسم في `AgentExecutor`.
   - `executeUserRequest(...)` (6 معاملات، القديمة) — لسه موجودة، بترجع تستدعي
     overload جديد بـ 7 معاملات (approvalCallback في الآخر) — **لا كسر توافقي**.
   - `executeStep()` — إضافة الـ risk/snapshot/approval gate بعد الـ
     validation الموجود.
   - `runAutoVerify()` — method خاصة جديدة، بتتنادى تلقائيًا بعد أي
     `build_project` ناجح (مباشر أو عبر `SelfCorrectionLoop`).
   - `Callback` interface — إضافة `onVerifyStarted`/`onVerifyCompleted`
     (كـ `default` — لا كسر توافقي).

### لسه محتاج اختبار فعلي (لا يوجد جهاز/UI في هذه البيئة)
- إن الـ overload الجديد لـ `executeUserRequest` بيمرر الـ `ApprovalCallback`
  صح لـ `ApprovalManager` وبيوصل فعليًا لواجهة موافقة حقيقية (لسه مفيش UI
  مربوطة أصلاً — خارج نطاق هذه الجلسة، زي كل المراحل قبلها).
- إن `run_and_verify_on_device` بيتنفذ فعليًا بنجاح تلقائي بعد `build_project`
  ناجح على جهاز حقيقي فيه root، وإن رفض/فشل الـ verify فعليًا **مش** بيوقف
  باقي خطوات الـ plan (اتفحص منطقيًا في الكود، مش اتشغّل فعليًا).
- إن رسالة الرفض الافتراضي (`DENIED` من غير callback) بتوصل بوضوح للمستخدم
  عبر `onStepFailed`/`onVerifyCompleted` لو حد وصل UI مستقبلًا.
- **ملاحظة صريحة**: مفيش أي جهاز Android متصل بالبيئة، فمفيش `javac`/Gradle
  build كامل اتنفذ للتأكد من عدم وجود خطأ compile — تم التحقق فقط عبر مراجعة
  يدوية دقيقة لتوازن الأقواس، وتوقيعات الكلاسات الفعلية (`ToolValidator`,
  `ApprovalManager`, `ToolValidationResult`, `ApprovalCallback`) من الأرشيف
  المرفوع نفسه، وليس من الذاكرة.

---

## المرحلة ٥ (إعادة المحاولة) — Offline AI Models عبر LiteRT-LM

### السياق
محاولة سابقة لهذه المرحلة فشلت في أول Gradle sync بخطأ توافق نسخة Kotlin metadata
(`litertlm-android:0.13.1` مبني بـ Kotlin metadata version 2.3.0، والمشروع كان
مستخدم `kotlin-gradle-plugin:2.1.21` اللي بيقرأ لحد 2.2.0 بس). الملفات اللي اتبنت
في المحاولة دي كانت فاضية في الأرشيف المرفوع (`ai/offline/` كان directory فاضي) —
يعني تم البدء من الصفر في هذه الجلسة، مش استكمال كود موجود.

### القرار الجوهري: دعم Qwen + Gemma معًا (خمس موديلات)، مش Gemma بس
البرومبت المرفق (`PROMPT_PHASE_5.md`) كان بيحدد Gemma-only عبر MediaPipe. طلب
المستخدم الصريح في هذه الجلسة تجاوز ذلك: دعم موديلات متعددة تغطي الأجهزة الضعيفة/
المتوسطة/القوية، مع توضيح الإمكانيات تحت كل موديل، وأولوية لموديل يدعم البرمجة
الفعلية (زي Qwen). البرومبت المرفق اتعامل معه كخلفية سياقية فقط، مش كمواصفة نهائية.

### الموديلات الخمسة المختارة (LiteRT-LM، ملفات `.litertlm` حقيقية من
`huggingface.co/litert-community`، تم التأكد من أسماء الملفات عبر بحث ويب في هذه الجلسة):

| الموديل | الحجم التقريبي | الفئة | ملاحظة |
|---|---|---|---|
| Qwen3 0.6B | ~0.7GB | جهاز ضعيف | الأخف، قدرات برمجة محدودة |
| Gemma 3 1B | ~1.0GB | جهاز ضعيف | من Google، متوازن |
| **Qwen2.5 1.5B Instruct** | ~1.7GB | جهاز متوسط | **الافتراضي الموصى به** — أقوى الخمسة في اتباع تعليمات برمجية |
| Gemma 3n E2B | ~2.9GB | جهاز قوي | الأكبر، أعلى جودة استدلال عام |

كل موديل له نص عربي (`getCapabilityNote()`) يظهر تحته مباشرة في شاشة الإعدادات،
زي ما طُلب بالظبط.

### فحص minSdk (=26) مقابل الحد الأدنى الفعلي لـ LiteRT-LM
مفيش رقم minSdk موثق رسميًا بشكل صريح لـ AAR الأندرويد نفسه من جوجل. تم الاعتماد
على مصدر غير مباشر لكنه موثوق: `flutter_litert_lm` (plugin مجتمعي بيغلف نفس
الـ native runtime) بيوثق **الحد الأدنى Android API 24**. بما إن minSdk الحالي
للمشروع = **26 > 24**، **مفيش تعارض إطلاقًا** — تم تنفيذ الميزة بدون أي شرط `sdk
>=`/`<=` لأن الشرط غير مطلوب فعليًا. لو ظهر تعارض حقيقي عند أول Gradle sync
حقيقي (رقم مختلف كليًا)، الحل هو إضافة `android26Implementation`/
`android33Implementation` flavor-scoped dependency بنفس نمط `bundletool`/`r8`
الموجود بالفعل في `app/build.gradle`، مش شرط runtime.

### إصلاح خطأ Kotlin metadata (السبب الجذري، مش workaround)
`build.gradle` (root): رفع `kotlin-gradle-plugin` من `2.1.21` إلى **`2.3.20`**
(أحدث نسخة stable مؤكدة وقت الجلسة، Kotlin 2.4.0 stable لكن 2.3.20 كافية ومضمونة
التوافق مع AGP 8.12.0 الحالي بدون قفزة كبيرة غير ضرورية). **ملاحظة مهمة**: هذا
التغيير يخص Gradle build toolchain فقط. المترجم المدمج جوه التطبيق نفسه
(`kotlinc-for-sketchware:2.1.21_rc3`, `compileOnly kotlin-compiler:2.1.21` —
لتصريف مشاريع المستخدمين) **لم يُمس إطلاقًا** — تم التأكد من إنه dependency منفصل
تمامًا قبل التعديل، فمفيش خطر كسر ميزة تصريف مشاريع المستخدمين.

### الملفات الجديدة (`app/src/main/java/pro/sketchware/ai/offline/`)
- **`LocalModelCatalog.java`** — enum بالموديلات الخمسة + رابط تنزيل حقيقي +
  حجم تقريبي + الحد الأدنى للرام + النص العربي التوضيحي لكل موديل.
- **`LocalModelState.java`** — enum بسيط (`NOT_DOWNLOADED`/`DOWNLOADING`/`READY`/`ERROR`).
- **`LocalModelManager.java`** — يدير المسارات على القرص، الحالة، نسبة التقدم،
  فحص الرام (تحذير فقط عند <6GB أو أقل من توصية الموديل نفسه — **لا يمنع أبدًا**،
  القرار النهائي للمستخدم زي ما هو متفق)، وفحص المساحة الحرة.
- **`LocalModelDownloader.java`** — تنزيل عبر OkHttp (المكتبة الموجودة بالفعل في
  المشروع)، بدعم استئناف عبر `Range` header. تم توثيق صراحة *لماذا* OkHttp مش
  WorkManager/DownloadManager (المشروع مفيهوش WorkManager كـ runtime dependency
  أصلاً — فقط كمكتبة تُقترح لمشاريع المستخدمين).
- **`LiteRtLmEngineBridge.kt`** — جسر Kotlin حول `Engine`/`Conversation` API
  (موثق من `ai.google.dev/edge/litert-lm/android`، تاريخ آخر تحديث موثق 2026-05-28).
  **مصمم عمدًا بواجهة callback بسيطة (`GenerationCallback`) بدلاً من تسريب
  Flow/suspend لجافا** — تفاديًا لمشاكل Java/Kotlin coroutine interop اللي بتفشل
  وقت الترجمة مش وقت المراجعة.
- **`LocalModelProvider.java`** — تنفيذ `AiApiClient` الكامل (تم التحقق من كل
  توقيع method من `AiApiClient.java`/`ModelInfo.java`/`ChatMessage.java`/
  `StreamingResponseHandler.java` الفعليين في الأرشيف المرفوع قبل الكتابة، مش من
  الذاكرة). Tool calling غير مفعّل هذه المرحلة (مش مطلوب، وغير قابل للتحقق بدون
  build حقيقي) — الـ overloads اللي بتاخد `List<ToolDefinition>` بتتجاهلها بأمان
  من غير ما تكسر أي call site موجود.

### الملفات المعدَّلة
- **`AiProvider.java`**: قيمة جديدة `LOCAL_LLM` (مجموعة رابعة `OFFLINE` منفصلة
  عن `FREE_NO_API`/`FREE_WITH_API`/`PAID`)، `getLimitsText()`, `getSelectorLabel()`.
- **`AiClientFactory.java`**: `case LOCAL_LLM: new LocalModelProvider(context)`.
- **`ProviderCapabilities.java`**: قيم افتراضية متحفظة لـ LOCAL_LLM (context 4096
  — يطابق `ekv4096` في أسماء ملفات الكتالوج، بدون vision/tools هذه المرحلة).
- **`app/build.gradle`**: إضافة `litertlm-android:0.13.1` و
  `kotlinx-coroutines-android:1.9.0`.
- **`activity_ai_settings.xml`**: بطاقة "Offline AI Models" جديدة (تحذير رام +
  RecyclerView) بين قائمة الـ providers و"Advanced Settings"، ظاهرة دايمًا
  (مش جوه القسم القابل للطي).
- **`item_offline_model.xml`** (جديد): صف كل موديل — اسم، فئة/حجم/رام مطلوب،
  نص القدرات، شريط تقدم، زر إجراء واحد يتغير حسب الحالة.
- **`OfflineModelAdapter.java`** (جديد، في `ai/adapters/`): يربط الكتالوج
  بالـ RecyclerView.
- **`AiSettingsActivity.java`**: `setupOfflineModels()` + `startOfflineModelDownload()`
  — تأكيد صريح (Dialog) قبل أي تنزيل يوضح الحجم بالـ GB وتحذير الرام لو الجهاز
  أضعف من توصية الموديل، زي ما طُلب.

### لسه محتاج اختبار فعلي (لا يوجد جهاز/شبكة إنترنت في هذه البيئة)
- **الأهم**: إن `com.google.ai.edge.litertlm:litertlm-android:0.13.1` بيتحل فعليًا
  من Google Maven بعد رفع Kotlin Gradle plugin لـ 2.3.20 — أول Gradle sync حقيقي
  عندك هو الاختبار الحقيقي الوحيد لده.
- إن أسماء الـ classes/methods في `LiteRtLmEngineBridge.kt`
  (`Engine`, `EngineConfig`, `Backend.CPU`, `ConversationConfig`,
  `Conversation.sendMessageAsync(String): Flow<Message>`) مطابقة ١٠٠٪ للـ API
  الفعلي في 0.13.1 — اتكتبت من التوثيق المتاح أونلاين وقت الجلسة، مش من قراءة
  الكود المصدري لـ 0.13.1 نفسه (مفيش وصول ليه في هذه البيئة).
- إن روابط التنزيل المباشرة (`resolve/main/*.litertlm`) شغالة فعليًا وبترجع
  الملف الصحيح لكل الموديلات الخمسة — اتأكد من الأسماء عبر بحث ويب، مش عبر تنزيل
  فعلي (مفيش شبكة في هذه البيئة أصلًا).
- إن استئناف التنزيل عبر `Range` header فعليًا مدعوم من CDN هجينج فيس (المتوقع نعم
  لأنه Cloudflare، لكن غير مؤكد ١٠٠٪ لكل الملفات).
- إن سلوك `-Xskip-metadata-version-check` مش لازم أصلاً بعد رفع النسخة لـ 2.3.20 —
  تم الاعتماد على إصلاح السبب الجذري بدل الـ workaround، لكن لو ظهرت مشكلة توافق
  تانية غير متوقعة، الـ flag موجود كخيار احتياطي في `kotlinOptions` مستني إضافته
  لو احتجته فعليًا.

## Phase 5.1 — إصلاح تضاعف الـ context + تفعيل tool calling للموديل المحلي

### المشكلة المُصلَحة
تشخيص مؤكَّد بمراجعة الكود الفعلي (مش تخمين): محادثة قصيرة نسبيًا كانت توصل
لـ ~8034 توكن رغم إن الموديل مُصدَّر بـ KV cache ثابت 4096 توكن فقط (مُشفَّر في
اسم الملف `ekv4096`، مش قيمة إعدادات قابلة للتغيير من كود التطبيق). السبب:
`LiteRtLmEngineBridge` كان بيعمل `Conversation` واحدة فقط عند تحميل الموديل
ويعيد استخدامها لكل نداء لاحق — لكن `Conversation` في LiteRT-LM بتحتفظ بتاريخها
الداخلي تلقائيًا مع كل `sendMessageAsync`. بينما `LocalModelProvider.buildPromptFromMessages`
مصمم عمدًا "stateless per call" (كل نداء يبعت كل التاريخ من الأول). النتيجة:
كل رسالة كانت تبعت التاريخ الكامل **فوق** تاريخ محفوظ بالفعل جوه الـ Conversation
من قبل — فالسياق يتضاعف تقريبًا مع كل رد بدل ما يكبر بشكل طبيعي.

### الإصلاح
- **`LiteRtLmEngineBridge.kt`**: إزالة الحقل المشترك `conversation` من مستوى
  الـ instance. كل نداء `generate()` دلوقتي بينشئ `Conversation` جديدة خاصة
  بيه فقط، ويقفلها في `finally` بعد انتهاء النداء (نجاح، خطأ، أو إلغاء) —
  فمفيش أي حالة بتعدّي من نداء للتاني جوه LiteRT-LM نفسها. الـ `Engine` (تحميل
  الموديل) لسه بيتشارك بين النداءات زي ما كان، بس الـ `Conversation` بقت
  محصورة داخل كل نداء بمفرده. ده بيطابق تمامًا التصميم الأصلي الموثق في
  `LocalModelProvider.buildPromptFromMessages` (stateless per call) على مستوى
  LiteRT-LM كمان، بدل ما يتعارض معاه.
- **`LocalModelProvider.java` — `trimHistoryForLocalModel()`**: تقليم بسيط
  (مش تلخيص) للتاريخ قبل إرساله، بميزانية توكنز محافِظة (يحجز مساحة لبلوك
  الأدوات + مخرجات الموديل، والباقي للتاريخ). يستخدم
  `TokenBudgetChecker.estimateTokens()` الموجود بالفعل في المشروع (تقدير
  4 حروف/توكن) بدل اختراع حسبة جديدة. يمشي من أحدث رسالة للأقدم ويوقف لما
  الميزانية تخلص، لكن بيحتفظ دايمًا بآخر رسالة على الأقل حتى لو تعدّت
  الميزانية بمفردها (عشان ميضيعش آخر سؤال من المستخدم).

### تفعيل tool calling للموديل المحلي (كان متجاهَل تمامًا سابقًا)
- **`ProviderCapabilities.java`**: `LOCAL_LLM.tools` اتغيّرت من `false` لـ
  `true`. مهم: من غير التغيير ده، أي كود جوه `LocalModelProvider` لدعم الأدوات
  كان هيفضل ميت — لأن `AgentExecutor` بيفحص `caps.supportsTools` قبل ما يمرر
  أي `ToolDefinition` أصلًا، وكان بيبعت قائمة فاضية دايمًا للموديل المحلي.
- **مصدر الأدوات**: **مفيش قائمة أدوات جديدة اتعملت أو اتخمّنت.**
  `LocalModelProvider.sendChatRequest(..., List<ToolDefinition> tools, ...)`
  بياخد نفس الـ `List<ToolDefinition>` بالظبط اللي `AgentExecutor` بيبنيها من
  `ToolRegistry.getToolDefinitions()` الحقيقي (نفس المصدر اللي بيستخدمه أي
  provider تاني زي Gemini/OpenAI — راجع `AgentExecutor.java` حوالين
  `toolRegistry.getToolDefinitions()`). الموديل المحلي ميقدرش "يقرأ" أي ملف
  بنفسه أو يستنتج الأدوات من العدم — مفيش قناة تانية يعرف بيها الأدوات المتاحة
  غير الـ prompt نفسه.
- **`buildToolPromptBlock()`**: تحويل قائمة الأدوات (60+ أداة حاليًا في
  `ToolRegistry`) لصيغة نصية مختصرة جدًا (اسم + أول جملة من الوصف + أسماء
  الباراميترات بس، من غير الـ JSON Schema الكامل) — عشان تنفع مع سقف 4096
  توكن. بتتبعت **مع كل رسالة طول المحادثة** (مش أول رسالة بس) لأن الموديل
  stateless تمامًا بين النداءات بعد إصلاح الـ Conversation أعلاه — لو
  الأدوات اتبعتت مرة واحدة بس، الموديل هينساها في أي رسالة بعد كذا.
- **صيغة الاستدعاء**: الموديل بيتوجَّه (عبر الـ prompt) إنه يطلع بلوك واحد
  بالظبط `<tool_call>{"name":"...","arguments":{...}}</tool_call>` لو عايز
  يستخدم أداة. اتم اختيار الصيغة النصية الصارمة دي بدل الاعتماد على
  `OpenApiTool` الرسمية بتاعة LiteRT-LM لأن توفرها في `0.13.1` غير محقَّق
  فعليًا في هذه البيئة (زي ما موضح في caveat البناء الأصلي بالفعل).
- **`parseToolCall()` / `extractBalancedJsonObject()`**: محلل صارم بيدور على
  الوسمين بالظبط، يستخرج أول كائن JSON متوازن الأقواس (بيتجاهل أقواس جوه
  السلاسل النصية عشان ميتلخبطش)، ويرفض أي حاجة مش JSON صالح فيه `name` —
  بيرجّع `null` (يعني "مفيش استدعاء أداة") بدل أي تخمين. رد بدون البلوك ده
  بيتعامل معاه كـ "شات عادي" زي ما كان الوضع الافتراضي أصلًا.

### لسه محتاج اختبار فعلي (بالإضافة لقائمة Phase 5 الأصلية)
- إن موديل صغير زي Qwen 4096 فعليًا هيتبع صيغة `<tool_call>` الصارمة باستمرار —
  موديلات صغيرة كتير بتهلوس أو تنسى الصيغة، وده محتاج قياس ميداني حقيقي على
  جهاز فعلي، مش تحليل نظري.
- إن ميزانية `RESERVED_FOR_TOOL_BLOCK_TOKENS = 900` كافية فعليًا لقائمة الـ60+
  أداة الحالية بالصيغة المختصرة — اتحسبت تقديريًا، لازم تتقاس فعليًا وتتعدَّل
  لو القائمة كبرت أو الوصف كان أطول من المتوقع.

## Phase 5.2 — تفعيل tool calling لسبعة موفرين سحابيين كانوا tools=false

### الفرق عن حالة الموديل المحلي (Phase 5.1)
الموديل المحلي احتاج نظام استدعاء أدوات نصي كامل من الصفر (`<tool_call>` tags +
parser صارم) لأنه مالوش أي دعم native للأدوات إطلاقًا. الوضع هنا مختلف جذريًا:
**الكود التقني الكامل لدعم الأدوات كان موجود وشغال بالفعل** لكل السبعة —
`buildOpenAiRequestBody()` في `NvidiaApiClient.java` بيبني صيغة OpenAI الرسمية
(`tools`/`tool_choice`) في الطلب، و`parseOpenAiSseStream()` بيستخرج
`tool_calls` من رد الـ streaming ويجمّعها في `ToolCall` objects صحيحة —
وكل السبعة موفرين بيستخدموا نفس الدالتين دول بالظبط (تأكيد مباشر من الكود، مش
تخمين). فتطبيق نظام نصي مماثل لحالة الموديل المحلي هنا كان هيبقى خطأ وازدواجية:
المشكلة الوحيدة كانت `ProviderCapabilities` بيضرب `tools(false)` فيمنع
`AgentExecutor` من تمرير أي `ToolDefinition` أصلًا رغم إن الـ API نفسه جاهز.

### الموفرين اللي اتفعّلوا
`CEREBRAS`, `SAMBANOVA`, `SCALEWAY`, `HUGGINGFACE`, `HYPERBOLIC`, `MORPH`,
`NOVITA` — كلهم `tools(false)` → `tools(true)` في `ProviderCapabilities.java`.
**ده تغيير قيمة إعداد بس، مفيش كود جديد اتكتب** — الـ request/response path
مكانش محتاج أي تعديل لأنه كان جاهز أصلًا.

### تنويه صريح ومهم
القيم الأصلية `tools(false)` للسبعة دول **مش موثَّقة بسبب واضح في الكود** —
مفيش تعليق يوضح إن الموفر ده رفض بارامتر `tools` فعليًا في تجربة سابقة، ولا
git history متاح في هذه البيئة يوضح متى/ليه اتحددت كده. حسب ما تأكد أثناء
هذه الجلسة، القيمة كانت افتراضية متحفظة من البداية ومحدتش فتشها فعليًا. يعني
التفعيل ده **افتراض معقول مبني على إن الكود التقني اللازم موجود وصحيح**،
مش تأكيد إن كل موفر من السبعة هيرد فعليًا بـ tool calls سليمة من موديلاته
المستضافة الفعلية. لازم اختبار ميداني حقيقي (مش نظري) لكل موفر على حدة، وخصوصًا:

- **`HUGGINGFACE`**: أعلى مخاطرة بين السبعة — الـ Inference API بتاعتها بتستضيف
  عائلات موديلات متنوعة جدًا، بعضها ممكن ميدعمش بارامتر `tools` إطلاقًا حتى لو
  كان الطلب متكوّن صح. ملحوظة إيجابية: `ModelCapabilities.resolveHuggingFace()`
  الموجود بالفعل كان معطَّل عمليًا (لأن `base.supportsTools` كانت `false` دايمًا)
  — دلوقتي بقى شغال فعليًا وبيقفل الأدوات تلقائيًا للموديلات اللي أسماءها توحي
  إنها base model مش instruct، فده تقليل مخاطرة جزئي متاح من غير أي كود إضافي.
- باقي الستة (`CEREBRAS`, `SAMBANOVA`, `SCALEWAY`, `HYPERBOLIC`, `MORPH`,
  `NOVITA`) كل واحد فيهم بيستضيف موديلات محدودة العدد نسبيًا (زي Llama/Qwen/
  Mistral المعروفين)، فمخاطرة الرفض أقل لكن لسه محتاجة تأكيد فعلي، مش افتراض.
- لو ظهر إن موفر معين فعليًا بيرفض `tools` (error من الـ API) أو بيتجاهله بصمت
  من غير أي tool_calls في الرد، الإصلاح السريع هو الرجوع بـ `tools(false)` لنفس
  الموفر ده تحديدًا في `ProviderCapabilities.java` — تغيير سطر واحد، مفيش داعي
  نلمس أي كود تاني.

## Phase 5.3 — إصلاح تجاوز حد التوكنات في الموديل المحلي (`LocalModelProvider.java`)

### السياق
بعد Phase 5.1، ظهر خطأ فعلي من محرك LiteRT-LM أثناء الاستخدام:
```
Status Code: 3
Input token ids are too long.
Exceeding the maximum number of tokens allowed: 11279 >= 4096
```
حصل حتى بعد مسح المحادثة بالكامل. الفحص أثبت إن السبب مش في التاريخ (history)
المحفوظ، لكن في تلات عيوب منطقية داخل `LocalModelProvider.java` نفسه — الملف
الوحيد المتغيّر في الـ phase دي.

### المشكلة الأولى (الأخطر) — أول رسالة كانت بتتبعت كاملة بدون قص
**قبل**: الحلقة في `trimHistoryForLocalModel()` كانت بتستخدم شرط
`if (!result.isEmpty() && cost > budgetRemaining)`. بما إن `result` بتبدأ فاضية،
الشرط ده مستحيل يتفعّل على أول رسالة (الأحدث) — فلو رسالة واحدة (مثلاً كود
متلصق أو تعليمة طويلة) لوحدها تجاوزت الميزانية بالكامل، كانت بتتبعت للمحرك
**كاملة** بغض النظر عن حجمها. ده السبب المباشر لخطأ الـ 11279 توكن حتى بعد
مسح المحادثة.

**بعد**: نفس الحلقة دلوقتي بتفرّق بين حالتين صريحتين:
- لو دي أول رسالة (`result.isEmpty()`) وتجاوزت الميزانية → **تُقص** (مش تُحذف
  ومش تتبعت كاملة) عن طريق دالة جديدة `truncateToBudget()`، وبتحتفظ بآخر جزء
  من النص (الأقرب للسؤال الفعلي عادةً) بدل أول جزء، مع رسالة توضيحية قصيرة
  في الأول تشرح إن جزء اتقص.
- لو رسالة أقدم وتجاوزت الميزانية المتبقية → تتوقف الحلقة عادي زي ما كانت
  (الرسائل الأحدث محفوظة بالفعل).
- `truncateToBudget()` بترجع نسخة جديدة من `ChatMessage` ومبتلمسش الكائن
  الأصلي في قائمة `messages` اللي جاية من الـ caller — لأن نفس القائمة ممكن
  تتبعت لموفر تاني (سحابي) في نفس الجلسة، فتعديلها في المكان كان هيسرّب قص
  خاص بالموديل المحلي لقائمة مشتركة.

### المشكلة الثانية — `RESERVED_FOR_TOOL_BLOCK_TOKENS = 900` رقم ثابت مش محسوب
**قبل**: الميزانية كانت بتحجز رقم ثابت (900 توكن) لكتلة الأدوات بغض النظر عن
حجمها الحقيقي — لو قائمة الأدوات كبرت (الكود توثيقي فعلاً في Phase 5.1 إنه
"لسه محتاج اختبار فعلي" بالظبط للسبب ده)، الحجز كان ممكن يبقى أقل من الحجم
الفعلي فيتجاوز الحد الأقصى، أو أكبر من اللازم فيقلل ميزانية الـ history بدون
داعي.

**بعد**: `buildPromptFromMessages()` بقت بتبني كتلة الأدوات (`buildToolPromptBlock`)
**أولًا**، تحسب حجمها الفعلي بـ `TokenBudgetChecker.estimateTokens()`، وبعدين
تحسب ميزانية الـ history المتبقية من الرقم الحقيقي ده (مش تخمين). نفس المنطق
اتطبّق على الـ system prompt نفسه، اللي مكانش بيتحسب في الميزانية أصلًا قبل
كده (كان بيتضاف بعد حساب الميزانية، مش قبلها).

### المشكلة الثالثة — مفيش فحص نهائي قبل إرسال البرومبت للمحرك
**قبل**: `sendChatRequest()` كان بيبني البرومبت ويبعته لـ `engineBridge.generate()`
على طول، من غير أي قياس نهائي — فلو في أي خطأ تقدير (heuristic 4-أحرف-لكل-توكن
مش دقيق 100%) أو أي مصدر تضخيم غير متوقع، المستخدم كان بياخد رسالة خطأ خام من
المحرك نفسه (`Input token ids are too long`) بدل رسالة مفهومة من التطبيق.

**بعد**: بعد بناء البرومپت مباشرة وقبل استدعاء `engineBridge.generate()`، بيتحسب
`TokenBudgetChecker.estimateTokens(prompt)` على النص الكامل الفعلي (system +
tools + history + رسالة المستخدم مع بعض)، ويتقارن بحد أمان
(`HARD_KV_CACHE_TOKENS - RESERVED_FOR_OUTPUT_TOKENS - FINAL_CHECK_SAFETY_MARGIN_TOKENS`
= 4096 - 512 - 64 = 3520 توكن). لو اتجاوز، الطلب **ميتبعتش للمحرك خالص** —
بيوصل `handler.onError()` برسالة واضحة بالعربي/الإنجليزي حسب لغة الواجهة تقول
"الرسالة دي طويلة جدًا على الموديل ده، جرّب تقصرها أو تبدأ محادثة جديدة" بدل
كود خطأ رقمي من المحرك.

### تفاصيل تقنية إضافية
- الثابت `RESERVED_FOR_TOOL_BLOCK_TOKENS` اتشال خالص من الكود (مبقاش له
  استخدام) — استُبدل بحساب ديناميكي في كل استدعاء.
- ثابت جديد `FINAL_CHECK_SAFETY_MARGIN_TOKENS = 64` بيغطي هامش خطأ التقدير
  التقريبي (4 أحرف/توكن) في الفحص النهائي، فوق الـ 512 توكن المحجوزة أصلًا
  للرد.
- التوقيع الداخلي لـ `trimHistoryForLocalModel()` اتغيّر من "مفيش parameters"
  لـ `(List<ChatMessage> messages, int budgetTokens)` — دالة private بس، مفيش
  أي مكان تاني في المشروع بيستدعيها (اتأكد بالبحث عن الاستخدامات قبل التعديل).

### ما لم يتغيّر (تعمّدًا)
- **مشكلة Gemma (401 Unauthorized)** و**Qwen3 (404)** المذكورتين في التحليل
  الأصلي **لم تُلمَسا في الـ phase دي** — دول في `LocalModelCatalog.java` و
  `LocalModelDownloader.java`، ملفين مختلفين تمامًا عن نطاق الإصلاح المطلوب
  هنا (بند 1-3 بس). تفاصيلهم في القسم الجاي.
- منطق `TokenBudgetChecker.java` نفسه (الـ heuristic، `DEFAULT_MAX_PAYLOAD_TOKENS`)
  ما اتغيرش — استُخدم كما هو، زي ما كان مستخدم في Phase 5.1.

### تحقق تم إجراؤه فعليًا
- قراءة الملف كامل قبل أي تعديل، سطر بسطر.
- التأكد إن `ChatMessage` عندها `setContent()`/`getConversationId()` وكونستركتور
  `(conversationId, content)` قبل استخدامهم في `truncateToBudget()`.
- بحث شامل في المشروع كله يتأكد إن الدوال المعدَّلة (`trimHistoryForLocalModel`,
  `buildPromptFromMessages`, `truncateToBudget`) مش مستخدمة من أي ملف تاني —
  كلهم private وخاصين بـ `LocalModelProvider.java` بس.
- التأكد إن مفيش أي مرجع متبقي للثابت المحذوف `RESERVED_FOR_TOOL_BLOCK_TOKENS`
  أو `HISTORY_TOKEN_BUDGET` غير مقصود (فحص `grep` بعد التعديل).

### لسه محتاج اختبار فعلي على الجهاز
- إن قيمة `FINAL_CHECK_SAFETY_MARGIN_TOKENS = 64` كافية فعليًا — دي تقديرية
  زي باقي الأرقام في هذا الملف، مبنية على نفس الـ heuristic التقريبي
  (4 أحرف/توكن) لـ `TokenBudgetChecker`، مش على تحقق فعلي من نسبة الخطأ
  الحقيقية لموديلات Qwen/Gemma tokenizers.
- إن رسالة التقصير التوضيحية (`"…(earlier part of this message was trimmed…)…"`)
  المُضافة لبداية النص المقصوص مش بتربّك الموديل الصغير أو تتفسّر غلط كجزء
  من طلب المستخدم — محتاج ملاحظة فعلية على جهاز.
- السيناريو الكامل (نفس الخطأ الأصلي: 11279 توكن، مسح محادثة، إلخ) لازم
  يتكرر فعليًا بعد التعديل على جهاز حقيقي للتأكد إن الخطأ اختفى نهائيًا ومفيش
  أي مسار تاني بيولّد نفس المشكلة.

## Phase 5.3 (تابع) — Gemma 401 / Qwen3 404 (لسه لم يُصلَح — تحليل فقط)

### تأكيد بالفحص الفعلي
- `LocalModelDownloader.java`: الطلب بيتبني بـ
  `new Request.Builder().url(model.getDownloadUrl())` **من غير أي
  `Authorization` header إطلاقًا**. مفيش أي مفهوم HF Token في الملف ده ولا في
  `LocalModelCatalog.java`. يعني أي موديل Gated فعليًا (زي Gemma على Hugging
  Face) هيرجّع 401 مضمون، مش احتمال.
- `LocalModelCatalog.java`: الكتالوج الحالي (النسخة اللي في الأرشيف ده) بيوثّق
  إنه اتراجع فعليًا بالبحث في يوليو 2026 وأسامي الملفات فيه دقيقة (`Qwen3-0.6B_
  multi-prefill-seq_q8_ekv4096.litertlm` وهكذا) — يعني مشكلة الـ 404 المذكورة
  في التحليل الأصلي **يُحتمل إنها كانت في نسخة كتالوج أقدم** واتصلحت من غير
  قصد ضمن إعادة كتابة الكتالوج في Phase 5. **ده لسه محتاج تأكيد فعلي بتحميل
  حقيقي** — الروابط بتاعة huggingface.co مش متاحة للفحص المباشر من بيئة العمل
  دي (مش ضمن الـ domains المسموحة للشبكة هنا).
- مفيش حقل `gated` أو أي آلية لتمييز الموديلات المقيّدة عن الحرة في الكتالوج
  الحالي.

### الحالة
القسم ده لسه **تحليل بس، مفيش كود اتغيّر**. الإصلاح (إضافة `gated` field +
دعم HF Token في الـ Downloader + إرفاقه كـ `Authorization: Bearer`) اتفق عليه
كبند رابع اختياري في خطة Phase 5.3 الأصلية، ولسه محتاج قرار صريح قبل التنفيذ.

## Phase 5.4 — حذف tool block + تبسيط قص الـ history + إصلاح Gemma/Qwen + موديلات جديدة

### السياق
بعد Phase 5.3، ظهر نفس الخطأ تقريبًا لكن بسيناريو أوضح بكتير:
```
User: hi
⚠️ This message is too long for "Qwen2.5 1.5B Instruct"
   (~11203 tokens estimated, limit ~3520 for this on-device model).
```
رسالة "hi" وحدها مستحيل توصل لـ 11203 توكن من نفسها. الفحص أثبت إن السبب هو
كتلة الأدوات (tool block) نفسها: `ToolRegistry` فيه **106 أداة مسجلة فعليًا**
(اتعد مباشرة من الكود، مش تقدير)، وحتى بأكثر صيغة مختصرة (سطر واحد لكل أداة)
كانت الكتلة دي وحدها بتكلف 8,000-11,000+ توكن — أكتر من ضعف الـ 4096 توكن
المتاحة بالكامل، **قبل ما نحسب أي حرف من الرسالة أو الـ system prompt**.
Phase 5.1 و5.3 حاولوا يحسبوا حجم الكتلة دي بدقة أكتر (Phase 5.3)، لكن المشكلة
الحقيقية مكنتش في الحساب — كانت في إن الكتلة نفسها أكبر من الميزانية الكاملة
بغض النظر عن أي تحسين في القياس.

### التغيير الأول — حذف tool block نهائيًا للموديل المحلي (`LocalModelProvider.java`)
**قبل**: `buildPromptFromMessages()` كانت بتبني كتلة أدوات مضغوطة من كل الـ106
أداة وترسلها مع كل رسالة.

**بعد**: `tools` بقى **يتجاهل بالكامل** — مفيش أي كتلة أدوات تتبني أو تتبعت
للموديل المحلي إطلاقًا، والموديل بيشتغل كـ plain chat فقط. اتشالت الدوال
`buildToolPromptBlock()`, `firstSentence()`, `extractParameterNames()` بالكامل
(كانت بقت dead code). اتسابت `parseToolCall()` والـ tags
(`<tool_call>`/`</tool_call>`) كـ"مش مستخدمة لكن غير ضارة" — لو الموديل
مقالش الصيغة دي بنفسه (مستبعد بدون تعليمة صريحة في البرومبت) هيتفسر كـ"مفيش
استدعاء أداة" زي المتوقع بالظبط، فمفيش خطر من إبقائها لمرحلة مستقبلية ممكن
فيها نبعت جزء صغير من الأدوات المرتبطة بالسياق بس (مش الـ106 كلهم).

**تحديث مرتبط في `ProviderCapabilities.java`**: `LOCAL_LLM.tools(true)` رجعت
`tools(false)` — عشان `AgentExecutor` يوقف عن بناء `List<ToolDefinition>`
لموديل مش هيستخدمها أصلًا، بدل ما يبنيها و`LocalModelProvider` يتجاهلها بصمت.

### التغيير الثاني — تبسيط قص الـ history (نتيجة تلقائية للتغيير الأول)
مفيش منطق جديد اتضاف هنا فعليًا — `trimHistoryForLocalModel()` من Phase 5.3
(تمشي من الأحدث للأقدم، تقص أول رسالة لو تجاوزت الميزانية لوحدها) **ظلت زي
ما هي بالظبط**. اللي اتغيّر هو إن الميزانية المتاحة ليها بقت كبيرة فعليًا
(قريبة من الـ 4096 توكن الكاملة ناقص الـ system prompt) بدل ما تكون شبه صفر
بسبب كتلة الأدوات. عمليًا كده الموديل بقى يقرأ أكبر قد ممكن من المحادثة
السابقة اللي يقدر يستوعبها، ولو رسالة واحدة (الحالية عادة) كبيرة أوي لوحدها،
تتقص (مش تتحذف) زي ما اتفق عليه في Phase 5.3.

### التغيير الثالث — تصحيح أسماء ملفات Gemma/Qwen (`LocalModelCatalog.java`)
بحثت فعليًا (مش افتراض) في مستودعات Hugging Face الفعلية وطلعت بالنتائج دي:

| الموديل | الاسم القديم (غلط) | الاسم الصحيح (اتأكد بالبحث) |
|---|---|---|
| Qwen3 0.6B | `Qwen3-0.6B_multi-prefill-seq_q8_ekv4096.litertlm` | `Qwen3-0.6B.litertlm` |
| Gemma 3 1B | `...q8_ekv4096.litertlm` | `...q4_ekv4096.litertlm` (اتعمل rename فعليًا على المستودع) |
| Gemma 3n E2B | مستودع `litert-community/gemma-3n-E2B-it-litert-lm` | مستودع `google/gemma-3n-E2B-it-litert-lm` (الاسم القديم كان **مستودع غلط بالكامل**، مش بس اسم ملف) |
| Qwen2.5 1.5B | — | ✅ اتأكد إنه صحيح فعليًا (شفت صفحة الملفات مباشرة) |

بالنسبة لـ Gemma 3 1B وGemma 3n E2B، الاتنين **gated فعليًا** (موثق صراحة في
صفحة الموديل على Hugging Face: `extra_gated_prompt` بيطلب قبول ترخيص gemma).
ده بيفسر الـ 401 الأصلي بغض النظر عن اسم الملف — حتى بعد تصحيح الاسم، من غير
HF Token الطلب هيفضل يرجّع 401.

**تنويه صريح**: التصحيحات دي مبنية على نتائج بحث فعلي (مش تخمين)، لكن ماعنديش
وصول مباشر لـ huggingface.co من بيئة العمل دي (مش ضمن الـ domains المسموحة
للشبكة) — يعني محدش قدر يتأكد بتحميل فعلي إن الروابط شغالة 100%. **لازم
اختبار تحميل حقيقي على جهاز فعلي** قبل الثقة الكاملة في الأسماء دي.

### التغيير الرابع — إضافة `boolean gated` + دعم HF Token
- `LocalModelCatalog`: إضافة حقل `gated` (`isGated()`) — `true` لكل موديلات
  Gemma (الاتنين الموجودين)، `false` لباقي التلاتة (Qwen3, Qwen2.5, Phi-4-mini
  الجديد) لأنهم عامة وميحتاجوش تسجيل دخول.
- `AiPreferences.java`: إضافة `getHuggingFaceToken()`/`setHuggingFaceToken()`/
  `hasHuggingFaceToken()` — بتتخزن في نفس `secureStore` المشفّر اللي بيتخزن
  فيه مفاتيح الـ API التانية (مش plain SharedPreferences)، لأنه credential
  بنفس الحساسية.
- `LocalModelManager.java`: إضافة `getContext()` — getter بسيط عشان
  `LocalModelDownloader` يقدر يوصل لـ `AiPreferences` من غير ما `LocalModelManager`
  نفسه يعرف حاجة عن الـ auth (فصل مسؤوليات).
- `LocalModelDownloader.java`: قبل أي محاولة تحميل، لو الموديل `isGated()`
  وملقيش token محفوظ، الطلب **بيفشل فورًا برسالة واضحة** ("يتطلب Hugging Face
  access token...") من غير ما يبعت أي طلب شبكة أصلًا. لو فيه token، بيتضاف
  `Authorization: Bearer <token>` كـ header على طلب التحميل (بما فيه طلبات
  الـ Range resume).
- `OfflineModelAdapter.java`: تحسين بسيط — سطر الـ tier تحت اسم كل موديل بقى
  يوضح "🔒 requires HF token" لو الموديل gated، عشان المستخدم يعرف قبل ما
  يحاول التحميل ويفاجئ بخطأ.

### التغيير الخامس — إضافة موديل Phi-4-mini Instruct (موديل أكبر، غير مقيد)
موديل جديد في الكتالوج: **Phi-4-mini Instruct** من Microsoft (3.8B parameter،
MIT license، **غير مقيد إطلاقًا** — مفيش gate ولا token مطلوب). حجمه الفعلي
المتأكد منه بالبحث المباشر في صفحة المستودع: 3.91 GB (ملف
`Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm`). ده أكبر موديل
في الكتالوج دلوقتي (أكبر من Qwen2.5 1.5B اللي كان الأكبر غير-Gemma قبل كده)،
وMicrosoft بتوثق قدرة برمجية/استدلال أقوى نسبيًا لـ Phi-4-mini مقارنة
بالموديلات الأصغر.

### تنويه مهم — سقف الـ 4096 توكن حد فعلي من المصدر، مش قيد من التطبيق
بحثت بشكل موسّع عن نسخ ekv أكبر (8192+) لأي موديل غير-Gemma على
`litert-community` (Qwen2.5-3B, Qwen2.5-Coder, DeepSeek-R1-Distill, إلخ).
**النتيجة**: كل موديل غير-Gemma منشور حاليًا على `litert-community` (Qwen2.5،
Qwen3، Phi-4-mini، DeepSeek-R1-Distill) بيستخدم نفس السياق الأقصى `ekv4096`
بالظبط، مؤكد ده من مصدر تقني مباشر (توثيق مكتبة `flutter_litert_lm` اللي
بتوصف الصيغة دي كـ"standard packaging format ... uses for every non-Gemma
model"). يعني **4096 توكن هو السقف الفعلي المتاح لأي موديل جاهز حاليًا**، مش
قرار تعسفي من الكود. طلب "موديلات بتوكنز أكبر" اتنفذ بمعنى "أكبر موديل متاح
حاليًا" (Phi-4-mini، 3.8B parameter) مش بمعنى "سياق أكبر من 4096"، لأن الأخيرة
مش متاحة فعليًا من أي مصدر رسمي دلوقتي. لو ظهرت نسخة `ekv8192` أو أكبر لاحقًا
من litert-community، تحديث `HARD_KV_CACHE_TOKENS` في `LocalModelProvider.java`
وربطها بموديل جديد في الكتالوج هيبقى تغيير مباشر وقتها.

### تحقق تم إجراؤه فعليًا
- عدّ الأدوات الفعلي في `ToolRegistry.java` (106 استدعاء `registry.register(new`)
  بدل الاعتماد على تقدير.
- بحث ويب مباشر ومتعدد لكل موديل في الكتالوج (Qwen3-0.6B, Gemma3-1B-IT,
  gemma-3n-E2B, Qwen2.5-1.5B, Phi-4-mini-instruct) للتأكد من اسم الملف
  والمستودع والحجم الفعلي، بدل نسخ القيم القديمة أو افتراض إنها لسه صحيحة.
- التأكد إن `litert-community/Gemma3-1B-IT` و`google/gemma-3n-E2B-it-litert-lm`
  فعلاً `gated` (شفت الـ `extra_gated_prompt` مباشرة في الـ README بتاعهم).
- بحث موسّع للتأكد من عدم وجود نسخة `ekv` أكبر من 4096 لأي موديل غير-Gemma
  منشور حاليًا، بدل افتراض إن سياق أكبر متاح ومحدش دوّر عليه.
- فحص `AiPreferences.java` للتأكد من نمط `secureStore` الموجود ومطابقة نفس
  النمط بدل اختراع آلية تخزين جديدة.
- بحث شامل عن كل استخدامات `buildToolPromptBlock`, `trimHistoryForLocalModel`,
  `LocalModelCatalog` (المُنشئ القديم بـ7 parameters) في المشروع كامل للتأكد
  من عدم وجود مرجع مكسور بعد التعديلات.
- فحص `OfflineModelAdapter.java` للتأكد إن كل الـ getters المستخدمة
  (`getDisplayName`, `getTier`, `getApproxSizeLabel`, `getMinRamGb`,
  `getCapabilityNote`) لسه بنفس التوقيع بعد إضافة `gated` field.

### لسه محتاج اختبار فعلي / شغل إضافي
- **تحميل فعلي على جهاز حقيقي** للتأكد إن أسماء الملفات المصححة (Qwen3,
  Gemma3-1B, Gemma-3n-E2B) شغالة 100% — البحث بيأكدها بقوة لكن معنديش وصول
  مباشر لـ huggingface.co من بيئة العمل دي للتحقق النهائي بتحميل فعلي.
- **حقل إدخال HF Token في الواجهة**: الـ backend كامل وجاهز
  (`AiPreferences.setHuggingFaceToken()`, دعمه في `LocalModelDownloader`)،
  لكن **لسه مفيش UI فعلي** (حقل نص + زرار حفظ) في `AiSettingsActivity`/
  `activity_ai_settings.xml` للمستخدم يدخل الـ token منه. محتاج شغل XML +
  ربط منفصل، اتأجل عمدًا لأنه محتاج ملف XML مش موجود في السياق الحالي وتعديل
  عليه بدون رؤيته مباشرة كان هيزود مخاطرة كسر الـ layout.
- اختبار فعلي إن استجابات الموديل المحلي (بدون أدوات خالص دلوقتي) لسه مفيدة
  بما يكفي كـ"plain chat assistant" — الـ trade-off صريح: فقدنا القدرة على
  استدعاء أدوات محليًا مقابل حل مشكلة تجاوز التوكنز بالكامل.
- التأكد إن `Phi-4-mini-instruct` (3.8B) فعليًا شغّال بسرعة مقبولة على أجهزة
  8GB RAM زي ما موصوف في `minRamGb` — رقم تقديري، مش مقاس فعليًا على جهاز.


## Phase 5.5 — إعادة تحقق من الكتالوج + محاولة إضافة Kimi/DeepSeek + توثيق Gemma 4

### السياق
طلب المستخدم: (1) "إصلاح" `Qwen2.5-0.5B-Instruct` اللي اتوصف في محادثة سابقة
إنه ملف تالف/غير متطابق، (2) إضافة موديلات زي Kimi وDeepSeek جديدة، (3) ملف
مضغوط بالتعديلات.

### النتيجة الأولى — "الإصلاح" المطلوب لـ Qwen2.5-0.5B غير ممكن، لكن مفيش عطل أصلًا
اتفحص `LocalModelCatalog.java` الحالي فوجدت إن الصف الموجود فعلًا اسمه
`QWEN2_0_5B_INSTRUCT` ويشاور على **Qwen2 (مش Qwen2.5)**. اتأكد بالبحث المباشر
في صفحة `litert-community/Qwen2-0.5B-Instruct` إن الملف `Qwen2_0.5B_Instruct.litertlm`
(647.4 MB) **لسه صحيح تمامًا ومطابق لما هو مكتوب بالكود** — مفيش عطل فعلي فيه.

الالتباس: مستودع **`litert-community/Qwen2.5-0.5B-Instruct`** (بالـ.5، جيل
أحدث) موجود فعلًا وungated، لكن بالفحص المباشر لقايمة ملفاته طلع إن **كل
الملفات فيه `.tflite` أو `.task` (MediaPipe LLM Inference API) — مفيش ولا ملف
`.litertlm` واحد**. محرك التطبيق (`LiteRtLmEngineBridge`) بيحمّل `.litertlm`
حصرًا، فمستحيل يتضاف المستودع ده من غير تغيير مسار تحميل مختلف تمامًا مش
موجود في التطبيق. اتسيب من غير إضافة، وأضفت توثيق واضح في جافادوك الصف
الموجود يشرح الفرق بين Qwen2 وQwen2.5 في السياق ده.

### النتيجة الثانية — Kimi وDeepSeek: بحث معاد، نفس النتيجة
- **Kimi (Moonshot AI K2)**: بحث ويب معاد أكّد نفس نتيجة الجلسات السابقة —
  مفيش أي تحويل `.litertlm`/LiteRT منشور من Moonshot AI ولا من المجتمع.
  عائلات الموديلات الموثقة رسميًا في LiteRT-LM هي Gemma, Llama, Phi-4, Qwen
  فقط. لا يوجد شيء اسمه Kimi في `litert-community` org أو مستودع GitHub
  الرسمي بتاع LiteRT-LM.
- **DeepSeek إضافي**: بحث عن أي مستودع DeepSeek تاني غير
  `DEEPSEEK_R1_DISTILL_QWEN_1_5B` الموجود بالفعل (ومُخفى/`@Deprecated` في
  الكتالوج). النتيجة الوحيدة المرتبطة كانت مناقشة مفتوحة على Hugging Face
  Inference Providers (`litert-community/Deepseek`, #2487) بتطلب من جوجل/
  المجتمع إضافة دعم — دي **مناقشة طلب، مش مستودع موديل فعلي** فيه ملفات
  يتحمّلها حد. مفيش حاجة تُضاف.

### النتيجة الثالثة — اكتشاف Gemma 4 (موديل جديد، غير مفعّل بعد)
اكتُشف مستودع `litert-community/gemma-4-E2B-it-litert-lm` أثناء البحث. الفرق
عن Gemma 3/3n (المشالين قبل كده بسبب الـ gating): توثيق جوجل الرسمي
(`developers.google.com/edge/litert-lm/models/gemma-4`) بيقول صراحة **"Gemma
4 is licensed under the Apache-2.0 license"** من غير أي إشارة لـ
`extra_gated_prompt` في أي مكان اتفحص. الملف العام اسمه `gemma-4-E2B-it.litertlm`
بحجم 2.58GB (مؤكد من نفس صفحة التوثيق الرسمية، اللي بتذكر كمان نسخة E4B بحجم
3.65GB بنفس نمط الاسم). حسب الـ model card بتاعه بيدعم function calling رسمي
— لو صح، ده أول موديل في الكتالوج يدعم استدعاء أدوات فعليًا.

**اتسيب عمدًا بدون تفعيل** (مش أُضيف كـ enum constant حي) — طلب المستخدم صراحة
كده لحد ما يتأكد أحد بتحميل فعلي إن الرابط شغال من غير HF token، لأن بيئة
العمل دي معندهاش وصول لـ huggingface.co (مش ضمن الـ domains المسموحة للشبكة)
عشان تجرب التحميل بنفسها. اتوثق بالتفصيل في جافادوك الكلاس + تعليق `NOTE`
بعد نهاية قايمة الـ enum، يوضح بالظبط القيم اللازمة (hfRepo, fileName, URL,
size) لو حد قرر يفعّله بعد التأكد.

### تحقق تم إجراؤه فعليًا
- بحث ويب مباشر لصفحة `litert-community/Qwen2-0.5B-Instruct` (الملف الحالي في
  الكتالوج) — تأكيد الاسم والحجم بدون تغيير.
- بحث ويب مباشر لصفحة `litert-community/Qwen2.5-0.5B-Instruct` (المستودع
  المطلوب) — تأكيد عدم وجود ملف `.litertlm` فيه إطلاقًا.
- بحث ويب متعدد لـ Kimi وDeepSeek على `litert-community` وGitHub الرسمي
  لـ LiteRT-LM.
- بحث ويب + قراءة مباشرة لصفحة `litert-community/gemma-4-E2B-it-litert-lm`
  وصفحة `google/gemma-4-E2B-it` وصفحة توثيق Google AI Edge الرسمية للتأكد من
  حالة الترخيص واسم/حجم الملف العام.
- فحص توازن الأقواس والتعليقات في `LocalModelCatalog.java` بعد التعديل
  (`{`/`}`, `(`/`)`, `/*`/`*/`) للتأكد من سلامة البنية النحوية.

### لسه محتاج اختبار فعلي / شغل إضافي
- **تحميل فعلي لـ Gemma 4 E2B** من جهاز حقيقي بدون HF token، للتأكد 100% إنه
  مش gated فعليًا قبل تفعيله كـ enum constant حي في الكتالوج.
- لو اتأكد إنه ungated وشغال: تفعيل الصف `GEMMA_4_E2B` (القيم موثقة بالكامل
  في الكود)، وبعدين تقييم منفصل لو التطبيق عايز يستفيد من قدرة function
  calling بتاعته (يتطلب مراجعة قرار Phase 5.4 بحذف tool block بالكامل من
  `LocalModelProvider`، مش مجرد إضافة صف في الكتالوج).
- نفس الملاحظة القديمة لسه قائمة: تحميل فعلي على جهاز حقيقي لباقي أسماء
  الملفات في الكتالوج (Qwen3, Qwen2.5-1.5B, DeepSeek-R1-Distill,
  Qwen2-0.5B) لأن بيئة العمل معندهاش وصول مباشر لـ huggingface.co.


## Phase 5.6 — تفعيل الموديل المخفي + بحث عميق عن أقوى موديل ungated متاح

### السياق
طلب المستخدم: (1) تفعيل الموديلات المخفية في الكتالوج أو حذفها نهائيًا لو
مش هتتفعّل، (2) بحث عميق ودقيق في Hugging Face/جوجل عن أقوى موديل حاليًا
يحقق نفس المعايير (ungated + `.litertlm` رسمي + سقف `ekv4096`) عشان يتضاف.

### التغيير الأول — تفعيل `DEEPSEEK_R1_DISTILL_QWEN_1_5B`
كان مخفي بـ `@Deprecated` + استبعاد صريح في `all()` بسبب تقارير ميدانية عن
ضعف في استخدام الأدوات وسلسلة تفكير `<think>` طويلة. اتقرر التفعيل بدل الحذف
للأسباب دي:
- الملف نفسه **لسه صحيح 100%** (اتأكد في Phase 5.5 من غير أي تعديل) — المشكلة
  الأصلية كانت في سلوك الموديل، مش في صحة الملف.
- **السبب الأصلي للإخفاء بقى غير منطبق فعليًا**: من Phase 5.4، الموديل
  المحلي بقى "plain chat" بالكامل — مفيش أي tool block بيتبني أو يتبعت له
  إطلاقًا (`LocalModelProvider.sendChatRequest` بيتجاهل `tools` تمامًا). يعني
  "ضعف DeepSeek في استخدام الأدوات" مبقاش سيناريو ممكن يحصل في التطبيق ده
  أصلًا، فمفيش سبب فعلي يستاهل إخفاء الموديل.

**تعديلات الكود**:
- `LocalModelCatalog.java`: شيل `@Deprecated` من الـ enum constant، وحدّث
  الجافادوك بتاعه ليشرح السبب الجديد للتفعيل. `all()` بقت ببساطة
  `Arrays.asList(values())` من غير أي فلترة.
- `LocalModelManager.getSelectedModel()`: شيل الشرط الخاص اللي كان بيرجّع
  الموديل الافتراضي لو المستخدم كان مختار DeepSeek قبل الإخفاء — دلوقتي
  بيتعامل معاه زي أي موديل تاني عادي.
- `LocalModelProvider.sendChatRequest()`: شيل السطر اللي كان بيحوّل طلب صريح
  لـ DeepSeek إلى `null` (يرجع للموديل الافتراضي) — طلب صريح لـ modelId بتاعه
  بقى يتم احترامه زي أي موديل تاني.

### التغيير الثاني — بحث عميق عن أقوى موديل ungated + إضافة `PHI4_MINI_INSTRUCT`
اتفتحت صفحة "Android Models" collection الرسمية بتاعة `litert-community`
مباشرة (17 موديل نص-توليد مُدرَجين فيها) وتمت مراجعتهم واحد واحد مقابل نفس
المعايير التلاتة (ungated + `.litertlm` فعلي + `ekv4096`):

| الموديل | النتيجة |
|---|---|
| Gemma 4 E2B/E4B | مرشح موثّق بالفعل من Phase 5.5، لسه غير مفعّل لحد التأكد من التحميل الفعلي |
| Gemma 3n E2B/E4B, Gemma3-1B-IT | Gemma = تاريخيًا gated، اتشالوا قبل كده لنفس السبب |
| **Phi-4-mini-instruct** | **✅ أقوى موديل ungated متاح — 3.8B parameter، MIT license، مؤكد** |
| Qwen2.5-1.5B-Instruct, DeepSeek-R1-Distill-Qwen-1.5B | موجودين بالفعل في الكتالوج |
| Qwen2.5-0.5B-Instruct | مفيش ملف `.litertlm` (اتأكد Phase 5.5) |
| Gemma2-2B-IT, SmolLM-135M, TinyLlama-1.1B, SmolVLM-256M, embeddinggemma, Gecko-110m | أصغر/أضعف أو مش موديل نصي عام (embedding/QA) |

**Phi-4-mini-instruct** طلع أقوى موديل متاح فعليًا حسب المعايير: 3.8B
parameter (أكبر عدد parameters لأي موديل ungated في القايمة كلها)، ترخيص MIT
واضح تمامًا (`License: mit` مباشر على صفحة المستودع، مفيش أي
`extra_gated_prompt` في أي مكان)، وملف `.litertlm` مؤكد:
`Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm` بحجم 3.91GB —
اتأكد من مصدرين مستقلين (جدول الملفات المباشر على المستودع، وصفحة release
notes لتطبيق تالت (Layla) بيستخدم نفس الرابط بالظبط لتكامله الخاص).

**ليه كان متشال قبل كده ولية السبب بقى مش منطبق**: Phase 5.4 شالته بسبب
تقارير ميدانية عن كراش/إعادة تشغيل التطبيق بعد استدعاء أداة (tool call)،
والتفسير المرجّح كان حجم الموديل الأكبر (3.8B) بيسبب native OOM. لكن **نفس
جلسة Phase 5.4 دي بالظبط شالت استدعاء الأدوات بالكامل من مسار الموديل
المحلي** — يعني "كراش بعد tool call" مبقاش سيناريو ممكن يحصل خالص لأي موديل
محلي حاليًا، بما فيهم Phi-4-mini. القلق الأصلي حول استهلاك الذاكرة (حجم
الموديل نفسه) لسه قائم وحقيقي، فاتعامل معاه بـ `minRamGb = 8` و
`DeviceTier.HIGH_END` (أعلى من أي موديل تاني في الكتالوج) بدل ما يتم تجاهله.

### تحقق تم إجراؤه فعليًا
- بحث + قراءة مباشرة لصفحة "Android Models" collection كاملة (17 موديل) على
  `litert-community` بدل الاعتماد على قايمة جزئية من الذاكرة.
- بحث + قراءة مباشرة لصفحة `litert-community/Phi-4-mini-instruct` — تأكيد
  `License: mit` ومفيش أي نص gating.
- بحث مزدوج (جدول ملفات المستودع + صفحة release notes لتطبيق Layla) لتأكيد
  اسم وحجم ملف `.litertlm` الدقيق (3.91GB) قبل استخدامه.
- بحث عن أي تقرير علني موثّق لمشكلة "Phi-4-mini crash after tool call" —
  محدش لقى مصدر عام (التقرير الأصلي كان من "field reports" داخلية في
  Phase 5.4، مش من مصدر منشور)، لكن السبب المنطقي في الجافادوك اتوثق بوضوح
  كـ"غير منطبق دلوقتي" مش كـ"اتنفى بالكامل".
- فحص شامل لكل استخدامات `DEEPSEEK_R1_DISTILL_QWEN_1_5B` و`@Deprecated` في
  المشروع (`grep` على `app/src/main/java/pro/sketchware/ai/`) للتأكد من تحديث
  كل نقطة كانت بتتعامل معاه كموديل مخفي (`LocalModelCatalog.all()`,
  `LocalModelManager.getSelectedModel()`, `LocalModelProvider.sendChatRequest()`).
- فحص عدم وجود أي مرجع لـ `PHI4_MINI_INSTRUCT` في باقي المشروع (متوقع، بما
  إن كل الأكواد التانية بتتعامل مع الكتالوج عن طريق getters عامة مش أسماء
  enum صريحة) — تأكيد إن الإضافة معزولة وآمنة.
- فحص توازن الأقواس المعقوصة/الأقواس العادية/تعليقات الكتلة في التلات ملفات
  المعدّلة (`LocalModelCatalog.java`, `LocalModelManager.java`,
  `LocalModelProvider.java`) بعد التعديل، ومقارنة `LocalModelProvider.java`
  بالنسخة الأصلية غير المعدّلة للتأكد إن أي فرق ظاهر في عدّ الأقواس كان موجود
  من الأصل (جوه تعليقات Javadoc) ومش نتيجة كسر بالتعديل.

### لسه محتاج اختبار فعلي / شغل إضافي
- **تحميل فعلي لـ Phi-4-mini-instruct** على جهاز حقيقي 8GB+ RAM للتأكد إن
  إعادة الإضافة دي فعلًا حلّت المشكلة ومفيش كراش، بما إن الفرضية إن السبب كان
  الأدوات لسه فرضية معقولة مش مؤكدة بمصدر خارجي.
- **تحميل فعلي لـ DeepSeek-R1-Distill-Qwen-1.5B** بعد التفعيل، للتأكد إن
  سلوك الـ `<think>` الطويل مقبول للمستخدم كـ"plain chat" بدون أدوات.
  Phase 5.4 وثّق تحفظ عن الجودة، مش عطل — التقييم النهائي محتاج استخدام فعلي.
- نفس الملاحظات القديمة القائمة: Gemma 4 E2B لسه محتاج تأكيد تحميل فعلي قبل
  التفعيل، وباقي أسماء ملفات الكتالوج محتاجة اختبار تحميل حقيقي على جهاز.


## Phase 5.7 — تفعيل Gemma 4 E2B بناءً على تعليمات صريحة من المستخدم

### السياق
في Phase 5.5/5.6، اتوثق مرشح `Gemma 4 E2B` بالتفصيل الكامل (الرابط، اسم
الملف، الحجم) لكن **اتسيب عمدًا غير مفعّل** لحد ما حد يتأكد بتحميل فعلي إنه
مش gated، لأن بيئة العمل معندهاش وصول لـ `huggingface.co` للتحقق بنفسها.

المستخدم طلب صراحة تفعيله دلوقتي. قبل التنفيذ، اتوضح للمستخدم الفرق الدقيق
بين حاجتين مختلفتين: (1) "مفيش نص gating ظاهر في صفحة الموديل" — ده **متأكد
منه فعليًا** بالفحص المباشر، و(2) "التحميل مش هيرجع 401 من غير token" — ده
**غير متأكد منه** لأن محدش قدر يجرب التحميل فعليًا. المستخدم اختار التفعيل
بناءً على التوثيق المتاح رغم كده (`gated=false`)، فاتنفذ الطلب.

### التغيير
- `LocalModelCatalog.java`: إضافة `GEMMA_4_E2B` كـ enum constant حي (آخر
  عنصر في القايمة):
  - `hfRepo`: `litert-community/gemma-4-E2B-it-litert-lm`
  - `fileName`: `gemma-4-E2B-it.litertlm` (الملف العام/CPU-portable، مش
    نسخ الـ NPU المخصصة لشرائح Intel/Qualcomm/Tensor الموجودة في نفس
    المستودع بأحجام مختلفة شوية)
  - الحجم: 2.58GB (من توثيق Google AI Edge الرسمي)
  - `gated = false` — **بناءً على تعليمات المستخدم الصريحة**، مش بناءً على
    تحقق كامل بتحميل فعلي.
  - جافادوك الصف موثّق بالتفصيل الكامل لتوضيح الفرق بين "مفيش نص gating
    ظاهر" و"التحميل هيشتغل فعليًا من غير token"، وبيوجّه لتغيير `gated` لـ
    `true` مباشرة لو ظهرت تقارير 401 من مستخدمين حقيقيين — مع الإشارة إن
    `LocalModelDownloader` عنده بالفعل معالجة جاهزة للموديلات الـ gated
    (بتفشل بسرعة برسالة واضحة بدل 401 خام محيّر).
- تحديث الجافادوك العلوي للفئة بإضافة قسم "Phase 5.7" يوضح القرار والسبب.
- إزالة الملاحظة القديمة (`NOTE` بعد نهاية القايمة) اللي كانت بتقول "لسه غير
  مفعّل، الطريقة لإضافته كالتالي" — بقت غير دقيقة بعد التفعيل الفعلي،
  اتستبدلت بإشارة مختصرة للصف الجديد.

### تنويه صريح مهم للمستخدم
القرار ده **مش نفس مستوى اليقين** اللي اتاح لـ `Phi-4-mini-instruct` (Phase
5.6) أو باقي موديلات الكتالوج. الفرق:
- Qwen/DeepSeek/Phi-4: ترخيص واضح (Apache-2.0/MIT) **بدون تاريخ معروف** لأي
  مستودع gated بنفس الترخيص ده على Hugging Face.
- Gemma عائلة ليها **تاريخ موثّق** من gating حتى مع أجيال سابقة (Gemma
  2/3/3n كلهم كانوا gated فعليًا، اتأكد وقتها بفحص `extra_gated_prompt`
  المباشر في الـ README بتاعهم). عدم ظهور نفس النص في صفحة Gemma 4 مؤشر
  قوي إنه اتغيّر فعليًا، لكنه مش تأكيد نهائي 100% زي باقي القايمة.

لو ظهر 401 لمستخدم حقيقي، الإصلاح بسيط: تغيير `false` لـ `true` في سطر
`gated` بتاع `GEMMA_4_E2B` — الكود التاني (`LocalModelDownloader`) already
جاهز للتعامل مع الحالة دي.

### تحقق تم إجراؤه فعليًا
- إعادة قراءة كل التوثيق المُجمّع من Phase 5.5 عن Gemma 4 E2B (اسم الملف،
  الحجم، حالة الترخيص) قبل التفعيل، بدل افتراض إنه لسه صحيح من غير مراجعة.
- فحص باقي المشروع (`grep`) للتأكد من عدم وجود أي مرجع صريح لـ `GEMMA_4_E2B`
  محتاج تحديث في مكان تاني غير `LocalModelCatalog.java` — مفيش، لأن باقي
  الكود بيتعامل مع الكتالوج عن طريق getters عامة.
- فحص توازن الأقواس المعقوصة/العادية وتعليقات الكتلة في
  `LocalModelCatalog.java` بعد التعديل، وعدّ الـ enum constants (6 عناصر،
  الأخير بس بـ `;`) للتأكد من سلامة البنية.
- مراجعة بصرية كاملة للصف الجديد وسياقه المباشر للتأكد من عدم وجود أسطر
  متبقية (orphaned) من التعديلات القديمة بعد الاستبدال.

### لسه محتاج اختبار فعلي / شغل إضافي
- **الأهم**: تحميل فعلي لـ `Gemma 4 E2B` على جهاز حقيقي للتأكد النهائي من
  `gated=false`. لو ظهر 401، غيّر السطر المذكور فوق لـ `true` فورًا.
- تقييم منفصل (خارج نطاق التغيير ده) لو التطبيق عايز يستفيد من قدرة function
  calling الرسمية بتاعة Gemma 4 — يتطلب مراجعة قرار Phase 5.4 بحذف tool
  block بالكامل من `LocalModelProvider`.
- نفس الملاحظات القديمة القائمة لباقي الكتالوج (تحميل فعلي على جهاز حقيقي
  لكل الموديلات).


## Phase 5.8 — تحميل الموديلات كـ Foreground Service + إشعار بأزرار Pause/Resume/Stop

### السياق
طلب المستخدم: التحميل يشتغل في الخلفية ومايتوقفش إلا لو المستخدم أوقفه هو
بنفسه، ويظهر في الإشعارات مع إمكانية Pause/Resume/Stop من الإشعار نفسه.

### المشكلة قبل التعديل
`LocalModelDownloader.download()` كان بينفّذ التحميل عبر OkHttp عادي من غير
أي Foreground Service ولا إشعار. النقل نفسه كان بيشتغل على thread pool بتاع
OkHttp (مش UI thread)، فكان بيكمل شغل فترة قصيرة حتى لو الشاشة اتقفلت — لكن
من غير Foreground Service ظاهر للنظام، أندرويد ممكن (وهيحصل فعليًا) يقتل
الـ process لاسترجاع الذاكرة بمجرد ما مفيش حاجة في الـ foreground، فيوقف
تحميل ملف بحجم جيجابايتات في نص الطريق من غير ما المستخدم يلاحظ.

كان فيه دليل مباشر على المشكلة دي في الكود نفسه: `AiSettingsActivity.onStop()`
كان بيوقف (pause) أي تحميل نشط فورًا لحظة ما الشاشة تختفي — يعني التحميل
كان بيتوقف تلقائيًا لمجرد تبديل التطبيق، عكس المطلوب تمامًا.

### التغيير
1. **`LocalModelDownloadService.java` (ملف جديد)**: Foreground Service حقيقي:
   - بيستدعي `LocalModelDownloader.download()` **بدون أي تغيير في منطق
     النقل نفسه** (نفس كود OkHttp/Range-header resume القديم حرفيًا — تم
     التأكد بـ `diff` مباشر إن `LocalModelDownloader.java` **لم يتغيّر
     إطلاقًا**).
   - بيعرض إشعار `ongoing` واحد لكل موديل بيتحمّل (معرّف فريد مُشتق من hash
     الـ model ID، عشان أكتر من تحميل بالتوازي يشتغلوا من غير تعارض).
   - الإشعار فيه progress bar حقيقي (`setProgress`) + زرين: **Pause/Resume**
     (بيتبدّل تلقائيًا حسب الحالة) و**Stop**. الأزرار دي `PendingIntent`
     بترجع لنفس الـ Service بـ action مختلف (`ACTION_PAUSE`/`ACTION_RESUME`/
     `ACTION_CANCEL`)، فمفيش داعي لـ `BroadcastReceiver` منفصل.
   - عند اكتمال التحميل أو فشله، الإشعار الـ`ongoing` بيتبدّل لإشعار عادي
     (قابل للمسح) بالنتيجة النهائية.
   - الـ Service بيفضل شغال (`startForeground`) طول ما فيه تحميل واحد على
     الأقل نشط أو موقّف مؤقتًا (paused لكن متتبّع)، وبيوقف نفسه
     (`stopSelf()`) بس لما آخر تحميل يخلص/يتلغي/يفشل.

2. **`AiSettingsActivity.java`**:
   - `startOfflineModelDownload()`: بدل النداء المباشر لـ
     `LocalModelDownloader.download()`، بقى بيستدعي
     `LocalModelDownloadService.start()` (بيبدأ الـ Service بـ Intent).
   - `onCancelDownload`/`onPauseDownload`: بقوا بينادوا
     `LocalModelDownloadService.cancel()`/`.pause()` بدل النداء المباشر
     لـ `LocalModelDownloader`، عشان حالة الـ Service تفضل متزامنة.
   - **الأهم**: `onStop()` **اتشال منها بالكامل** الكود اللي كان بيوقف
     (pause) أي تحميل نشط لحظة ما الشاشة تختفي — ده كان بالظبط السبب اللي
     بيمنع "التحميل في الخلفية" المطلوب. دلوقتي مغادرة الشاشة/تبديل
     التطبيق/قفل الشاشة **مالهاش أي تأثير على التحميل** — بس Pause/Stop
     الصريح (من الإشعار أو من الشاشة) هو اللي بيوقفه.
   - أُضيف **polling loop خفيف** (`Handler.postDelayed` كل ثانية) بديل
     عن الـ `DownloadCallback` المباشر القديم — بما إن التحميل بقى شغال
     جوه Service مستقل ممكن يكمل شغل والشاشة مقفولة تمامًا، الشاشة (لو
     مفتوحة) بتتابع تقدم التحميل عن طريق قراءة نفس الحالة المحفوظة اللي
     الـ Service نفسه بيكتبها في `LocalModelManager` (SharedPreferences)،
     مش عن طريق callback مباشر. الـ polling بيبدأ تلقائيًا في `onResume()`
     لو لقى تحميل شغال بالفعل، وبيتوقف في `onDestroy()` لمنع تسريب الذاكرة.
   - `onDestroy()`: أُضيف `downloadPollHandler.removeCallbacksAndMessages(null)`.

3. **`AndroidManifest.xml`**:
   - تسجيل `<service android:name="pro.sketchware.ai.offline.LocalModelDownloadService" .../>`
     بـ `android:foregroundServiceType="dataSync"` (احترازيًا — targetSdk
     الحالي 28 مش بيتطلبه بعد، لكن أضيف عشان الكود يفضل صالح لو الـ
     targetSdk اترفع لاحقًا من غير حاجة لمراجعة تانية).
   - إضافة `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />`
     — مطلوب إلزاميًا بجانب `foregroundServiceType="dataSync"` من API 34
     فصاعدًا (`FOREGROUND_SERVICE` العادي لوحده مبقاش كافي).
   - `POST_NOTIFICATIONS` كان بالفعل موجود ومطلوب مركزيًا في
     `PermissionsActivity` — مفيش تعديل إضافي محتاج هنا، بس اتوثق في
     جافادوك الـ Service إن رفض الصلاحية دي بيمنع ظهور الإشعار بس مش بيمنع
     التحميل نفسه من الاكتمال (`startForeground` بينجح والنقل بيكمل عادي).

4. **`OfflineModelAdapter.java`**: **لم يتغيّر إطلاقًا** — الأداتر أصلًا
   مصمم بشكل عام بالكامل، بيقرأ الحالة من `LocalModelManager.getState()`/
   `getProgressPercent()`/`isPaused()` (المصدر المشترك اللي الـ Service
   الجديد بيكتب فيه) وبينادي `callback.onPauseDownload/onResumeDownload/
   onCancelDownload` اللي اتعدّلوا في `AiSettingsActivity` بس. التصميم
   الموجود اتماشى تلقائيًا مع البنية الجديدة من غير أي لمسة.

### إصلاح مهم اتلقط أثناء الفحص الذاتي
أول نسخة من `LocalModelDownloadService` استخدمت
`Service.STOP_FOREGROUND_REMOVE` مباشرة من غير SDK check — الـ constant ده
**متاح بس من API 33 (Android 13)**، بينما `minSdk` بتاع المشروع هو 26. ده
كان هيسبب `NoSuchFieldError`/crash فوري على أي جهاز أقدم من Android 13. تم
اكتشافه وإصلاحه ذاتيًا قبل التسليم بإضافة `Build.VERSION.SDK_INT` check:
لو API 33+ يستخدم الـ constant الجديد، غير كده يستخدم
`stopForeground(true)` (الـ overload القديم، deprecated لكن شغال من API 26).

### تحقق تم إجراؤه فعليًا
- قراءة كاملة لـ `LocalModelDownloader.java` قبل التعديل لفهم البنية
  الحالية (activeCalls map، pause/cancel semantics، DownloadCallback).
- قراءة كاملة لـ `LocalModelManager.java` (مصدر الحالة المشتركة) و
  `AiBackgroundService.java` (كمرجع لنمط الـ Service/Notification المتبع
  بالفعل في المشروع، بدل اختراع نمط جديد).
- فحص `AndroidManifest.xml` بالكامل: تأكيد وجود `FOREGROUND_SERVICE`
  permission بالفعل، و`POST_NOTIFICATIONS`، وتحديد `targetSdk=28`/`minSdk=26`
  من `app/build.gradle` قبل تحديد نوع الـ foreground service المطلوب.
- فحص أيقونات drawable موجودة بالفعل (`ic_pause_white_48dp`,
  `ic_play_white_48dp`, `ic_stop_sign`, `ic_mtrl_download`) قبل استخدامها،
  بدل افتراض أسماء أو إنشاء أيقونات جديدة غير ضرورية — تأكيد إنها بالفعل
  مُشار لها في `OldResourceIdMapper.java` بنفس الأسماء.
- `diff` مباشر بين `LocalModelDownloader.java` قبل وبعد التعديل — تأكيد إنه
  **لم يتغيّر حرفًا واحدًا**.
- فحص توازن الأقواس المعقوصة/العادية وتعليقات الكتلة في كل الملفات
  المعدّلة/الجديدة (`LocalModelDownloadService.java`,
  `AiSettingsActivity.java`) قبل وبعد التعديل، ومقارنة عدد الأقواس بالنسخة
  الأصلية للتأكد إن الفرق منطقي (زيادة تتناسب مع الكود المُضاف فقط).
- التحقق من صلاحية بنية XML لملف `AndroidManifest.xml` بعد التعديل
  (`xml.etree.ElementTree.parse`) للتأكد من عدم وجود أخطاء syntax.
- مراجعة توافق كل استدعاء API جديد مع `minSdk=26` تحديدًا (`getSystemService(Class)`
  من API 23 آمن، `STOP_FOREGROUND_REMOVE` من API 33 مش آمن واتصلح، باقي
  الفحوصات محاطة بـ `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O` بالفعل
  بنفس نمط `AiBackgroundService` الأصلي).
- فحص `OfflineModelAdapter.java` بالكامل للتأكد إنه مش محتاج أي تعديل —
  البنية العامة بتاعته (بيقرأ من `LocalModelManager` المشترك، مش من
  callback مباشر) تتوافق تلقائيًا مع أي جهة بتكتب في نفس الـ state.

### لسه محتاج اختبار فعلي / شغل إضافي
- **تشغيل فعلي على جهاز حقيقي**: بدء تحميل، تصغير التطبيق/قفل الشاشة،
  التأكد إن الإشعار بيتحدّث وإن التحميل فعليًا مكمل في الخلفية من غير
  توقف. هذا التغيير معتمد على فهم دقيق لسلوك Android's foreground service
  lifecycle، لكن السلوك الفعلي (خصوصًا على أجهزة/ROMs معينة بتطبّق battery
  optimization عدواني زي بعض أجهزة Xiaomi/Huawei) محتاج تأكيد ميداني.
- **اختبار Pause/Resume من الإشعار نفسه** (مش بس من داخل الشاشة) للتأكد إن
  الـ `PendingIntent`s بترجع للـ Service صح وتحدّث الحالة والإشعار زي
  المتوقع.
- **اختبار تحميلين بالتوازي** (لو المستخدم بدأ تحميل موديلين في نفس الوقت)
  للتأكد إن كل واحد بإشعاره المستقل وإن Pause واحد مايأثرش على التاني.
- **اختبار سيناريو رفض صلاحية POST_NOTIFICATIONS**: التأكد إن التحميل فعليًا
  بيكمل لحد الآخر حتى من غير إشعار ظاهر، زي ما هو موثّق في الجافادوك.

## Phase 6 — Migration من LiteRT-LM إلى llama.cpp (بداية فقط — غير مكتملة)

### السبب
تحقيق في هذه الجلسة أكّد إن سقف الـ 4096 توكن لكل موديل أوفلاين (`ekv4096`
مبني في ملف `.litertlm` وقت التصدير من Google/litert-community) هو السبب
الجذري وراء إن الأوفلاين بياخد أصغر مجموعة أدوات ممكنة (`TINY` tier في
`ToolRegistry`) بدل ما يستجيب للأدوات زي الأونلاين. تم التأكد إن مفيش نسخة
أكبر من `ekv4096` منشورة لأي موديل في الكتالوج الحالي، فالحل المعتمد هو
تغيير المحرك بالكامل لـ llama.cpp اللي بياخد حجم الـ context (`n_ctx`)
كمعامل وقت التحميل مش وقت التصدير. الخطة الكاملة المعتمدة موجودة في
سجل الجلسة (plan mode)، مش منسوخة هنا بالكامل — هذا القسم يوثّق فقط اللي
اتنفذ فعليًا وحدود التنفيذ.

### ما تم تنفيذه فعليًا في هذه الجلسة
- **ملف جديد**: `LlamaCppEngineBridge.kt` — بديل `LiteRtLmEngineBridge.kt`
  (**اتحذف**)، بنفس الـ contract الخارجي (`GenerationCallback`,
  `HistoryTurn`, `generate()`, `cancelGeneration()`, `close()`) عشان
  `LocalModelProvider` يحتاج أقل تغييرات ممكنة. بيعتمد على `external fun`
  surface (`LlamaNative`) لسه **مش متصل بأي كود native حقيقي** — الـ
  `System.loadLibrary("llama-android")` هيرمي `UnsatisfiedLinkError` لحد
  ما موديول `:llama` الحقيقي يتضاف.
- **`LocalModelProvider.java`**: `HARD_KV_CACHE_TOKENS` بقى
  `LlamaCppEngineBridge.CONTEXT_SIZE_TOKENS` (8192 بدل 4096)، مع تعديل
  الهوامش (`RESERVED_FOR_OUTPUT_TOKENS`, `FINAL_CHECK_SAFETY_MARGIN_TOKENS`,
  `MAX_KNOWLEDGE_BLOCK_TOKENS`) تناسبيًا، واستبدال كل استدعاءات/إشارات
  `LiteRtLmEngineBridge` بـ `LlamaCppEngineBridge`.
- **`ProviderCapabilities.java`**: `LOCAL_LLM.maxContext` بقى `8_192`. تم
  توثيق مشكلة مفتوحة: بما إن 8192 > `ToolRegistry.TINY_CONTEXT_THRESHOLD_TOKENS`
  (4096)، الموديل المحلي هيترقّى تلقائيًا لـ `MEDIUM` tier (~20 أداة) عبر
  `AgentExecutor`'s الموحّد لكل الموفرين — ده **لم يتم التحقق منه ولا
  تثبيته عمدًا** في هذه الجلسة (الخطة المعتمدة بتقول الاتساع في الأدوات
  يكون follow-up منفصل)، فلازم يتفحص قبل الاعتماد عليه.
- **`app/build.gradle`**: حذف `com.google.ai.edge.litertlm:litertlm-android`.
  موديول `:llama` (الـ native module الحقيقي) **لسه مش مضاف** — راجع
  التعليق الجديد في الملف لتفاصيل الخطوات الناقصة.
- **`AndroidManifest.xml`**: حذف `<uses-native-library>` الخاصة بـ OpenCL
  (كانت لـ LiteRT-LM's GPU backend، مش لازمة لـ llama.cpp CPU-only v1).
- **`AiSettingsActivity.java` + `activity_ai_settings.xml`**: مفتاح GPU
  acceleration اتعطّل (`setEnabled(false)`, `setChecked(false)`) بدل ما
  يفضل شغال وهو مالوش تأثير فعلي — كان هيوهم المستخدم إنه فعّل حاجة.
- تحديثات توثيق (javadoc/تعليقات) في `LocalModelCatalog.java`,
  `LocalModelManager.java`, `AiProvider.java` لتعكس المحرك الجديد.

### قيود حقيقية في هذه البيئة — لسه ناقص وحرج
هذه الجلسة اكتشفت إن بيئة التنفيذ **معندهاش**:
1. Android NDK مثبّت — الكود الجديد **لم يُبنَ أبدًا**، تمامًا زي حال
   `LiteRtLmEngineBridge.kt` الأصلي.
2. وصول لـ github.com (403 policy denial مؤكد) — يعني **موديول
   `examples/llama.android` الحقيقي من llama.cpp لسه مش متضاف للمشروع**.
   `LlamaNative`'s الـ `external fun` surface في الملف الجديد **افتراض
   غير متحقق منه**، لازم يتراجع عليه لما حد يقدر يستنسخ المصدر الحقيقي.
3. وصول لـ huggingface.co (403 policy denial مؤكد) — يعني
   **`LocalModelCatalog.java` لسه بيأشر على ملفات `.litertlm` قديمة**، مش
   `.gguf`. الأوفلاين **مش هيشتغل فعليًا بعد هذا التغيير** لحد ما حد يراجع
   الكتالوج بروابط GGUF حقيقية متحقق منها (تم توثيق ده بوضوح في class
   javadoc الخاص بـ `LocalModelCatalog.java` — عمدًا لم يتم تخمين أي رابط،
   الكتالوج نفسه فيه تاريخ موثّق لروابط اتخمنت غلط وسببت 404).

### الخلاصة
هذا commit بيمثّل **الطبقة الجاهزة من التعديلات في Java/Kotlin/Gradle/XML
فقط** — مش migration مكتمل وقابل للتشغيل. الثلاث نقاط الحرجة فوق (NDK،
موديول llama.cpp الحقيقي، كتالوج GGUF متحقق منه) لازم تتعمل في بيئة عندها
وصول شبكة كامل + Android SDK/NDK قبل ما الأوفلاين يرجع يشتغل فعليًا.


## Phase 6 (تابع) — GitHub بقى شغال + استنساخ الموديول الحقيقي + إعادة تصميم جوهرية

### تحديث الوضع
بعد كتابة القسم فوق، اتضح إن **GitHub بقى شغال فعليًا** في الجلسة دي (خلاف
اللي كان موثّق فوق) — `git clone`/`git ls-remote` حقيقيين على
`ggml-org/llama.cpp` نجحوا. **Hugging Face لسه محجوب** (403 policy denial
اتأكد تاني)، **والـ NDK لسه مش موجود**.

### اكتشافات حرجة من قراءة المصدر الحقيقي
بعد استنساخ `examples/llama.android` فعليًا وقراءة `ai_chat.cpp` و
`InferenceEngineImpl.kt` الحقيقيين:

1. **الـ API الحقيقي مختلف جذريًا عن التخمين الأول**: مش JNI منخفض المستوى
   زي ما افترضت أول مرة، لكن API عالي المستوى (`com.arm.aichat.InferenceEngine`:
   `loadModel()`, `setSystemPrompt()`, `sendUserPrompt(): Flow<String>`).
2. **المحرك state-ful بالكامل من جوّه**: بيحتفظ بالمحادثة (KV cache +
   تاريخ الأدوار + ردوده هو نفسه) داخل الكود الـ native، مع context-shifting
   تلقائي. مفيش API عام لحقن تاريخ كامل مرة واحدة — بس رسالة جديدة كل مرة.
   ده تعارض مباشر مع تصميم `LocalModelProvider` القديم (إعادة بعت الـ
   history كامل في كل نداء). **بناءً على اختيار المستخدم الصريح**، اتعمل
   إعادة تصميم لـ `LocalModelProvider` عشان يتكيّف مع نمط المحرك: بيتتبع هل
   الطلب الجديد امتداد طبيعي (`isContinuationOf` — prefix match) للمحادثة
   اللي المحرك عارفها، وبيبعت بس الرسالة الأحدث في كل الحالتين (مش هيستيقظ
   الـ history القديمة لو حصل انقطاع — قيد موثّق صراحة، مش نسيان).
3. **`DEFAULT_CONTEXT_SIZE = 8192`** في `ai_chat.cpp` نفسه — نفس الرقم اللي
   اخترناه بالظبط، صدفة كويسة، من غير ما نحتاج نعدل حاجة.
4. **تعارض minSdk حقيقي**: الموديول الرسمي محدد `minSdk = 33` و
   `abiFilters = [arm64-v8a, x86_64]` بس، بينما المشروع كله `minSdk = 26`
   وعنده 4 معماريات. **القرار المتخذ (الخيار الموصى به)**: مفيش رفع لـ
   minSdk العام للمشروع (كان هيقصي كل مستخدمي Android 8-12 من كل حاجة، مش
   بس الأوفلاين) — بدل كده:
   - `tools:overrideLibrary="com.arm.aichat"` في المانفست لتفادي خطأ الدمج.
   - `LlamaCppEngineBridge.isDeviceSupported()` بيتفحص فعليًا (SDK_INT >= 33
     + معمارية 64-bit) قبل أي محاولة استخدام للمحرك — أي جهاز أقدم بياخد
     رسالة خطأ واضحة بدل كراش.

### ما تم تنفيذه فعليًا في هذا التحديث
- **`third_party/llama.cpp`**: إضافة submodule حقيقي (shallow، مثبّت على
  commit `571d0d5` / tag `b10068`)، ~162MB.
- **`settings.gradle`**: إضافة `:llama` موديول يأشر على
  `third_party/llama.cpp/examples/llama.android/lib`.
- **`app/build.gradle`**: `implementation project(":llama")` فعليًا (بدل
  التعليق النظري السابق)، مع توثيق تعارض minSdk.
- **`AndroidManifest.xml`**: إضافة `<uses-sdk tools:overrideLibrary=.../>`.
- **`LlamaCppEngineBridge.kt`**: **إعادة كتابة كاملة** ضد الـ API الحقيقي
  (`AiChat.getInferenceEngine`, `InferenceEngine`)، مع `isDeviceSupported()`.
- **`LocalModelProvider.java`**: إعادة تصميم كبيرة — حذف `HistoryTurn`/
  `trimHistoryForLocalModel`/الـ history budget بالكامل، إضافة
  `isContinuationOf`/`lastSentMessages`/`forceReset`، تبسيط `PromptAssembly`
  لرسالة واحدة + system instruction بس، إضافة فحص `isDeviceSupported()` في
  بداية `sendChatRequest`.

### لسه ناقص (بلا تغيير عن القسم السابق)
Hugging Face لسه محجوب (كتالوج GGUF مش متحقق منه)، والـ NDK لسه مش موجود
(مفيش بناء فعلي اتعمل). التصميم الجديد **غير مُختبر فعليًا** — الـ
continuation heuristic (`isContinuationOf`) والتكامل مع الموديول الحقيقي
لازم يتفحصوا على جهاز حقيقي بمجرد توفر NDK.
