---
name: cody-traps
description: "The non-obvious failures that are specific to running inside Cody's proot container — npm cache rename errors, TLS trust under interception, missing systemd, Python SSL, storage and turn-time limits, and where files actually live. Use when a command fails oddly, a build works locally but not here, an HTTPS call is rejected, or before installing anything with npm or pip."
license: Apache-2.0
metadata:
  opencode/slash: "true"
  opencode/autoinvoke: "true"
---

# Traps inside Cody

The environment is a Termux prefix with a Debian container under proot. Proot
translates syscalls without a kernel, which is where most of the surprises come
from. None of this is normal Linux behaviour.

## npm installs fail with `ENOENT` on `rename()`

npm's `cacache` writes blobs by renaming a temp file into a content-addressed path.
Under proot that `rename()` returns `ENOENT` on **every** filesystem — `/root`,
`/tmp`, `/dev/shm`, all identical. It is not a permissions problem and clearing the
cache does not help.

Use something with its own resolver:

```sh
bun add <pkg>        # a single static binary, sidesteps cacache entirely
pnpm add <pkg>       # content-addressable store, no rename dance
```

PyPI is fine. npm is the specific casualty.

## HTTPS fails behind an inspecting proxy

One bundle governs everything: `/data/data/com.termux/files/usr/etc/tls/cert.pem`.
curl is compiled against that path, python resolves it via `openssl_cafile`, and the
GnuTLS build apt uses embeds the same path. Nothing else needs configuring, and there
is nothing to export.

If a request is rejected, the anchor is missing rather than the verification being
too strict. Do not disable verification — that weakens every later `apt-get` the agent
runs. The fix is to add the anchor.

## Python has no "skip verify" switch

`urllib` reads no environment variable for it. To fetch something with verification
genuinely off, patch the default context:

```python
import ssl
ssl._create_default_https_context = ssl._create_unverified_context
```

Use this only for a one-off download you have a reason to distrust. Never leave it in
committed code.

## There is no systemd, and no kernel

- No services, no `systemctl`, no daemons. Long-running processes are started by the
  app and die with it.
- Ports below 1024 need privileges that do not exist. Use 1024 and up.
- No kernel modules, no `mount`, no namespaces, no cgroups beyond what proot fakes.
- `/proc` is procotied. Do not trust it as a description of the host.

## Resource limits

- **Storage is the binding constraint.** Two root filesystems plus the agent's working
  files. Check free space before cloning a repository or installing a large package,
  and say so if a task will not fit.
- **One turn has a ceiling.** A very long build is fine; a genuinely stuck one is
  surfaced after 15 minutes. Break long jobs into steps rather than launching one
  enormous background process.
- A request that appears to time out has **not** failed. The reply is still streaming.
  Do not resubmit.

## Where things are

| | |
| --- | --- |
| Debian container root | `/data/data/com.termux/files/usr/var/lib/proot-distro/containers/debian/rootfs` |
| Your working directory | `/root` — this is what opencode treats as the project |
| Termux home | `/data/data/com.termux/files/home` |
| Shared between Termux and Debian | `/tmp` (proot runs with `--shared-tmp`) |
| Service keys | `/root/.cody-credentials.env`, mode 600 — `source` it, never read a value out loud |
| Files the user sent | `/root/.opencode-attachments/` |

## Handy commands

```sh
proot-distro login debian --shared-tmp                 # Debian shell
proot-distro login debian --shared-tmp -- /bin/bash -lc '<command>'
df -h / /root /tmp                                     # check space before big work
source /root/.cody-credentials.env && gh auth status   # confirm a key is wired
```

## Delivering work

Put finished files next to each other. HTML previews are served from the folder the
file lives in, so a page referencing `./style.css` or `./photo.png` needs those beside
it — not in a parent directory. Use relative paths.

Do not assume Android can read a path in the container. When handing something over,
tell the user which file it is and let them take it from the app.