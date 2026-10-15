#!/data/data/com.termux/files/usr/bin/bash
# Installs Blender's command line interface inside the Debian container.
#
# The agent uses it headless (`blender --background --python ...`) to build, convert and export 3D
# models - most often to .glb, which the app previews in its WebView. No display or GPU is needed
# for that; Cycles CPU rendering also works headless. Only EEVEE requires OpenGL.
#
# Optional step: a failure here must not fail provisioning, so the script always exits 0.
#
# As with 06-install-tooling.sh, the work runs in a helper executed *inside* the container - the
# agent has to be able to find `blender` on its PATH.
PREFIX=/data/data/com.termux/files/usr
HOME_DIR=/data/data/com.termux/files/home
PATH=$PREFIX/bin
TMPDIR=$PREFIX/tmp
export PREFIX HOME="$HOME_DIR" PATH TMPDIR LANG=en_US.UTF-8
cd "$HOME_DIR" || exit 1

INNER="$TMPDIR/install-blender-inner.sh"
cat > "$INNER" <<'INNEREOF'
say() { printf '%s\n' "$*"; }
have() { command -v "$1" >/dev/null 2>&1; }

export SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt
APT_OPTS="-o Acquire::https::Verify-Peer=false"

# Blender's bundled Python has no numpy, and the glTF exporter imports it unconditionally - so
# without this, `bpy.ops.export_scene.gltf` dies with ModuleNotFoundError and the agent cannot
# produce the .glb files the app previews. Debian splits it out into its own package.
install_numpy() {
  say "=== glTF exporter dependency (numpy) ==="
  if python3 -c 'import numpy' >/dev/null 2>&1; then
    say "numpy: already available"
  elif apt-get $APT_OPTS install -y --no-install-recommends python3-numpy >/dev/null 2>&1; then
    say "numpy: installed via apt"
  else
    say "numpy: FAILED (.glb export will not work)"
  fi
}

if have blender; then
  say "blender already installed: $(blender --version 2>/dev/null | head -1)"
  install_numpy
  say "=== summary ==="
  printf 'installed  blender  %s\n' "$(blender --version 2>/dev/null | head -1)"
  exit 0
fi

say "=== apt ==="
apt-get $APT_OPTS update -y >/dev/null 2>&1
if apt-get $APT_OPTS install -y --no-install-recommends blender >/dev/null 2>&1 && have blender; then
  say "blender installed via apt: $(blender --version 2>/dev/null | head -1)"
  install_numpy
  say "=== summary ==="
  printf 'installed  blender  %s\n' "$(blender --version 2>/dev/null | head -1)"
  exit 0
fi
say "apt: unavailable, trying the portable tarball"

arch=$(uname -m)
case "$arch" in
  x86_64)  barch=x64 ;;
  aarch64) barch=arm64 ;;
  *)       barch="" ;;
esac

if [ -n "$barch" ]; then
  # download.blender.org keeps every release under /release/Blender<major>/<minor>/, which is far
  # more reliable than the GitHub API (unauthenticated calls are rate limited).
  for ver in 4.3.2 4.2.9 4.1.3 4.0.2; do
    url="https://download.blender.org/release/Blender${ver%.*}/blender-${ver}-linux-${barch}.tar.xz"
    say "trying $url"
    if curl -fsSL -m 900 -o /tmp/blender.tar.xz "$url" 2>/dev/null \
       && mkdir -p /opt/blender \
       && tar -xJf /tmp/blender.tar.xz -C /opt/blender --strip-components=1 2>/dev/null \
       && ln -sf /opt/blender/blender /usr/local/bin/blender 2>/dev/null \
       && have blender; then
      say "blender installed from tarball: $(blender --version 2>/dev/null | head -1)"
      install_numpy
      say "=== summary ==="
      printf 'installed  blender  %s\n' "$(blender --version 2>/dev/null | head -1)"
      exit 0
    fi
  done
  say "tarball FAILED"
fi

install_numpy
say "=== summary ==="
printf 'FAILED    blender\n'
exit 0
INNEREOF

proot-distro login debian --shared-tmp -- /bin/bash /tmp/install-blender-inner.sh
exit 0
