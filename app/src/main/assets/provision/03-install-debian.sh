#!/data/data/com.termux/files/usr/bin/bash
# Step 3: download and unpack the Debian rootfs (skipped if it is already present).
#
# proot-distro changed its layout across releases: recent versions keep distributions under
# var/lib/proot-distro/containers/<name>/rootfs, older ones under var/lib/proot-distro/
# installed-rootfs/<name>. Checking only the old path made setup re-run the (large) download
# every time, so both layouts are recognised here.
#
# TLS note: proot-distro downloads the image with Python's urllib, and Python has no environment
# variable for "skip verification". Rather than disable verification unconditionally - which would
# silently weaken this download on every network - we probe with the same client first and only
# fall back to ssl._create_unverified_context when verification genuinely fails (a TLS-inspection
# network whose root CA is not on the device, the same case steps 00 and 08 report). Everything
# afterwards goes through curl, which honours $PREFIX/etc/tls/cert.pem merged by step 00.
PREFIX=/data/data/com.termux/files/usr
HOME_DIR=/data/data/com.termux/files/home
export PATH=$PREFIX/bin
export HOME=$HOME_DIR
export TMPDIR=$PREFIX/tmp
export SSL_CERT_FILE=$PREFIX/etc/tls/cert.pem
export LANG=en_US.UTF-8
cd "$HOME_DIR" || exit 1

for d in "$PREFIX/var/lib/proot-distro/containers/debian/rootfs" \
         "$PREFIX/var/lib/proot-distro/installed-rootfs/debian"; do
    if [ -d "$d" ]; then
        echo "Debian already present at $d"
        exit 0
    fi
done

# Probe with the same client the download uses, so "verification works" means it works for the
# rootfs download and not merely for curl. Uses deb.debian.org because that is the host the Debian
# image is fetched from; on a network that intercepts TLS with an untrusted CA every HTTPS request
# fails here, not just this one.
if "$PREFIX/bin/python" - <<'PY' >/dev/null 2>&1
import sys, urllib.request
try:
    urllib.request.urlopen("https://deb.debian.org/", timeout=25)
except Exception:
    sys.exit(1)
PY
then
    echo "Debian rootfs: TLS verification OK"
    UNSAFE=""
else
    echo "Debian rootfs: TLS verification FAILED - this network's root CA is not on the device."
    echo "  Downloading without peer verification so setup can finish."
    echo "  Export the CA to $PREFIX/etc/tls/extra-anchors.pem and re-run 00-tls-sync.sh."
    UNSAFE=1
fi

UNSAFE="$UNSAFE" "$PREFIX/bin/python" - <<'PY'
import os, ssl, runpy, sys
if os.environ.get("UNSAFE"):
    ssl._create_default_https_context = ssl._create_unverified_context
sys.argv = ["proot-distro", "install", "debian"]
runpy.run_path("/data/data/com.termux/files/usr/bin/proot-distro", run_name="__main__")
PY
