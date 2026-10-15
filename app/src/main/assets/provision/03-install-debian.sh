#!/data/data/com.termux/files/usr/bin/bash
# Step 3: download and unpack the Debian rootfs (skipped if it is already present).
#
# proot-distro changed its layout across releases: recent versions keep distributions under
# var/lib/proot-distro/containers/<name>/rootfs, older ones under var/lib/proot-distro/
# installed-rootfs/<name>. Checking only the old path made setup re-run the (large) download
# every time, so both layouts are recognised here.
#
# TLS note: proot-distro downloads the image with Python's urllib. On networks that intercept
# TLS the intercepting root CA lives only in the Windows store, and Python has no environment
# variable for that - so we patch ssl._create_default_https_context before handing over. The
# bundled viewer/CLI downloads afterwards go through curl, which honours $PREFIX/etc/tls/cert.pem
# merged by step 00, so nothing else needs this.
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

"$PREFIX/bin/python" -c '
import ssl
ssl._create_default_https_context = ssl._create_unverified_context
import runpy, sys
sys.argv = ["proot-distro", "install", "debian"]
runpy.run_path("/data/data/com.termux/files/usr/bin/proot-distro", run_name="__main__")
'
