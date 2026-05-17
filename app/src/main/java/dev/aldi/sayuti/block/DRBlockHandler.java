package dev.aldi.sayuti.block;

import java.util.ArrayList;
import java.util.HashMap;

public class DRBlockHandler {

    public static void addBlocks(ArrayList<HashMap<String, Object>> arrayList) {
        addViewBlocks(arrayList);
        addStringBlocks(arrayList);
        addStringOperatorBlocks(arrayList);
        addSharedPreferencesBlocks(arrayList);
        addBasicComponentBlocks(arrayList);
        addIntentPutExtraBlocks(arrayList);
    }

    public static void addViewBlocks(ArrayList<HashMap<String, Object>> arrayList) {
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("name", "setBackgroundResource");
        hashMap.put("type", " ");
        hashMap.put("code", "%s.setBackgroundResource(R.drawable.%s);");
        hashMap.put("color", "#4A6CD4");
        hashMap.put("palette", "-1");
        hashMap.put("spec", "%m.view setBackgroundResource %m.drawable");
        arrayList.add(hashMap);
    }

    public static void addStringBlocks(ArrayList<HashMap<String, Object>> arrayList) {
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("name", "concatenateVarString");
        hashMap.put("type", " ");
        hashMap.put("code", "%s += %s;");
        hashMap.put("color", "#EE7D16");
        hashMap.put("palette", "-1");
        hashMap.put("spec", "%m.varStr append %s");
        arrayList.add(hashMap);
    }

    public static void addStringOperatorBlocks(ArrayList<HashMap<String, Object>> arrayList) {
        // placeholder - no active blocks currently
    }

    public static void addSharedPreferencesBlocks(ArrayList<HashMap<String, Object>> arrayList) {
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("name", "getBooleanSharedPreferences");
        hashMap.put("type", "b");
        hashMap.put("code", "%s.getBoolean(%s, false)");
        hashMap.put("color", "#2CA5E2");
        hashMap.put("palette", "-1");
        hashMap.put("spec", "%m.file getBoolean key %s");
        arrayList.add(hashMap);

        hashMap = new HashMap<>();
        hashMap.put("name", "putBooleanSharedPreferences");
        hashMap.put("type", " ");
        hashMap.put("code", "%s.edit().putBoolean(%s, %s).apply();");
        hashMap.put("color", "#2CA5E2");
        hashMap.put("palette", "-1");
        hashMap.put("spec", "%m.file putBoolean key %s value %b");
        arrayList.add(hashMap);

        hashMap = new HashMap<>();
        hashMap.put("name", "getIntSharedPreferences");
        hashMap.put("type", "d");
        hashMap.put("code", "%s.getInt(%s, 0)");
        hashMap.put("color", "#2CA5E2");
        hashMap.put("palette", "-1");
        hashMap.put("spec", "%m.file getInt key %s");
        arrayList.add(hashMap);

        hashMap = new HashMap<>();
        hashMap.put("name", "putIntSharedPreferences");
        hashMap.put("type", " ");
        hashMap.put("code", "%s.edit().putInt(%s, (int) %s).apply();");
        hashMap.put("color", "#2CA5E2");
        hashMap.put("palette", "-1");
        hashMap.put("spec", "%m.file putInt key %s value %d");
        arrayList.add(hashMap);
    }

    public static void addBasicComponentBlocks(ArrayList<HashMap<String, Object>> arrayList) {
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("name", "intentGetBoolean");
        hashMap.put("type", "b");
        hashMap.put("code", "getIntent().getBooleanExtra(%s, false)");
        hashMap.put("color", "#2CA5E2");
        hashMap.put("palette", "-1");
        hashMap.put("spec", "Activity getExtra key %s");
        arrayList.add(hashMap);

        hashMap = new HashMap<>();
        hashMap.put("name", "intentGetDouble");
        hashMap.put("type", "d");
        hashMap.put("code", "getIntent().getDoubleExtra(%s, 0.0)");
        hashMap.put("color", "#2CA5E2");
        hashMap.put("palette", "-1");
        hashMap.put("spec", "Activity getExtra key %s");
        arrayList.add(hashMap);
    }

    public static void addIntentPutExtraBlocks(ArrayList<HashMap<String, Object>> arrayList) {
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("name", "intentPutExtraBoolean");
        hashMap.put("type", " ");
        hashMap.put("code", "%s.putExtra(%s, %s);");
        hashMap.put("color", "#2CA5E2");
        hashMap.put("palette", "-1");
        hashMap.put("spec", "%m.intent putExtra key %s value %b");
        arrayList.add(hashMap);

        hashMap = new HashMap<>();
        hashMap.put("name", "intentPutExtraDouble");
        hashMap.put("type", " ");
        hashMap.put("code", "%s.putExtra(%s, (double) %s);");
        hashMap.put("color", "#2CA5E2");
        hashMap.put("palette", "-1");
        hashMap.put("spec", "%m.intent putExtra key %s value %d");
        arrayList.add(hashMap);
    }
}
