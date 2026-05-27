package com.example;

import com.example.IRemoteServiceCallback;
import com.example.OnKeyEventListener;
import com.example.keymap.KeymapData;

interface IRemoteService {
    void destroy() = 16777114;
    void startServer(in KeymapData keymap, IRemoteServiceCallback cb, int screenWidth, int screenHeight) = 2;
    void stopServer() = 3;
    void registerOnKeyEventListener(OnKeyEventListener l) = 4;
    void unregisterOnKeyEventListener(OnKeyEventListener l) = 5;
    void reloadKeymap(in KeymapData keymap) = 6;
    boolean isActive() = 7;
    void pauseMouse() = 8;
    void resumeMouse() = 9;
}
