#!/data/data/com.termux/files/usr/bin/bash
# Step 5: install opencode inside Debian and make it reachable from any login shell.
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"

proot-distro login debian --shared-tmp -- /bin/bash -lc '
    export DEBIAN_FRONTEND=noninteractive
    export SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt
    APT_OPTS="-o Acquire::https::Verify-Peer=false"
    apt-get $APT_OPTS update -y >/dev/null 2>&1
    apt-get $APT_OPTS install -y curl ca-certificates git ripgrep unzip xz-utils >/dev/null 2>&1

    installed=0
    for url in https://opencode.ai/install https://opencode.ai/install.sh; do
        if curl -fsSL "$url" -o /tmp/oc-install.sh 2>/dev/null; then
            if bash /tmp/oc-install.sh >/tmp/oc-install.log 2>&1; then
                installed=1
                break
            fi
        fi
    done

    # The installer places the binary in ~/.opencode/bin but cannot persist PATH for us from here,
    # so export it for both login and interactive shells (profile.d + ~/.bashrc).
    PATH_LINE="export PATH=\"\$PATH:\$HOME/.opencode/bin:\$HOME/.local/bin:/usr/local/bin\""
    mkdir -p /etc/profile.d
    printf "%s\n" "$PATH_LINE" > /etc/profile.d/opencode.sh
    if ! grep -q ".opencode/bin" "$HOME/.bashrc" 2>/dev/null; then
        printf "%s\n" "$PATH_LINE" >> "$HOME/.bashrc"
    fi

    # Idempotent: a re-run must succeed if opencode is already present, even if the installer
    # failed this time (for example because the network dropped mid-download).
    if command -v opencode >/dev/null 2>&1 ||
       [ -x "$HOME/.opencode/bin/opencode" ] ||
       [ -x /usr/local/bin/opencode ] ||
       [ -x /usr/bin/opencode ]; then
        echo "OPENCODE_INSTALLED"
        "$HOME/.opencode/bin/opencode" --version 2>/dev/null ||
            opencode --version 2>/dev/null || true
        exit 0
    fi

    echo "OPENCODE_NOT_FOUND"
    tail -n 25 /tmp/oc-install.log 2>/dev/null
    exit 1
'
