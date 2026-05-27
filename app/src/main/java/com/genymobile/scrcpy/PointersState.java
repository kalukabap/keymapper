package com.genymobile.scrcpy;

import android.view.MotionEvent;

public class PointersState {
    public static final int MAX_POINTERS = 10;
    private final Pointer[] pointers = new Pointer[MAX_POINTERS];
    private int pointerCount = 0;

    public PointersState() {
        for (int i = 0; i < MAX_POINTERS; i++) {
            pointers[i] = new Pointer();
        }
    }

    public Pointer get(int index) {
        return pointers[index];
    }

    public int getPointerIndex(int pointerId) {
        // Find existing pointer with this id
        for (int i = 0; i < pointerCount; i++) {
            if (pointers[i].getId() == pointerId && !pointers[i].isUp()) {
                return i;
            }
        }
        // Find empty slot
        for (int i = 0; i < MAX_POINTERS; i++) {
            if (pointers[i].isUp()) {
                pointers[i].setId(pointerId);
                pointers[i].setUp(false);
                if (i >= pointerCount) pointerCount = i + 1;
                return i;
            }
        }
        return -1;
    }

    public int update(MotionEvent.PointerProperties[] props, MotionEvent.PointerCoords[] coords) {
        int count = 0;
        for (int i = 0; i < pointerCount; i++) {
            if (!pointers[i].isUp()) {
                props[count].id = pointers[i].getId();
                coords[count].x = pointers[i].getPoint().x;
                coords[count].y = pointers[i].getPoint().y;
                coords[count].pressure = pointers[i].getPressure();
                count++;
            }
        }
        // Clean up: shrink pointerCount if trailing slots are empty
        while (pointerCount > 0 && pointers[pointerCount - 1].isUp()) {
            pointerCount--;
        }
        return count;
    }

    public void release() {
        for (int i = 0; i < MAX_POINTERS; i++) {
            pointers[i].setUp(true);
        }
        pointerCount = 0;
    }
}
