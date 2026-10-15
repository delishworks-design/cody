LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# libtermux-bootstrap embeds the Termux rootfs zip (one per ABI) as a byte blob and exposes it
# through JNI so that the app can extract it without shipping it as a regular asset.
LOCAL_MODULE := libtermux-bootstrap
LOCAL_SRC_FILES := termux-bootstrap-zip.S termux-bootstrap.c
LOCAL_CFLAGS := -O2
LOCAL_LDFLAGS := -Wl,--gc-sections

include $(BUILD_SHARED_LIBRARY)
