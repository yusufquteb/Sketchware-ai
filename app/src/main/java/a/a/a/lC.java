package a.a.a;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class lC {
    public static DB a;

    // FIX: Each project file requires a disk read + AES decrypt (oB.h -> oB.d + oB.c).
    // Doing this sequentially for every project on a single thread made the projects
    // list take seconds (or longer) to load once the user had many projects, which
    // felt like — and could contribute to — a freeze/ANR on the Main page.
    // We now decrypt/parse projects in parallel using a bounded thread pool sized to
    // the device's core count, while keeping the exact same per-project logic and
    // error handling (a corrupt/unreadable project is skipped, not fatal).
    public static ArrayList<HashMap<String, Object>> a() {
        final String str = "project";
        ArrayList<HashMap<String, Object>> arrayList = new ArrayList<>();
        File[] listFiles = new File(wq.n()).listFiles();
        if (listFiles == null) {
            return arrayList;
        }

        int threadCount = Math.max(2, Math.min(listFiles.length, Runtime.getRuntime().availableProcessors()));
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        List<Future<HashMap<String, Object>>> futures = new ArrayList<>(listFiles.length);

        try {
            for (final File file : listFiles) {
                futures.add(pool.submit(new Callable<HashMap<String, Object>>() {
                    @Override
                    public HashMap<String, Object> call() {
                        try {
                            if (new File(file, str).exists()) {
                                oB oBVar = new oB(); // oB is not guaranteed thread-safe; use one instance per task
                                String path = file.getAbsolutePath() + File.separator + str;
                                HashMap<String, Object> a = vB.a(oBVar.a(oBVar.h(path)));
                                if (yB.c(a, "sc_id").equals(file.getName())) {
                                    return a;
                                }
                            }
                        } catch (Throwable e) {
                            Log.e("ERROR", e.getMessage(), e);
                        }
                        return null;
                    }
                }));
            }

            for (Future<HashMap<String, Object>> future : futures) {
                try {
                    HashMap<String, Object> result = future.get(30, TimeUnit.SECONDS);
                    if (result != null) {
                        arrayList.add(result);
                    }
                } catch (Throwable e) {
                    Log.e("ERROR", "Failed to load a project", e);
                }
            }
        } finally {
            pool.shutdown();
        }

        return arrayList;
    }

    public static HashMap<String, Object> a(String str) {
        for (HashMap<String, Object> stringObjectHashMap : a()) {
            if (yB.c(stringObjectHashMap, "my_sc_pkg_name").equals(str) && yB.b(stringObjectHashMap, "proj_type") == 1) {
                return stringObjectHashMap;
            }
        }
        return null;
    }

    public static void a(Context context, String str) {
        File file = new File(wq.c(str));
        if (file.exists()) {
            oB oBVar = new oB();
            oBVar.a(file);
            oBVar.b(wq.d(str));
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(wq.g());
            stringBuilder.append(File.separator);
            stringBuilder.append(str);
            oBVar.b(stringBuilder.toString());
            stringBuilder = new StringBuilder();
            stringBuilder.append(wq.t());
            stringBuilder.append(File.separator);
            stringBuilder.append(str);
            oBVar.b(stringBuilder.toString());
            stringBuilder = new StringBuilder();
            stringBuilder.append(wq.d());
            stringBuilder.append(File.separator);
            stringBuilder.append(str);
            oBVar.b(stringBuilder.toString());
            stringBuilder = new StringBuilder();
            stringBuilder.append(wq.e());
            stringBuilder.append(File.separator);
            stringBuilder.append(str);
            oBVar.b(stringBuilder.toString());
            oBVar.b(wq.b(str));
            oBVar.b(wq.a(str));
            stringBuilder = new StringBuilder();
            stringBuilder.append("D01_");
            stringBuilder.append(str);
            new DB(context, stringBuilder.toString()).a();
            stringBuilder = new StringBuilder();
            stringBuilder.append("D02_");
            stringBuilder.append(str);
            new DB(context, stringBuilder.toString()).a();
            stringBuilder = new StringBuilder();
            stringBuilder.append("D03_");
            stringBuilder.append(str);
            new DB(context, stringBuilder.toString()).a();
            stringBuilder = new StringBuilder();
            stringBuilder.append("D04_");
            stringBuilder.append(str);
            new DB(context, stringBuilder.toString()).a();
        }
    }

    public static void a(Context context, boolean z) {
        if (a == null) {
            a = new DB(context, "P15");
        }
    }

    public static void a(String str, HashMap<String, Object> hashMap) {
        File file = new File(wq.n());
        if (!file.exists()) {
            file.mkdirs();
        }
        str = wq.c(str);
        str = str + File.separator + "project";
        String a = vB.a(hashMap);
        oB oBVar = new oB();
        try {
            oBVar.a(str, oBVar.d(a));
        } catch (Throwable e) {
            Log.e("ERROR", e.getMessage(), e);
        }
    }

    public static String b() {
        int parseInt = Integer.parseInt("600") + 1;
        for (HashMap<String, Object> stringObjectHashMap : a()) {
            parseInt = Math.max(parseInt, Integer.parseInt(yB.c(stringObjectHashMap, "sc_id")) + 1);
        }
        return String.valueOf(parseInt);
    }

    public static HashMap<String, Object> b(String str) {
        Throwable e;
        oB oBVar = new oB();
        HashMap<String, Object> hashMap = null;
        try {
            String c = wq.c(str);
            if (!new File(c).exists()) {
                return null;
            }
            String path = c + File.separator + "project";
            HashMap<String, Object> a = vB.a(oBVar.a(oBVar.h(path)));
            try {
                return !yB.c(a, "sc_id").equals(str) ? null : a;
            } catch (Exception e2) {
                e = e2;
                hashMap = a;
                Log.e("ERROR", e.getMessage(), e);
                return hashMap;
            }
        } catch (Exception e3) {
            e = e3;
            Log.e("ERROR", e.getMessage(), e);
            return hashMap;
        }
    }

    public static void b(String str, HashMap<String, Object> hashMap) {
        File file = new File(wq.c(str));
        if (file.exists()) {
            String path = file + File.separator + "project";
            oB fileUtil = new oB();
            try {
                HashMap<String, Object> a = vB.a(fileUtil.a(fileUtil.h(path)));
                if (yB.c(a, "sc_id").equals(str)) {
                    if (hashMap.containsKey("isIconAdaptive")) {
                        a.put("isIconAdaptive", hashMap.get("isIconAdaptive"));
                    }
                    if (hashMap.containsKey("custom_icon")) {
                        a.put("custom_icon", hashMap.get("custom_icon"));
                    }
                    a.put("my_sc_pkg_name", hashMap.get("my_sc_pkg_name"));
                    a.put("my_ws_name", hashMap.get("my_ws_name"));
                    a.put("my_app_name", hashMap.get("my_app_name"));
                    a.put("sc_ver_code", hashMap.get("sc_ver_code"));
                    a.put("sc_ver_name", hashMap.get("sc_ver_name"));
                    a.put("sketchware_ver", hashMap.get("sketchware_ver"));
                    a.put("color_accent", hashMap.get("color_accent"));
                    a.put("color_primary", hashMap.get("color_primary"));
                    a.put("color_primary_dark", hashMap.get("color_primary_dark"));
                    a.put("color_control_highlight", hashMap.get("color_control_highlight"));
                    a.put("color_control_normal", hashMap.get("color_control_normal"));
                    fileUtil.a(path, fileUtil.d(vB.a(a)));
                }
            } catch (Throwable e) {
                Log.e("DEBUG", e.getMessage(), e);
            }
        }
    }

    public static String c() {
        ArrayList<HashMap<String, Object>> var0 = a();
        ArrayList<Integer> projectIndices = new ArrayList<>();

        for (HashMap<String, Object> stringObjectHashMap : var0) {
            String workspaceName = yB.c(stringObjectHashMap, "my_ws_name");
            if (workspaceName.equals("NewProject")) {
                projectIndices.add(1);
            } else if (workspaceName.indexOf("NewProject") == 0) {
                try {
                    projectIndices.add(Integer.parseInt(workspaceName.substring(10)));
                } catch (Exception ignored) {
                }
            }
        }

        projectIndices.sort(new IntegerComparator());
        int var3 = 0;

        for (int nextProjectIndex : projectIndices) {
            int var5 = var3 + 1;
            if (nextProjectIndex == var5) {
                var3 = var5;
            } else {
                if (nextProjectIndex == var3) {
                    continue;
                }
                break;
            }
        }

        if (var3 == 0) {
            return "NewProject";
        } else {
            return "NewProject" + (var3 + 1);
        }
    }

    public static void d() {
        for (String str : a.c().keySet()) {
            a(str, a.g(str));
        }
        a.a();
    }

    private static class IntegerComparator implements Comparator<Integer> {

        @Override
        public int compare(Integer first, Integer second) {
            return first.compareTo(second);
        }
    }
}
