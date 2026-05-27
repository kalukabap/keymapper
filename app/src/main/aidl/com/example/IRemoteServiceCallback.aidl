package com.example;

interface IRemoteServiceCallback {
    void onServiceConnected() = 1;
    void onServiceDisconnected() = 2;
    void alertMouseAimActivated() = 3;
    void setCursorX(int x) = 4;
    void setCursorY(int y) = 5;
    void enablePointer() = 6;
    void disablePointer() = 7;
}
