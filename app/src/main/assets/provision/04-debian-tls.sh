#!/data/data/com.termux/files/usr/bin/bash
# Step 4: give the Debian container the same trust anchors, so curl/apt/python *inside* it can
# verify HTTPS too (opencode.ai must be reachable from there to install opencode).
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
ANCHORS="$PREFIX/etc/tls/opencode-anchors.pem"
EXTRA="$PREFIX/etc/tls/extra-anchors.pem"

ROOTFS=""
for d in "$PREFIX/var/lib/proot-distro/containers/debian/rootfs" \
         "$PREFIX/var/lib/proot-distro/installed-rootfs/debian"; do
    if [ -d "$d" ]; then ROOTFS="$d"; break; fi
done
if [ -z "$ROOTFS" ]; then
    echo "Debian rootfs not found - run the previous step first."
    exit 1
fi

mkdir -p "$ROOTFS/usr/local/share/ca-certificates"
cat "$ANCHORS" > "$ROOTFS/usr/local/share/ca-certificates/opencode-anchors.crt"
# Manually added anchors (e.g. a corporate root CA) travel as their own file so a re-run of
# TlsTrustSync, which rewrites the Android-derived list, cannot drop them. The stale copy is removed
# rather than left alone: otherwise a CA the user has since deleted or replaced stays trusted inside
# the container, and this step would report anchors that are no longer on the device.
EXTRA_CRT="$ROOTFS/usr/local/share/ca-certificates/opencode-extra-anchors.crt"
if [ -s "$EXTRA" ]; then
    cp "$EXTRA" "$EXTRA_CRT"
    extra_count=$(grep -c 'BEGIN CERTIFICATE' "$EXTRA_CRT")
else
    rm -f "$EXTRA_CRT"
    extra_count=0
fi
echo "Copied $(grep -c 'BEGIN CERTIFICATE' "$ROOTFS/usr/local/share/ca-certificates/opencode-anchors.crt") anchors + $extra_count manual into Debian"

# Verification is only restored if it actually works. Some networks intercept TLS with a root that
# lives solely in the host machine's certificate store, so the anchor is genuinely missing here and
# no amount of copying can conjure it. Probing first means provisioning still completes on such a
# network (apt runs unverified) while a healthy network gets proper verification from the start,
# rather than being silently downgraded for everyone's sake.
#
# The probe runs in the container, but the guidance is printed here: proot-distro does not pass the
# outer environment through, so a path known only to this script cannot be interpolated in there.
out=$(proot-distro login debian --shared-tmp -- /bin/bash -lc '
    export DEBIAN_FRONTEND=noninteractive
    export SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt
    mkdir -p /etc/apt/apt.conf.d
    command -v update-ca-certificates >/dev/null 2>&1 \
        || apt-get -o Acquire::https::Verify-Peer=false install -y ca-certificates >/dev/null 2>&1
    update-ca-certificates >/dev/null 2>&1

    if curl -sS -m 25 -o /dev/null https://opencode.ai/ 2>/dev/null; then
        cat > /etc/apt/apt.conf.d/99-opencode-tls <<EOF
Acquire::https::Verify-Peer "true";
Acquire::https::Verify-Host "true";
Acquire::https::CAInfo "/etc/ssl/certs/ca-certificates.crt";
Acquire::https::CAPath "/dev/null";
EOF
        echo "Debian HTTPS verification: OK"
    else
        cat > /etc/apt/apt.conf.d/99-opencode-tls <<EOF
Acquire::https::Verify-Peer "false";
Acquire::https::Verify-Host "true";
Acquire::https::CAInfo "/etc/ssl/certs/ca-certificates.crt";
Acquire::https::CAPath "/dev/null";
EOF
        echo "TLS_ANCHOR_MISSING"
    fi
    exit 0
')

echo "$out"
if echo "$out" | grep -q TLS_ANCHOR_MISSING; then
    echo "Debian HTTPS verification: FAILED - this network intercepts TLS with a root CA that is not on the device."
    echo "Setup continues without verification. To fix it, export that CA from your machine to"
    echo "$EXTRA and re-run 00-tls-sync.sh."
fi
exit 0
