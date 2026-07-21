# مهم: ملفات مصدرية ناقصة من Git (تمنع البناء من نسخة نظيفة)

## المشكلة التي كشفها الـ CI
أول workflow بناء حقيقي (على نسخة نظيفة من المستودع) فشل عند تجميع Kotlin بأخطاء
`Unresolved reference 'build'` / `Unresolved reference 'BuildSettings'`.

**السبب الجذري:** كان في `.gitignore` النمط `**/build/` الذي يُقصد به تجاهل مجلدات ناتج
Gradle — لكنه كان **يتطابق أيضاً مع حزم مصدرية اسمها `build`**، فمنع رفع ملفات مصدر حقيقية
إلى Git نهائياً. البناء المحلي عندك يعمل فقط لأن الملفات موجودة على قرصك؛ أي نسخة نظيفة
(CI، جهاز جديد، أو متعاون آخر) تفتقدها ولا تستطيع التجميع.

## ما تم إصلاحه تلقائياً في هذا الـ commit
`.gitignore` صُحّح: أُبقي تجاهل مجلدات ناتج Gradle (`app/build/`, `llama/build/`, الجذر)،
وأُضيف استثناء `!**/src/**/build/` ليصبح بالإمكان تتبّع أي مجلد `build` مصدري تحت `src/`.
(تم التحقق: ملفات المصدر لم تعد مُتجاهَلة، ومجلدات ناتج Gradle لا تزال مُتجاهَلة.)

## ما يجب أن تفعله أنت (الملفات موجودة على جهازك فقط)
هذه حزم مخصّصة لمشروعك (نسخة DayDream مختلفة عنها فعلاً — تحقّقنا)، فلا يمكن استرجاعها من أي
مصدر آخر. من جهازك الذي يبني بنجاح:

```bash
git checkout claude/ai-offline-tools-response-qkqk1i
git pull                    # يسحب إصلاح .gitignore
git add app/src/main/java/mod/hey/studios/build/ \
        app/src/main/java/mod/jbk/build/ \
        app/src/main/java/mod/pranav/build/ \
        app/src/main/java/pro/sketchware/ai/tools/build/
git status                  # تأكّد من ظهور الملفات أدناه كـ new file
git commit -m "Add build-system source files previously hidden by .gitignore"
git push
```

نصيحة: بعد `git pull`، شغّل `git status` أولاً — سيُظهر **كل** الملفات التي صارت قابلة
للتتبّع الآن (قد تكون هناك مجلدات `build` مصدرية أخرى غير المذكورة أدناه). أضِف كل ما يظهر
منها تحت `src/`.

## قائمة الحزم/الملفات الناقصة المؤكَّدة (الحد الأدنى المطلوب للتجميع)
| الحزمة | الأصناف التي يستدعيها الكود |
|---|---|
| `mod/hey/studios/build/` | `BuildSettings` (ثوابت مخصّصة مثل `SETTING_PARALLEL_ECJ`, `SETTING_DEXER_R8`, `SETTING_AUTO_CLEAN_AFTER_BUILD` — غير موجودة في نسخة DayDream) |
| `mod/jbk/build/` | `BuiltInLibraries` (+ الصنف الداخلي `BuiltInLibrary`), `BuildProgressReceiver` |
| `mod/jbk/build/compiler/bundle/` | `AppBundleCompiler` |
| `mod/jbk/build/compiler/dex/` | `DexCompiler` (نسختك تحتوي خطّاف الـ desugaring — راجع تعليق `app/build.gradle`) |
| `mod/jbk/build/compiler/resource/` | `ResourceCompiler` |
| `mod/pranav/build/` | `JarBuilder`, `R8Compiler` |
| `pro/sketchware/ai/tools/build/` | `BuildTools`, `CompileTools`, `BuildRepairTool`, `AdvancedBuildTool`, `RunAndVerifyOnDeviceTool` (أدوات المساعد الذكي الخاصة بالبناء) |

بمجرد رفع هذه الملفات، سيتقدّم الـ CI إلى ما بعد تجميع Kotlin. (كل الطبقات السابقة — تحميل
NDK، تراخيص SDK، وبناء llama.cpp الأصلي — تعمل بنجاح بالفعل، دامت ~13 دقيقة قبل الوصول
لهذا الخطأ.)
