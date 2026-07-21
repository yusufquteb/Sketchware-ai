# تقرير التدقيق الشامل (Audit) — جلسة يوليو 2026

نطاق الفحص: **1271 ملف Java/Kotlin (~283 ألف سطر)**، منها 179 ملف (~50 ألف سطر) في حزمة
المساعد الذكي `pro.sketchware.ai`. المنهجية: مسحات آلية منهجية على كامل الشجرة
(deprecated APIs، أنماط التهنيج/ANR، كشف الملفات الميتة والمكررة بالتحليل الاستاتيكي)
+ قراءة يدوية عميقة لنواة المساعد الذكي (AgentExecutor، ToolRegistry، الـ providers،
جسر الموديل المحلي، أدوات الـ lint).

---

## القسم 1 — عيوب حقيقية تم إصلاحها في هذه الجلسة

### 1.1 زر الإيقاف لا يوقف حلقة الـ failover (`AgentExecutor.java`)
`cancel()` يعتمد على `executor.shutdownNow()` لإرسال interrupt يكسر أي انتظار داخل
حلقة الوكيل — لكن سطر انتظار الـ failover (`Thread.sleep(800)`) كان يبتلع
`InterruptedException` بصمت (`catch (InterruptedException ignored2) {}`)، فيضيع الـ
interrupt flag وتكمل الحلقة عبر قائمة الـ failover كاملة (عشرات المزودين × الموديلات)
بعد ما المستخدم ضغط إيقاف. **الإصلاح**: استعادة الـ interrupt والخروج فوراً عبر مسار
الإلغاء الطبيعي (`postCancelled`).

### 1.2 `LlamaCppEngineBridge.close()` كان "يقتل" محرك الأوفلاين لبقية عمر التطبيق
- كان يستدعي `engine.destroy()` — لكن المحرك singleton على مستوى الـ process
  (`InferenceEngineImpl.instance` يُخزَّن ولا يُصفَّر أبداً)، و`destroy()` يلغي الـ
  coroutine scope الداخلي نهائياً. النتيجة: أي إغلاق لمحادثة (حتى `onCleared()` عابر
  للـ ViewModel) كان سيجعل **كل** محادثة أوفلاين لاحقة تفشل بصمت حتى إعادة تشغيل
  التطبيق. **الإصلاح**: `cleanUp()` (يفرّغ الموديل ويعيد المحرك لحالة قابلة لإعادة
  الاستخدام) بدل `destroy()`.
- كان يستخدم `runBlocking` — وهو قابل للاستدعاء من الـ main thread عبر
  `ChatViewModel.onCleared()` → `LocalModelProvider.shutdown()` = خطر تهنيج (ANR)
  مباشر أثناء انتظار الـ dispatcher أحادي الخيط بتاع المحرك. **الإصلاح**: التنظيف
  على خيط خلفي.
- الـ `CoroutineScope` الخاص بالجسر نفسه لم يكن يُلغى أبداً في `close()` — تسريب.
  **الإصلاح**: `scope.cancel()`.

### 1.3 أداة الـ lint في المساعد كانت تنصح المستخدمين بـ API مهمل
`LintTools` كانت توصي حرفياً باستخدام `AsyncTask` (محذوف من API 30) كحل لمشكلة
`Thread.sleep`. **الإصلاح**: التوصية أصبحت `Handler(Looper.getMainLooper()).postDelayed()`
أو `ExecutorService`. (`CodeAnalysisTools` كانت توصياتها صحيحة بالفعل.)

### 1.4 انحراف التوثيق عن السلوك الفعلي في اختيار أدوات الموديل المحلي
ثلاثة مواضع (javadoc + تعليقات) كانت تدّعي أن الموديل المحلي يستقبل "7 أدوات أساسية
فقط" — بينما الواقع منذ رفع السياق لـ 8192: `getToolsForContextBudget(8192)` يرقّيه
تلقائياً لفئة MEDIUM (~21 أداة)، ولا أحد يستدعي `getEssentialTools()` في المسار الحي
إطلاقاً (deprecated). **القرار المعتمد في هذا التدقيق**: الترقية لـ MEDIUM **مقصودة
ومعتمدة** — تخدم الهدف الأصلي (استجابة الأوفلاين للأدوات مثل الأونلاين)، والكتلة
المضغوطة لـ 21 أداة في حدود 1-2K توكن تقديري داخل ميزانية 8192، مع حارسين ضد التجاوز
(فحص pre-flight في `LocalModelProvider` + حد المحرك نفسه). التوثيق صُحح ليطابق الواقع.

### 1.5 ملفات ميتة (غير مُشار إليها من أي كود/XML/منفست) — حُذفت
| الملف | الأسطر |
|---|---|
| `pro/sketchware/util/GlobFileSearch.java` | 145 |
| `pro/sketchware/ai/chat/ui/TypingIndicatorAnimator.java` | 119 |
| `pro/sketchware/utility/search/GlobPattern.java` | 34 |
| `pro/sketchware/utility/BinaryExecutor.java` | 33 |
| `pro/sketchware/git/GitProjectWorkflow.java` | 19 |
| `pro/sketchware/utility/search/FileSearchResult.java` | 17 |
| `pro/sketchware/project/ProjectFileNode.java` | 15 |

(`PatternBackgroundView` بدا ميتاً في مسح الكود لكنه مستخدم من
`activity_icon_creator.xml` — Custom View عبر XML — فبقي كما هو. تعليق
`chat_typing_dot.xml` المشير للـ animator المحذوف صُحح.)

---

## القسم 2 — نتائج مفحوصة وسليمة (لا تحتاج تغيير)

- **شبكة الـ AI clients ليست مصدر تهنيج**: كل استدعاءات `.execute()` المتزامنة
  (20+ client) تجري داخل `ExecutorService` خلفي (`AgentExecutor` أحادي الخيط،
  و`AiSettingsActivity` عبر executor خاص). لا يوجد I/O شبكة على الـ main thread.
- **بقية `Thread.sleep` في نواة الـ AI**: كلها على خيوط خلفية مع معالجة interrupt
  سليمة (`ToolExecutionGuard` retry backoff، `ModelManager` exponential backoff) —
  باستثناء الحالة المصلحة في 1.1.
- **مشاركة OkHttp client**: الـ clients السحابية تشارك singleton مقصوداً (موثق في
  `AiApiClient.shutdown()`) — استبدال client أثناء failover لا يسرّب موارد شبكة.
- **الأدوات المضافة للمساعد**: `buildToolBlock` يعرض أسماء الباراميترات الحقيقية
  المطلوبة (إصلاح ميداني سابق موثق)، و`parseToolCall` له مسار صارم + fallback مقيد
  بأسماء الأدوات المعروضة فعلاً — تصميم سليم.

---

## القسم 3 — ديون تقنية موثقة عمداً بدون تغيير (تغييرها يحتاج قرار/جهد منفصل)

مرتبة بالأولوية المقترحة:

1. **`ChatMessage` مكرر** (`ai/models/ChatMessage` مستخدم في 45 ملفاً و
   `ai/chat/model/ChatMessage` في 6) — توحيدهما refactor واسع يلمس واجهة الـ
   providers كلها؛ يستحق جلسة مخصصة.
2. **`startActivityForResult`/`onActivityResult` (deprecated)** في 28 ملفاً —
   الانتقال لـ Activity Result API ميكانيكي لكنه واسع ويحتاج اختباراً يدوياً لكل شاشة.
3. **`getExternalStorageDirectory` (deprecated منذ API 29)** في 37 ملفاً — يعمل حالياً
   لأن التطبيق يطلب `MANAGE_EXTERNAL_STORAGE`؛ أي تغيير هنا قرار منتج (نموذج تخزين
   التطبيق كله مبني عليه).
4. **`AsyncTask`** في ملفين legacy (`mod/hilal/saif/...`) و**`ProgressDialog`** في
   10 ملفات — معظمها كود Sketchware الموروث؛ استبدالها آمن لكنه يحتاج تمريرة UI
   مخصصة.
5. **تكرارات بنيوية موروثة**: `BuiltInLibraryManager`/`BuiltInLibraryUtils` مكرران
   بين `pro.sketchware.util.library` و`a/a/a` (الشجرة المعتّمة الموروثة)، وكذلك
   `ThemeManager`، `SimpleHighlighter`، `DependencyDownloadAdapter/Item`،
   `CompileErrorCapture`، `SystemLogPrinter`، `CircularDependencyDetector` — الشجرة
   المعتّمة `a/a/a` لا يُنصح بلمسها إلا مع تغطية اختبار.
6. **علة Phi-4 "يخرج من المشروع" بعد tool call** — من عهد LiteRT-LM، لم تُعد تُختبر
   بعد الانتقال لـ llama.cpp؛ تحتاج استنساخاً ميدانياً على المحرك الجديد.
7. **كتالوج الموديلات الأوفلاين** لا يزال يشير لملفات `.litertlm` — يحتاج إعادة كتابة
   بروابط GGUF متحقق منها (كان محجوباً عن هذه البيئة، موثق في `LocalModelCatalog`).

---

## القسم 4 — ملاحظات على عملية البناء

- لا يوجد أي CI على GitHub (لا `.github/workflows`) — يُنصح بإضافة workflow واحد
  على الأقل (assembleDebug) ليكشف أخطاء الـ sync/البناء قبل الدمج بدل اكتشافها يدوياً.
- سلسلة إصلاحات دمج موديول `:llama` موثقة بالكامل في `llama/build.gradle` (الهيدر)
  و`CHANGES.md` Phase 6.
- إصلاح desugaring لمشاريع المستخدمين على الأجهزة القديمة (readAllBytes/API 29)
  مُفعَّل الآن فعلياً في `DependencyResolver.compileJar()`.

---

## القسم 5 — جولة ثانية بناءً على بلاغات المستخدم (مراجعة نقدية أعمق)

### عيوب مؤكدة أُصلحت في هذه الجولة
1. **"300 موديل تتحول لـ 1"**: `AgentExecutor` كان يمسح كاش موديلات المزوّد **بالكامل**
   عند فشل موديل واحد قديم، فترجع الواجهة للقائمة الثابتة (موديل واحد لـ NVIDIA).
   الآن يُحذف الموديل الفاسد فقط (`AiPreferences.removeCachedModel`).
2. **لا توجد علامة على الموديل النشط**: كارت `item_model.xml` لم يكن `checkable` —
   نداء `setChecked()` كان no-op صامت. الآن إطار + أيقونة ✓ بلون primary.
3. **تهنيج صفحة المشاريع + توقف إدخال SDK**: الأدابتر كان يطلق حتى 3 خيوط خام لكل
   صف عند كل bind (قراءة أيقونة + كتابتي ميتاداتا) — فتح الكيبورد فوق الصفحة يعيد
   الـ bind لكل الصفوف = عاصفة خيوط. الآن pool مشترك محدود + إصلاح الميتاداتا مرة
   واحدة + حارس ضد الـ recycled holders.
4. **أدوات المناظر (add/modify/remove/batch/replace_subtree) لم تعمل يوماً فعلياً**:
   - خريطة الأنواع كانت مختلفة عن ثوابت `ViewBean` الحقيقية في كل شيء تقريباً
     (TextView=2 بينما 2=HScrollView؛ أنواع 19-22 خارج النطاق أصلاً — الآلية
     المرجحة لعلة "الخروج من المشروع بعد الأداة" القديمة).
   - كتابة `text` كنص مسطح بينما `ViewBean` يحتاج `TextBean` object.
   - هيكل `children` متداخل غير موجود في الصيغة المسطحة الحقيقية (parent/parentType).
   الخمس أدوات أُعيد بناؤها فوق مسار `ViewBeanParser`/`saveViewBeans` المُثبت
   (نفس مسار `add_view_xml` الشغال) بثوابت صحيحة وحقول هرمية حقيقية.
5. **الفقاعة الفارغة + رسائل المنسق غير المفهومة**: الرد المكوّن من tool-call فقط كان
   يترك فقاعة مساعد فارغة — الآن تُحذف. رسالة "🔧 اسم_الأداة" الجافة صارت
   "Running …" عند البدء + سطر نتيجة مفهوم (✅ ملخص / ❌ خطأ) عند الاكتمال.
6. **اختيار الأداة من الشريط الجانبي يرسل مباشرة** الآن — إلا القوالب التي تحتوي
   `[placeholder]` يجب أن يكملها المستخدم فتبقى في خانة الإدخال.
7. **`global_search` تقوّت**: باراميترات `case_sensitive`/`use_regex`/`file_filter`
   كانت مدعومة في التنفيذ لكن **غير معلنة في الـ schema** فلم يكن الموديل يعرفها
   إطلاقاً؛ أُعلنت + سقف 200 نتيجة مع ملاحظة توجيه بدل إغراق الـ context + معالجة
   قيمة filter غير صحيحة برسالة مفيدة.
8. **CI أُضيف أخيراً**: `.github/workflows/android-build.yml` يبني
   `assembleAndroid26Debug` مع submodules على كل push/PR.

### تقييم نقدي: نظام الـ Pulse
البنية سليمة (حوار Continue/Cancel بعدّاد 10 ثوان + auto-continue، `CountDownLatch`
على خيط الوكيل، تنظيف صحيح عند القرار). لم يُعثر على عيب وظيفي فيه. ملاحظتان
تحسينيتان (غير منفذتين): العدّاد يعيش على Handler الـ Activity — عند إدارة الشاشة
أثناء العرض يُفقد الحوار ويُعتمد على auto-continue، وهو سلوك مقبول لكنه غير مصرح
به للمستخدم؛ ونص الخطة يعرض raw plan بدون تنسيق.

### تقييم نقدي: منطق تسلسل الـ pipeline
التسلسل الحالي (TokenOptimizer → قدرات المزوّد → اختيار فئة الأدوات → إرسال →
parsing → ToolExecutionGuard (منع تكرار + retry واحد للأخطاء العابرة) → تغذية
النتيجة → تكرار حتى انتهاء الأدوات أو حد أمان 30) **منطقي وسليم بنيوياً**، مع
failover مرتب عبر (مزوّد، موديل). أضعف نقطة كانت رسائل الحالة للمستخدم (أُصلحت
أعلاه) وليست الترتيب نفسه.

### تقليل الأدوات مقابل أدوات أقوى (توصية، غير منفذة)
الكتالوج 106+ أداة فيه تجزئة زائدة فعلاً — مرشحون واضحون للدمج في أدوات أقوى:
- عائلة القراءة (`read_file`/`get_screen_source`/`get_compile_logs`/...) → أداة
  `read` واحدة بباراميتر نوع.
- عائلة القوائم (`list_files`/`list_activities`/`list_libraries`/...) → `list`
  واحدة بباراميتر kind.
- إبقاء الأدوات "الفعلية" المتخصصة (build/الأدوات الكاتبة) منفصلة للأمان.
التنفيذ يتطلب تمريرة migration على البرومبتات والـ manifest — جلسة مخصصة.

### سؤال "أرفع صورة تصميم وينفذها المساعد؟" — الإجابة الحالية: لا
`FileAttachManager` يرسل **اسم** ملف الصورة كنص فقط (`[Image: x.png]`) — البكسلات
لا تصل للموديل أبداً، رغم أن عدة مزوّدين معلَّمين `vision(true)` في
`ProviderCapabilities`. تفعيلها يتطلب: تضمين الصورة base64 في جسم طلب المزوّدين
الداعمين + دعم أجزاء صور في `ChatMessage` + معاينة في الواجهة — ميزة متوسطة الحجم
قابلة للتنفيذ في جلسة مخصصة، والبنية التحتية (أعلام القدرات + مسار الإرفاق) جاهزة
نصفياً.
