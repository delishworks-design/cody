package com.termux.app.shell;

import com.termux.app.install.TermuxPaths;

/** Builds the environment used when launching Termux processes (mirrors Termux's own env). */
public final class TermuxShellEnvironment {
    private TermuxShellEnvironment() {}

    public static String[] build(String workingDirectory) {
        if (workingDirectory == null || workingDirectory.isEmpty())
            workingDirectory = TermuxPaths.HOME;

        String androidRoot = valueOr("ANDROID_ROOT", "/system");
        String androidData = valueOr("ANDROID_DATA", "/data");
        String externalStorage = valueOr("EXTERNAL_STORAGE", "");

        return new String[]{
                "TERM=xterm-256color",
                "COLORTERM=truecolor",
                "LANG=en_US.UTF-8",
                "HOME=" + TermuxPaths.HOME,
                "PREFIX=" + TermuxPaths.PREFIX,
                "TERMUX__HOME=" + TermuxPaths.HOME,
                "TERMUX__PREFIX=" + TermuxPaths.PREFIX,
                "TERMUX__ROOTFS_DIR=" + TermuxPaths.FILES_DIR,
                "TMPDIR=" + TermuxPaths.TMP,
                "PATH=" + TermuxPaths.BIN,
                "PWD=" + workingDirectory,
                "LD_LIBRARY_PATH=" + TermuxPaths.LIB,
                "ANDROID_ROOT=" + androidRoot,
                "ANDROID_DATA=" + androidData,
                "EXTERNAL_STORAGE=" + externalStorage,
                "ANDROID__BUILD_VERSION_SDK=" + android.os.Build.VERSION.SDK_INT,
        };
    }

    private static String valueOr(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isEmpty()) ? fallback : value;
    }
}
