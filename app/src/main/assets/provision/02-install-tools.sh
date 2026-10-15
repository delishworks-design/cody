#!/data/data/com.termux/files/usr/bin/bash
# Step 2: install proot-distro and a couple of tools opencode users expect.
#
# Each package is installed independently: apt aborts the entire command if any single name is
# unavailable, which would otherwise prevent proot-distro from being installed at all.
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
TLS="$PREFIX/etc/tls"
BUNDLE="$TLS/cert.pem"
export SSL_CERT_FILE="$BUNDLE"

# TEMPORARY: disable peer verification (re-enabled by 08-fix-tls.sh)
APT_OPTS="-o Acquire::https::Verify-Peer=false"

for pkg in proot-distro git ripgrep; do
    apt-get $APT_OPTS install -y "$pkg" || true
done
command -v proot-distro
