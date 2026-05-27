package com.example.keymap;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.List;

/**
 * Keymap data passed over AIDL to the server process.
 * Contains all touch mappings and config.
 */
public class KeymapData implements Parcelable {

    public String packageName = "";
    public int screenWidth;
    public int screenHeight;
    public List<TouchPoint> touchPoints = new ArrayList<>();
    public List<SwipeLine> swipeLines = new ArrayList<>();
    public MouseAimConfig mouseAimConfig;
    public float mouseSensitivity = 1.0f;
    public float scrollSpeed = 1.0f;

    public static class TouchPoint implements Parcelable {
        public int keyCode;       // KeyEvent.KEYCODE_*
        public float xPercent;    // 0.0 - 1.0
        public float yPercent;    // 0.0 - 1.0
        public int tapDuration;   // ms
        public String mode;       // "tap", "hold", "long_press"

        public TouchPoint() {}
        protected TouchPoint(Parcel in) {
            keyCode = in.readInt();
            xPercent = in.readFloat();
            yPercent = in.readFloat();
            tapDuration = in.readInt();
            mode = in.readString();
        }
        @Override public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(keyCode);
            dest.writeFloat(xPercent);
            dest.writeFloat(yPercent);
            dest.writeInt(tapDuration);
            dest.writeString(mode);
        }
        @Override public int describeContents() { return 0; }
        public static final Creator<TouchPoint> CREATOR = new Creator<TouchPoint>() {
            public TouchPoint createFromParcel(Parcel in) { return new TouchPoint(in); }
            public TouchPoint[] newArray(int size) { return new TouchPoint[size]; }
        };
    }

    public static class SwipeLine implements Parcelable {
        public int keyCode;
        public float startXPercent, startYPercent;
        public float endXPercent, endYPercent;
        public int duration;

        public SwipeLine() {}
        protected SwipeLine(Parcel in) {
            keyCode = in.readInt();
            startXPercent = in.readFloat();
            startYPercent = in.readFloat();
            endXPercent = in.readFloat();
            endYPercent = in.readFloat();
            duration = in.readInt();
        }
        @Override public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(keyCode);
            dest.writeFloat(startXPercent);
            dest.writeFloat(startYPercent);
            dest.writeFloat(endXPercent);
            dest.writeFloat(endYPercent);
            dest.writeInt(duration);
        }
        @Override public int describeContents() { return 0; }
        public static final Creator<SwipeLine> CREATOR = new Creator<SwipeLine>() {
            public SwipeLine createFromParcel(Parcel in) { return new SwipeLine(in); }
            public SwipeLine[] newArray(int size) { return new SwipeLine[size]; }
        };
    }

    public static class MouseAimConfig implements Parcelable {
        public float xCenter, yCenter;
        public float areaWidth, areaHeight;
        public float xSensitivity, ySensitivity;
        public boolean toggle;
        public int triggerKeyCode;

        public MouseAimConfig() {}
        protected MouseAimConfig(Parcel in) {
            xCenter = in.readFloat();
            yCenter = in.readFloat();
            areaWidth = in.readFloat();
            areaHeight = in.readFloat();
            xSensitivity = in.readFloat();
            ySensitivity = in.readFloat();
            toggle = in.readByte() != 0;
            triggerKeyCode = in.readInt();
        }
        @Override public void writeToParcel(Parcel dest, int flags) {
            dest.writeFloat(xCenter);
            dest.writeFloat(yCenter);
            dest.writeFloat(areaWidth);
            dest.writeFloat(areaHeight);
            dest.writeFloat(xSensitivity);
            dest.writeFloat(ySensitivity);
            dest.writeByte((byte)(toggle ? 1 : 0));
            dest.writeInt(triggerKeyCode);
        }
        @Override public int describeContents() { return 0; }
        public static final Creator<MouseAimConfig> CREATOR = new Creator<MouseAimConfig>() {
            public MouseAimConfig createFromParcel(Parcel in) { return new MouseAimConfig(in); }
            public MouseAimConfig[] newArray(int size) { return new MouseAimConfig[size]; }
        };
    }

    public KeymapData() {}

    protected KeymapData(Parcel in) {
        packageName = in.readString();
        screenWidth = in.readInt();
        screenHeight = in.readInt();
        touchPoints = in.createTypedArrayList(TouchPoint.CREATOR);
        swipeLines = in.createTypedArrayList(SwipeLine.CREATOR);
        mouseAimConfig = in.readParcelable(MouseAimConfig.class.getClassLoader());
        mouseSensitivity = in.readFloat();
        scrollSpeed = in.readFloat();
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(packageName);
        dest.writeInt(screenWidth);
        dest.writeInt(screenHeight);
        dest.writeTypedList(touchPoints);
        dest.writeTypedList(swipeLines);
        dest.writeParcelable(mouseAimConfig, flags);
        dest.writeFloat(mouseSensitivity);
        dest.writeFloat(scrollSpeed);
    }

    @Override public int describeContents() { return 0; }

    public static final Creator<KeymapData> CREATOR = new Creator<KeymapData>() {
        public KeymapData createFromParcel(Parcel in) { return new KeymapData(in); }
        public KeymapData[] newArray(int size) { return new KeymapData[size]; }
    };

    public void scale(int newWidth, int newHeight) {
        if (screenWidth > 0 && screenHeight > 0) {
            float sx = (float) newWidth / screenWidth;
            float sy = (float) newHeight / screenHeight;
            for (TouchPoint tp : touchPoints) {
                tp.xPercent *= sx;
                tp.yPercent *= sy;
            }
            for (SwipeLine sl : swipeLines) {
                sl.startXPercent *= sx;
                sl.startYPercent *= sy;
                sl.endXPercent *= sx;
                sl.endYPercent *= sy;
            }
        }
        screenWidth = newWidth;
        screenHeight = newHeight;
    }
}
