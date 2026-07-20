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
