#!/data/data/com.termux/files/usr/bin/bash
# Step 11: install the bundled agent skills.
#
# Skills are markdown instructions opencode advertises to the agent and loads on demand.
# They ship inside the APK rather than in the repo because the repo is invisible to
# everyone who installs the app - only the people building it ever see .opencode/.
#
# They are written to the container's ~/.config/opencode/skills, which is the *global*
# scope. That means one copy serves every project the user works on, rather than each
# project having to carry its own.
#
# opencode reads skills at startup, so this has to run before it starts. It is a
# provisioning step for exactly that reason.
#
# Written fresh on every run, never merged: a skill edited or deleted inside the
# container should not survive a run and contradict the version in the app.

PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
# Unpacked from the APK by ProvisionAssets alongside the other scripts.
ASSETS="$PREFIX/home/.opencode-provision/skills"
TARGET_REL="root/.config/opencode/skills"

if [ ! -d "$ASSETS" ]; then
    echo "SKILLS_NO_ASSETS"
    exit 0
fi

count="$(find "$ASSETS" -mindepth 2 -maxdepth 2 -name SKILL.md 2>/dev/null | wc -l | tr -d ' ')"
if [ "$count" = "0" ]; then
    echo "SKILLS_NONE"
    exit 0
fi

proot-distro login debian --shared-tmp -- /bin/bash -lc '
set -u
ASSETS="'"$ASSETS"'"
TARGET="/'"$TARGET_REL"'"

mkdir -p "$TARGET" || exit 1

# Replace rather than merge. Anything the user added by hand lives in a different
# directory and is untouched; anything here that they edited is overwritten, which is
# the point - the app is the source of truth for its own bundled skills.
rm -rf "$TARGET"/cody-*
cp -r "$ASSETS"/* "$TARGET"/ 2>/dev/null

# A SKILL.md whose name does not match its directory is silently skipped by opencode,
# so catch it here where the log is still in front of someone who can act on it.
for dir in "$TARGET"/*/; do
    [ -f "$dir/SKILL.md" ] || continue
    name="$(basename "$dir")"
    declared="$(sed -n "s/^name:[[:space:]]*//p" "$dir/SKILL.md" | head -1 | tr -d "\"\r")"
    if [ "$name" != "$declared" ]; then
        echo "SKILLS_NAME_MISMATCH dir=$name frontmatter=$declared"
    fi
done

count="$(find "$TARGET" -mindepth 2 -maxdepth 2 -name SKILL.md | wc -l | tr -d " ")"
echo "SKILLS_INSTALLED $count"
exit 0
'