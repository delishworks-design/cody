#!/data/data/com.termux/files/usr/bin/bash
# Starts opencode's web interface inside the Debian container.
#
# Same reasoning as serve.sh: proot does not isolate the network namespace, so binding 127.0.0.1
# here makes the UI reachable from the Android WebView without any forwarding.
export PREFIX=/data/data/com.termux/files/usr
export HOME=/data/data/com.termux/files/home
export PATH=$PREFIX/bin
export TMPDIR=$PREFIX/tmp
export LANG=en_US.UTF-8
export SSL_CERT_FILE=$PREFIX/etc/tls/cert.pem

PORT="${1:-4173}"
cd "$HOME" || exit 1

exec proot-distro login debian --shared-tmp -- /bin/bash -lc "
  export PATH=\"\$PATH:\$HOME/.opencode/bin:/usr/local/bin\"
  export HOME=/root
  cd \$HOME
  exec opencode web --port $PORT --hostname 127.0.0.1
"
