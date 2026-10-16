---
name: ship-deploy
description: "Ship something the user asked for — build it, run it, prove it works, then deploy and hand it back. Covers verifying a production build, deploying to Vercel or publishing a Supabase migration with the stored service keys, checking the live URL rather than assuming, and delivering files out of the container. Use when the task ends with 'deploy', 'publish', 'ship it', 'hand it over', or whenever a build is about to be called finished."
license: Apache-2.0
metadata:
  opencode/slash: "true"
  opencode/autoinvoke: "true"
---

# Shipping and handing over

Finishing the code is not finishing the task. The task ends when the user has
something that runs.

## Before deploying anything

1. **Production build, not dev.** `next build && npm run start`, or `gradle
   assembleRelease`. A dev server running fine proves nothing about the build.
2. **Run the built output** and hit the routes that matter. Not the root — the page
   you changed, the form you wired, the API you added.
3. **Check the console and the server log.** A page that renders while logging a 500
   on a missing env var is not done.
4. **Mobile width, and the keyboard.** 360px with the keyboard up. This is the device
   it will be opened on.
5. **Airplane mode.** What is genuinely needed after first load?

If any of that fails, fix it now. Deploying a broken build only makes the failure
further away from its cause.

## Vercel

The token is already in the environment. Do not ask the user for it.

```sh
source /root/.cody-credentials.env

npx vercel whoami                          # confirm the token and the account
npx vercel --prod --yes                    # real deploy, not a preview
```

- `--yes` skips the interactive prompts that will otherwise hang forever on a phone.
- **Read the URL from the output.** Then actually fetch it:

```sh
curl -sS -o /dev/null -w '%{http_code}\n' https://<the-url>
```

A deploy that returns 200 on the root can still 404 on every route that has a
parameter. Check the routes you built, with real values.

## Supabase

```sh
source /root/.cody-credentials.env

supabase projects list
supabase link --project-ref <ref>
supabase db push --include-seed              # migration lives in supabase/migrations
```

Take the project ref from the dashboard URL — the link step is interactive otherwise.

## Never

- Print a stored key to verify it. `gh auth status` proves a token without showing it.
- Deploy to production to "just check". Use a preview first unless asked.
- Claim it works because the command exited zero. Fetch the thing.
- Leave a `.env` with real values in a directory the agent might commit.

## Handing files back

The user cannot see the container's filesystem. They get files through the app.

- **HTML** — put it beside its assets in the same folder, reference them relatively,
  and name the file. The app renders it inline.
- **Images, zips, anything else** — say which file it is. They take it from the app's
  Files sheet or the share sheet.
- **A repo or a script** — make sure it runs from a clean checkout, with no absolute
  paths and no assumptions about this container. Anything referencing `/root` or
  `/data/data/com.termux` will not work anywhere else.

## The report

Say what you did, what you checked, and what you could not check. In that order.
No summary of the diff — they can see it.

If something is still broken, say so plainly. "Deployed, but the contact form is not
wired to an endpoint yet" is useful. "All done!" is not.