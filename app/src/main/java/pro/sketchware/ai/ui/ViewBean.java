package pro.sketchware.ai.ui;

import java.util.HashMap;
import java.util.Map;

/**
 * ViewBean is a data representation of a UI widget in the Sketchware Design Editor.
 * Ported from Sketchware-IA methodology.
 */
public class ViewBean {
    public String id;
    public String type; // e.g., "LinearLayout", "TextView", "Button"
    public int width = -1; // -1: MATCH_PARENT, -2: WRAP_CONTENT
    public int height = -1;
    public String text = "";
    public String textColor = "#000000";
    public String backgroundColor = "#FFFFFF";
    public int textSize = 14;
    public String gravity = "center";
    public String layoutGravity = "center";
    public int padding = 0;
    public int marginTop = 0;
    public int marginBottom = 0;
    public int marginLeft = 0;
    public int marginRight = 0;
    
    public java.util.List<ViewBean> children = new java.util.ArrayList<>();
    public String parentId = null;
    
    // Store additional custom properties
    public Map<String, String> customProps = new HashMap<>();

    public ViewBean(String id, String type) {
        this.id = id;
        this.type = type;
    }

    public void setProperty(String key, String value) {
        switch (key) {
            case "android:layout_width":
                this.width = parseDimension(value);
                break;
            case "android:layout_height":
                this.height = parseDimension(value);
                break;
            case "android:text":
                this.text = value;
                break;
            case "android:textColor":
                this.textColor = value;
                break;
            case "android:background":
                this.backgroundColor = value;
                break;
            case "android:textSize":
                this.textSize = Integer.parseInt(value.replaceAll("[^0-9]", ""));
                break;
            case "android:gravity":
                this.gravity = value;
                break;
            case "android:layout_gravity":
                this.layoutGravity = value;
                break;
            default:
                customProps.put(key, value);
                break;
        }
    }

    private int parseDimension(String value) {
        if ("match_parent".equalsIgnoreCase(value)) return -1;
        if ("wrap_content".equalsIgnoreCase(value)) return -2;
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return -2;
        }
    }
}
