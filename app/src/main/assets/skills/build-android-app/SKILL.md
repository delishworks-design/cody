---
name: build-android-app
description: "Build and maintain native Android apps in Kotlin with Jetpack Compose — activity and screen structure, state holders, theming, edge-to-edge, permissions, build types and release signing. Use when asked to build an Android app, add a screen, work on a Compose UI, fix an Android build, or prepare a signed release."
license: Apache-2.0
metadata:
  opencode/slash: "true"
  opencode/autoinvoke: "true"
---

# Building an Android app

## If the workspace is this repo

Cody is a working Kotlin + Compose app and it is the better reference than any
document. Read the real files before inventing a pattern:

| Question | File |
| --- | --- |
| How is a screen laid out and composed | `app/src/main/java/com/termux/app/ui/screens/` |
| How state is owned and survives process death | `chat/OpenCodeRuntime.kt` |
| How one immutable snapshot drives a list | `chat/ChatUiState`, `StateFlow` |
| How theming is done | `ui/theme/Theme.kt`, `ui/theme/Color.kt` |
| How navigation works | `MainActivity.kt` |
| How a long file is kept navigable | the section banners in `ChatScreen.kt` |

When you are outside this repo, the rules below stand on their own.

## Compose, honestly

- **State lives in a state holder, not in the composable.** If a value must outlive recomposition, hoist it. If it must outlive the screen, it belongs in a ViewModel or a process-scoped object.
- **Read state as state.** `collectAsStateWithLifecycle()`, not `collectAsState()` — the latter keeps collecting while backgrounded.
- **Pass lambdas down, state up.** A composable that takes an `onClick` can be tested and previewed. One that reaches for a singleton cannot.
- **`remember` for what recomputation should not redo; `rememberSaveable` only when the system may destroy and restore.** `rememberSaveable` on a dropdown flag is wrong — nobody expects a menu waiting for them after rotation.
- **Keys on every list.** `items(state.messages, key = { it.id })`. Without keys, a reordering animates by content and looks broken.
- **Do not nest scroll containers.** A `SelectionContainer` around a `LazyColumn` swallows the drag; put it per item. A `verticalScroll` inside a `DropdownMenu` fights the menu's own measurement.
- **Read a `LazyListState` inside `snapshotFlow`, not before layout.** A "is the user at the bottom?" test that runs before the new item is measured judges a fresh message off-screen and never scrolls to it.
- **One file per concern past ~800 lines.** Extract, then add a section banner comment explaining why the split happened.

## Layout and theme

- **Never hardcode a colour.** Every value resolves through a token in `ui/theme/Color.kt`, so a palette change is one file.
- `enableEdgeToEdge()` means the screens inset themselves. Forgetting `statusBarsPadding()` puts the top bar under the clock and makes it untappable.
- Compose sizes are `dp`, text is `sp`. A `sp` in a size modifier does not scale.
- Type scale: bump the Material defaults up a step or two for a phone. 12sp body is fine on a tablet and cramped at 360dp.
- Pin `LocalContentColor` if the screens paint their own backgrounds instead of wrapping in a `Surface`. Unpinned it stays `Color.Black` and every text without an explicit colour goes black-on-black.

## Threading

- Main dispatcher for anything the UI reads. That is the default, and it is why no locking is needed.
- `Dispatchers.IO` for every blocking call: HTTP, file I/O, the SSE reader, a process wait.
- Never block the main thread — including `Thread.sleep`, which the compiler will not warn you about.
- Do not launch work in a composable body. `LaunchedEffect(key)` or a view-model scope.

## Manifest and permissions

- Ask for a permission **at the moment of use**, never at launch, and explain why first.
- Handle the denial path. It is a real screen, not a log line.
- Declare every activity. Set `android:exported` explicitly.
- `allowBackup="false"` for anything holding secrets or a large extractable tree.

## Build

```
gradle :app:assembleDebug     # iterate
gradle :app:assembleRelease   # before shipping
```

- `assembleRelease` produces an **unsigned** APK. Sign it with `apksigner` outside the build so no keystore material or signing config ever enters the repo.
- `zipalign -f -p 4` before signing.
- `apksigner verify --print-certs` afterwards. A build that was never verified is not a build.
- **A signing key is an identity.** Generating one per build means no user can ever install an update. Keep it in a secret and reuse it.

## Before you say it is done

1. Compile it. An unbuilt change is not a change.
2. Run lint.
3. Check the logcat for exceptions you did not expect.
4. Rotate the device. Press home mid-request. Check the state survived.
5. Turn on airplane mode and check what still works.
6. Read every string out loud. Remove anything you would not say to a person.