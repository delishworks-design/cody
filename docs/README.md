# Preview images

The screenshots referenced by the **Preview** section in the top-level `README.md` live here. They
are real screenshots taken on the device running the release APK.

| File | Shows |
| --- | --- |
| `preview-chat.png` | The chat screen with `bash` tool cards, a collapsed *Thought for a moment*, the mode chip and the composer |
| `preview-setup.png` | First run — "Setting up your environment…" |
| `preview-welcome.png` | The empty chat, ready to type |

Keep them portrait, 1080x2436 as captured, and commit them as-is without re-encoding.

## Not published, on purpose

| File | Why it is held back |
| --- | --- |
| `preview-drawer.png` | The drawer renders real conversation titles verbatim — a shot of an in-use device publishes the client's project names with it |
| `preview-terminal.png` | Never captured. `TerminalActivity` is not currently reachable from the UI, so the shot could not be taken the way the others were |

To re-shoot the drawer safely: rename or delete the private conversations first, then capture an
empty-looking list. The drawer takes its titles from the device, not from the app, so nothing in the
code can filter what shows up in a screenshot.

## Privacy when re-shooting

Before capturing any screen, check what real names are visible in it:

- The **drawer** lists every conversation title on the device.
- The **question prompt** lists the git repositories opencode can see, by directory name.
- The **top bar** shows whatever the current chat is called — rename it if the auto-generated title
  came out as something like `Turn 1:….`