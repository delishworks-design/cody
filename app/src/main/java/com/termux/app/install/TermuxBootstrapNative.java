package com.termux.app.install;

/** JNI bridge that returns the embedded Termux rootfs bootstrap zip for the current ABI. */
public final class TermuxBootstrapNative {
    static {
        System.loadLibrary("termux-bootstrap");
    }

    private TermuxBootstrapNative() {}

    public static native byte[] getZip();
}
