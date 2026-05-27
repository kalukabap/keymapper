package com.example;

interface OnKeyEventListener {
    void onKeyEvent(String code, boolean pressed) = 1;
    void onMouseEvent(int relX, int relY, int buttons, int wheel) = 2;
}
