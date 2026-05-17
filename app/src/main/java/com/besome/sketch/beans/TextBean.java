package com.besome.sketch.beans;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.annotations.Expose;

import java.util.Objects;

import a.a.a.nA;

public class TextBean extends nA implements Parcelable {
    public static final Parcelable.Creator<TextBean> CREATOR = new Parcelable.Creator<>() {
        @Override
        public TextBean createFromParcel(Parcel source) {
            return new TextBean(source);
        }

        @Override
        public TextBean[] newArray(int size) {
            return new TextBean[size];
        }
    };

    public static int IME_OPTION_DONE = 6;
    public static int IME_OPTION_GO = 2;
    public static int IME_OPTION_NEXT = 5;
    public static int IME_OPTION_NONE = 1;
    public static int IME_OPTION_NORMAL = 0;
    public static int IME_OPTION_SEARCH = 3;
    public static int IME_OPTION_SEND = 4;
    public static int INPUT_TYPE_NUMBER_DECIMAL = 8194;
    public static int INPUT_TYPE_NUMBER_SIGNED = 4098;
    public static int INPUT_TYPE_NUMBER_SIGNED_DECIMAL = 12290;
    public static int INPUT_TYPE_PASSWORD = 129;
    public static int INPUT_TYPE_PHONE = 3;
    public static int INPUT_TYPE_TEXT = 1;
    public static String TEXT_FONT = "default_font";
    public static int TEXT_TYPE_BOLD = 1;
    public static int TEXT_TYPE_BOLDITALIC = 3;
    public static int TEXT_TYPE_ITALIC = 2;
    public static int TEXT_TYPE_NORMAL;
    @Expose
    public String hint;
    @Expose
    public int hintColor;
    @Expose
    public boolean hasHintColor;
    @Expose
    public String resHintColor;
    @Expose
    public int imeOption;
    @Expose
    public int inputType;
    @Expose
    public int line;
    @Expose
    public int singleLine;
    @Expose
    public String text;
    @Expose
    public int textColor;
    @Expose
    public boolean hasTextColor;
    @Expose
    public String resTextColor;
    @Expose
    public String textFont;
    @Expose
    public int textSize;
    @Expose
    public int textType;

    public TextBean() {
        text = "";
        textSize = 12;
        textType = TEXT_TYPE_NORMAL;
        textColor = 0xffffff;
        hint = "";
        hintColor = 0xffffff;
        hasTextColor = false;
        hasHintColor = false;
        singleLine = 0;
        line = 0;
        inputType = INPUT_TYPE_TEXT;
        imeOption = IME_OPTION_NORMAL;
        textFont = TEXT_FONT;
    }

    public TextBean(Parcel parcel) {
        text = parcel.readString();
        textSize = parcel.readInt();
        textColor = parcel.readInt();
        hasTextColor = parcel.readInt() != 0;
        textType = parcel.readInt();
        textFont = parcel.readString();
        hint = parcel.readString();
        hintColor = parcel.readInt();
        hasHintColor = parcel.readInt() != 0;
        singleLine = parcel.readInt();
        line = parcel.readInt();
        inputType = parcel.readInt();
        imeOption = parcel.readInt();
        resTextColor = parcel.readString();
        resHintColor = parcel.readString();
    }

    public static Parcelable.Creator<TextBean> getCreator() {
        return CREATOR;
    }

    public void copy(TextBean textBean) {
        text = textBean.text;
        textSize = textBean.textSize;
        textColor = textBean.textColor;
        hasTextColor = textBean.hasTextColor;
        textType = textBean.textType;
        textFont = textBean.textFont;
        hint = textBean.hint;
        hintColor = textBean.hintColor;
        hasHintColor = textBean.hasHintColor;
        singleLine = textBean.singleLine;
        line = textBean.line;
        inputType = textBean.inputType;
        imeOption = textBean.imeOption;
        resHintColor = textBean.resHintColor;
        resTextColor = textBean.resTextColor;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public boolean isEqual(TextBean textBean) {
        return Objects.equals(text, textBean.text)
                && textSize == textBean.textSize
                && textColor == textBean.textColor
                && hasTextColor == textBean.hasTextColor
                && textType == textBean.textType
                && Objects.equals(resTextColor, textBean.resTextColor)
                && Objects.equals(textFont, textBean.textFont)
                && Objects.equals(hint, textBean.hint)
                && hintColor == textBean.hintColor
                && hasHintColor == textBean.hasHintColor
                && singleLine == textBean.singleLine
                && line == textBean.line
                && inputType == textBean.inputType
                && imeOption == textBean.imeOption
                && Objects.equals(resHintColor, textBean.resHintColor);
    }

    public void print() {
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(text);
        parcel.writeInt(textSize);
        parcel.writeInt(textColor);
        parcel.writeInt(hasTextColor ? 1 : 0);
        parcel.writeInt(textType);
        parcel.writeString(textFont);
        parcel.writeString(hint);
        parcel.writeInt(hintColor);
        parcel.writeInt(hasHintColor ? 1 : 0);
        parcel.writeInt(singleLine);
        parcel.writeInt(line);
        parcel.writeInt(inputType);
        parcel.writeInt(imeOption);
        parcel.writeString(resTextColor);
        parcel.writeString(resHintColor);
    }
}
