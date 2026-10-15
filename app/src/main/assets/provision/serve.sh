#!/data/data/com.termux/files/usr/bin/bash
# Starts the headless opencode server inside the Debian container.
#
# proot does NOT create a separate network namespace, so a server bound to 127.0.0.1 inside the
# container is reachable from the Android app at exactly the same 127.0.0.1:<port> - no port
# forwarding or bridge is needed. That is what lets the chat UI talk to it with plain HTTP.
export PREFIX=/data/data/com.termux/files/usr
export HOME=/data/data/com.termux/files/home
export PATH=$PREFIX/bin
export TMPDIR=$PREFIX/tmp
export LANG=en_US.UTF-8
export SSL_CERT_FILE=$PREFIX/etc/tls/cert.pem

PORT="${1:-4096}"
cd "$HOME" || exit 1

exec proot-distro login debian --shared-tmp -- /bin/bash -lc "
  export PATH=\"\$PATH:\$HOME/.opencode/bin:/usr/local/bin\"
  export HOME=/root
  cd \$HOME
  exec opencode serve --port $PORT --hostname 127.0.0.1
"
