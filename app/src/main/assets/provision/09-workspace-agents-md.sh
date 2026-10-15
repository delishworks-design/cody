#!/data/data/com.termux/files/usr/bin/bash
# Step 9: teach the agent what this app can render.
#
# The app renders .html files and images that the agent writes, but opencode has no way to know that
# — it is a generic coding agent with no model of the UI around it. Left alone it pastes markup into
# a code block and tells the user to open a browser, which is exactly the behaviour the in-chat
# preview exists to remove.
#
# This drops an AGENTS.md into the container's /root, which opencode reads as project instructions.
# It is rewritten on every run so it tracks the app rather than drifting.
#
# The file is written to BOTH homes on purpose: the container's /root is what opencode actually
# reads as its project directory, and the Termux home is where the raw asset lives so it can be
# recovered or inspected without a container.
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
SOURCE="$HOME/.opencode-provision/workspace-agents-md.txt"

if [ ! -f "$SOURCE" ]; then
    echo "AGENTS_MD_SOURCE_MISSING"
    exit 0
fi

# Keep a copy outside the container so the text is inspectable from a plain shell.
cp -f "$SOURCE" "$HOME/AGENTS.md" 2>/dev/null || true

proot-distro login debian --shared-tmp -- /bin/bash -lc '
    cp -f /data/data/com.termux/files/home/.opencode-provision/workspace-agents-md.txt "$HOME/AGENTS.md" 2>/dev/null || {
        echo "AGENTS_MD_WRITE_FAILED"
        exit 0
    }
    wc -c < "$HOME/AGENTS.md"
'

echo "AGENTS_MD_INSTALLED"
exit 0
