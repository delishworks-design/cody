#!/data/data/com.termux/files/usr/bin/bash
# Installs the developer CLIs the agent is expected to reach for, inside the Debian container.
#
# Tolerant by design: this is an optional step (see OPTIONAL_STEPS in EnvironmentProvisioner), so a
# blocked registry or a missing package must not abort provisioning. Every tool is attempted, the
# outcome is printed as a summary, and the script exits 0 as long as it ran.
#
# The real work happens in a helper script executed *inside* the container, because the agent runs
# there and the tools have to be visible to it. Installing into Termux instead would leave them
# unusable: Termux's own Node reports process.platform=android, which is exactly what breaks
# platform-checked CLIs such as Supabase's.
PREFIX=/data/data/com.termux/files/usr
HOME_DIR=/data/data/com.termux/files/home
PATH=$PREFIX/bin
TMPDIR=$PREFIX/tmp
export PREFIX HOME="$HOME_DIR" PATH TMPDIR LANG=en_US.UTF-8
cd "$HOME_DIR" || exit 1

# --shared-tmp makes the container's /tmp this same directory, so the helper can be written here
# and executed there without any copying.
INNER="$TMPDIR/install-tooling-inner.sh"
cat > "$INNER" <<'INNEREOF'
say() { printf '%s\n' "$*"; }
have() { command -v "$1" >/dev/null 2>&1; }

export SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt
APT_OPTS="-o Acquire::https::Verify-Peer=false"

say "=== base packages ==="
apt-get $APT_OPTS update -y >/dev/null 2>&1
apt-get $APT_OPTS install -y --no-install-recommends git curl ca-certificates unzip jq nodejs npm >/dev/null 2>&1 \
  && say "apt: ok" || say "apt: FAILED (continuing)"

say "=== gh (GitHub CLI) ==="
if have gh; then
  say "gh already installed: $(gh --version 2>/dev/null | head -1)"
elif apt-get $APT_OPTS install -y --no-install-recommends gh >/dev/null 2>&1 && have gh; then
  say "gh installed via apt: $(gh --version 2>/dev/null | head -1)"
else
  arch=$(uname -m)
  case "$arch" in
    x86_64)  gharch=amd64 ;;
    aarch64) gharch=arm64 ;;
    *)       gharch="" ;;
  esac
  if [ -n "$gharch" ]; then
    # Ask GitHub for the current tag instead of pinning a version that may have been replaced.
    latest=$(curl -sS -m 20 -o /dev/null -w '%{url_effective}' -L \
               https://github.com/cli/cli/releases/latest 2>/dev/null | sed 's|.*/tag/||')
    if [ -n "$latest" ]; then
      url="https://github.com/cli/cli/releases/download/${latest}/gh_${latest#v}_linux_${gharch}.deb"
    else
      url="https://github.com/cli/cli/releases/latest/download/gh_2.62.0_linux_${gharch}.deb"
    fi
    if curl -fsSL -m 180 -o /tmp/gh.deb "$url" 2>/dev/null \
       && dpkg -i /tmp/gh.deb >/dev/null 2>&1 && have gh; then
      say "gh installed from .deb: $(gh --version 2>/dev/null | head -1)"
    else
      say "gh FAILED (apt and .deb both unavailable)"
    fi
  else
    say "gh FAILED (unknown architecture: $arch)"
  fi
fi

say "=== vercel (Vercel CLI) ==="
# Not via npm: npm's cacache writes blobs by renaming a temp file into a content-addressed path,
# and that rename reports ENOENT under proot on every cache location (/root, /tmp, /dev/shm). The
# install therefore gets part-way through and dies. bun is a single static binary with its own
# resolver, so it sidesteps npm's cache entirely.
if have vercel; then
  say "vercel already installed: $(vercel --version 2>/dev/null | head -1)"
else
  case "$(uname -m)" in
    x86_64)  bunarch=linux-x64 ;;
    aarch64) bunarch=linux-aarch64 ;;
    *)       bunarch="" ;;
  esac
  if [ -z "$bunarch" ]; then
    say "vercel FAILED (unknown architecture: $(uname -m))"
  elif ! have bun && { curl -fsSL -m 600 -o /tmp/bun.zip \
      "https://github.com/oven-sh/bun/releases/latest/download/bun-$bunarch.zip" >/dev/null 2>&1 \
      && unzip -o -q /tmp/bun.zip -d /tmp/bunz >/dev/null 2>&1; }; then
    say "vercel FAILED (could not download bun)"
  else
    # The archive unpacks to one directory named after the asset, e.g. bun-linux-x64/bun.
    bunbin=$(find /tmp/bunz -type f -name bun 2>/dev/null | head -1)
    [ -n "$bunbin" ] && install -m 0755 "$bunbin" /usr/local/bin/bun >/dev/null 2>&1
    have bun || say "vercel FAILED (bun did not unpack)"
  fi
  if have bun; then
    bun add -g vercel >/dev/null 2>&1
    # bun keeps globals in ~/.bun/bin, which is not on the agent's PATH - link them explicitly.
    [ -x /root/.bun/bin/vercel ] && ln -sf /root/.bun/bin/vercel /usr/local/bin/vercel
    if have vercel; then
      say "vercel installed via bun: $(vercel --version 2>/dev/null | tail -1)"
    else
      say "vercel FAILED (bun install did not produce a binary)"
    fi
  fi
fi

say "=== supabase (Supabase CLI) ==="
if have supabase; then
  say "supabase already installed: $(supabase --version 2>/dev/null | head -1)"
else
  arch=$(uname -m)
  case "$arch" in
    x86_64)  sarch=amd64 ;;
    aarch64) sarch=arm64 ;;
    *)       sarch="" ;;
  esac
  if [ -n "$sarch" ]; then
    url="https://github.com/supabase/cli/releases/latest/download/supabase_linux_${sarch}.tar.gz"
    if curl -fsSL -m 240 -o /tmp/supabase.tar.gz "$url" 2>/dev/null \
       && tar -xzf /tmp/supabase.tar.gz -C /tmp 2>/dev/null \
       && install -m 0755 /tmp/supabase /usr/local/bin/supabase 2>/dev/null \
       && have supabase; then
      say "supabase installed from tarball: $(supabase --version 2>/dev/null | head -1)"
    elif npm install -g supabase >/dev/null 2>&1 && have supabase; then
      say "supabase installed via npm: $(supabase --version 2>/dev/null | head -1)"
    else
      say "supabase FAILED (tarball and npm both unavailable)"
    fi
  else
    say "supabase FAILED (unknown architecture: $arch)"
  fi
fi

say "=== summary ==="
for tool in gh vercel supabase; do
  if have "$tool"; then
    printf 'installed  %s  %s\n' "$tool" "$($tool --version 2>/dev/null | head -1)"
  else
    printf 'FAILED    %s\n' "$tool"
  fi
done
exit 0
INNEREOF

proot-distro login debian --shared-tmp -- /bin/bash /tmp/install-tooling-inner.sh
exit 0
