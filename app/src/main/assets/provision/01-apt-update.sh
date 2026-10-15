#!/data/data/com.termux/files/usr/bin/bash
# Step 1: refresh the Termux package lists.
#
# The custom CA bundle (merged in step 00) must be explicitly used by apt. We export SSL_CERT_FILE
# for curl and create an apt config snippet so GnuTLS-backed apt picks up the same bundle.
# Additionally, we run apt with explicit -o options as a belt-and-suspenders approach.
#
# TEMPORARY: On networks with TLS inspection (Cisco Umbrella, etc.) where the corporate CA is not
# yet in the trust store, we disable peer verification so apt can at least download packages.
# This is re-enabled in step 08 after provisioning completes.
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
TLS="$PREFIX/etc/tls"
BUNDLE="$TLS/cert.pem"
export SSL_CERT_FILE="$BUNDLE"

# Point apt at the same bundle via GnuTLS (apt uses gnutls under the hood on Termux).
mkdir -p "$PREFIX/etc/apt/apt.conf.d"
cat > "$PREFIX/etc/apt/apt.conf.d/99-opencode-tls" <<EOF
Acquire::https::Verify-Peer "true";
Acquire::https::Verify-Host "true";
Acquire::https::CAInfo "$BUNDLE";
Acquire::https::CAPath "/dev/null";
EOF

# TEMPORARY: disable peer verification so apt works behind corporate TLS inspection
# This will be re-enabled by 08-fix-tls.sh after provisioning
apt-get -o Acquire::https::Verify-Peer="false" update -y
