package com.termux.app.install;

/**
 * Hard-coded Termux filesystem layout. These paths match the Termux bootstrap binaries and the
 * applicationId used by this app ({@code com.termux}). Changing them requires rebuilding the
 * Termux bootstrap packages.
 */
public final class TermuxPaths {
    private TermuxPaths() {}

    public static final String PACKAGE_NAME = "com.termux";
    public static final String FILES_DIR = "/data/data/com.termux/files";
    public static final String PREFIX = FILES_DIR + "/usr";
    public static final String BIN = PREFIX + "/bin";
    public static final String LIB = PREFIX + "/lib";
    public static final String ETC = PREFIX + "/etc";
    public static final String TMP = PREFIX + "/tmp";
    public static final String HOME = FILES_DIR + "/home";
    public static final String STAGING_PREFIX = FILES_DIR + "/usr-staging";
    public static final String LOGIN = BIN + "/login";
    public static final String BASH = BIN + "/bash";
    public static final String PROOT_DISTRO = BIN + "/proot-distro";
    public static final String OPENCODE_WRAPPER = BIN + "/opencode";

    /**
     * The single CA bundle used by every TLS client in the Termux prefix. curl is compiled against
     * it, python resolves it via {@code ssl.get_default_verify_paths().openssl_cafile}, and the
     * GnuTLS build used by apt embeds the same path — so updating this one file is enough for the
     * whole Termux environment (no environment variables required).
     */
    public static final String TLS_DIR = ETC + "/tls";
    public static final String CA_BUNDLE = TLS_DIR + "/cert.pem";

    /** Mirrors of the Android trust store (system + user CAs) written during setup. */
    public static final String CA_ANCHORS = TLS_DIR + "/opencode-anchors.pem";

    /** Clean copy of {@link #CA_BUNDLE} captured before the anchors are merged in. */
    public static final String CA_BUNDLE_PRISTINE = CA_BUNDLE + ".pristine";

    /**
     * Where proot-distro keeps its installed distribution root filesystems. Recent releases use
     * {@code containers/<name>/rootfs}; older ones used {@code installed-rootfs/<name>}. Both are
     * kept so setup works with either layout.
     */
    public static final String PROOT_DISTRO_CONTAINERS = PREFIX + "/var/lib/proot-distro/containers";
    public static final String PROOT_DISTRO_ROOTFS = PREFIX + "/var/lib/proot-distro/installed-rootfs";

    public static final String DEBIAN_CONTAINER_ROOTFS = PROOT_DISTRO_CONTAINERS + "/debian/rootfs";
    public static final String DEBIAN_LEGACY_ROOTFS = PROOT_DISTRO_ROOTFS + "/debian";

    public static final String SECOND_STAGE_NEW =
            PREFIX + "/etc/termux/termux-bootstrap/second-stage/termux-bootstrap-second-stage.sh";
    public static final String SECOND_STAGE_OLD =
            PREFIX + "/etc/termux/bootstrap/termux-bootstrap-second-stage.sh";
}
