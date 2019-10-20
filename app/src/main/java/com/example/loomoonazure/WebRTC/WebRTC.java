package com.example.loomoonazure.WebRTC;

import java.io.Closeable;
import android.view.Surface;
import android.content.Context;

import com.example.loomoonazure.GStreamer.GStreamer;

public class WebRTC implements Closeable {
    private static native void nativeClassInit();
    public static void init(Context context) throws Exception {
        System.loadLibrary("gstreamer_android");
        GStreamer.init(context);

        System.loadLibrary("gstwebrtc");
        nativeClassInit();
    }

    private long native_webrtc;
    private native void nativeNew();
    public WebRTC() {
        nativeNew();
    }

    private native void nativeFree();
    @Override
    public void close() {
        nativeFree();
    }

    private Surface surface;
    private native void nativeSetSurface(Surface surface);
    public void setSurface(Surface surface) {
        this.surface = surface;
        nativeSetSurface(surface);
    }

    public Surface getSurface() {
        return surface;
    }

    private String signallingServer;
    private native void nativeSetSignallingServer(String server);
    public void setSignallingServer(String server) {
        this.signallingServer = server;
        nativeSetSignallingServer(server);
    }

    public String getSignallingServer() {
        return this.signallingServer;
    }

    private String callID;
    private native void nativeSetCallID(String ID);
    public void setCallID(String ID) {
        this.callID = ID;
        nativeSetCallID(ID);
    }

    public String getCallID() {
        return this.callID;
    }

    private native void nativeCallOtherParty();
    public void callOtherParty() {
        nativeCallOtherParty();
    }

    private native void nativeEndCall();
    public void endCall() {
        nativeEndCall();
    }
}