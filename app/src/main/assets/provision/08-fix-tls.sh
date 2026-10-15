#!/data/data/com.termux/files/usr/bin/bash
# Step 08: restore TLS verification once provisioning is done - but only where it actually works.
#
# Steps 01-07 could run apt without peer verification so setup would complete behind corporate TLS
# inspection. Leaving it off forever would quietly weaken every later `apt-get` the agent runs, and
# turning it on when the root CA is genuinely absent would break them instead. So each side is
# probed, and whichever cannot verify keeps the unverified config plus one clear explanation.
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
TLS="$PREFIX/etc/tls"
BUNDLE="$TLS/cert.pem"

write_apt_config() {
    # $1 = destination, $2 = CA bundle to trust
    mkdir -p "$(dirname "$1")"
    cat > "$1" <<EOF
Acquire::https::Verify-Peer "$3";
Acquire::https::Verify-Host "true";
Acquire::https::CAInfo "$2";
Acquire::https::CAPath "/dev/null";
EOF
}

# -- Termux side ---------------------------------------------------------------------------
if SSL_CERT_FILE="$BUNDLE" curl -sS -m 25 -o /dev/null https://deb.debian.org/ 2>/dev/null; then
    write_apt_config "$PREFIX/etc/apt/apt.conf.d/99-opencode-tls" "$BUNDLE" true
    echo "TLS verification re-enabled for apt"
else
    write_apt_config "$PREFIX/etc/apt/apt.conf.d/99-opencode-tls" "$BUNDLE" false
    echo "TLS verification left OFF for apt: this network's root CA is not on the device."
fi

# -- Debian side ---------------------------------------------------------------------------
ROOTFS=""
for d in "$PREFIX/var/lib/proot-distro/containers/debian/rootfs" \
         "$PREFIX/var/lib/proot-distro/installed-rootfs/debian"; do
    if [ -d "$d" ]; then ROOTFS="$d"; break; fi
done

if [ -n "$ROOTFS" ]; then
    # The probe runs inside the container and must use the container's own merged bundle; the
    # Termux path is not guaranteed to resolve under proot.
    if proot-distro login debian --shared-tmp -- /bin/bash -lc \
        'export SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt
         curl -sS -m 25 -o /dev/null https://opencode.ai/' >/dev/null 2>&1; then
        write_apt_config "$ROOTFS/etc/apt/apt.conf.d/99-opencode-tls" "/etc/ssl/certs/ca-certificates.crt" true
        echo "TLS verification re-enabled for Debian apt"
    else
        write_apt_config "$ROOTFS/etc/apt/apt.conf.d/99-opencode-tls" "/etc/ssl/certs/ca-certificates.crt" false
        echo "TLS verification left OFF for Debian apt: this network's root CA is not on the device."
        echo "Export the CA from your machine to $TLS/extra-anchors.pem and re-run 00-tls-sync.sh."
    fi
fi

exit 0
