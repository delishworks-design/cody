#!/data/data/com.termux/files/usr/bin/bash
# Decide whether opencode is reachable inside Debian.
#
# Prints the resolved path when found, or the literal token OPENCODE_MISSING otherwise. The
# caller treats an empty result as "not installed" — the previous version only looked for the
# word "opencode" in the output, so a successful `test -x` (which prints nothing) was reported as
# a failure.
#
# Exits non-zero only if proot-distro itself is unusable; a missing opencode is a clean exit 0
# carrying OPENCODE_MISSING so setup can distinguish "not installed" from "container broken".
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"

proot-distro login debian --shared-tmp -- /bin/bash -lc '
    if command -v opencode >/dev/null 2>&1; then
        command -v opencode
        exit 0
    fi
    for p in "$HOME/.opencode/bin/opencode" /usr/local/bin/opencode /usr/bin/opencode; do
        if [ -x "$p" ]; then
            echo "$p"
            exit 0
        fi
    done
    echo OPENCODE_MISSING
    exit 0
'
