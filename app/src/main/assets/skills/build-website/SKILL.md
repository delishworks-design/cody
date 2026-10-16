---
name: build-website
description: "Build a production website or web app end to end — scaffold, type and colour tokens, components, responsive layout, accessibility, metadata, and a performance budget. Use when asked to build, rebuild, or restyle a page, landing page, dashboard, or any Next.js, React, Vite or Tailwind project."
license: Apache-2.0
metadata:
  opencode/slash: "true"
  opencode/autoinvoke: "true"
---

# Building a website

## Order matters

Do it in this order. Skipping to components is what produces a page that is polished
and wrong.

1. **Find out what exists.** Read the project before writing. Package manager, framework version, styling approach, component library, existing tokens. Match what is there — do not introduce a second styling system into a codebase that has one.
2. **Fix the tokens before the components.** Colour, type scale, radius, spacing, shadows. This is where taste happens and it is a decision you cannot defer. See `anti-slop-ui`.
3. **One real typeface pair.** A display face with a voice, a body face. Load them with `next/font` or `fontsource`, self-hosted, with `display: swap`.
4. **Build the page from real content.** Write the copy first, even in a comment. Layout decisions made against placeholder text are made twice.
5. **Break it before shipping it.** 360px with the keyboard open, 200% zoom, long German strings, empty and loading states.

## Tokens, not literals

No raw hex, no arbitrary spacing values in components.

```css
:root {
  --color-bg: oklch(18% 0.02 265);
  --color-surface: oklch(22% 0.02 265);
  --color-text: oklch(96% 0.01 265);
  --color-muted: oklch(68% 0.02 265);
  --color-accent: oklch(62% 0.19 28);
  --radius-card: 6px;
  --radius-pill: 999px;
  --space-1: 4px;  --space-2: 8px;  --space-3: 12px;
  --space-4: 16px; --space-6: 24px; --space-8: 32px;
  --space-12: 48px; --space-16: 64px; --space-24: 96px;
  --text-sm: 14px; --text-base: 16px; --text-lg: 20px;
  --text-xl: 28px; --text-2xl: 38px; --text-3xl: 56px;
}
```

One scale. If a value is not on it, either it belongs on it or the design is wrong.

## Layout

- A real spacing rhythm beats arbitrary padding. Pick a base unit and stay on it.
- Measure: 60–75 characters for body text. `max-width: 65ch`, not `max-w-3xl` everywhere.
- Let content decide column counts. Three cards is not a layout decision, it is a habit.
- Responsive by content, not by breakpoint archaeology: use `grid-template-columns: repeat(auto-fit, minmax(240px, 1fr))` and let it reflow.
- Prefer asymmetric or offset layouts where the content allows. Perfect symmetry reads as generated.
- Hover and focus states on everything interactive. `:focus-visible` with a real ring, never `outline: none` without a replacement.

## Loading and empty states

Every list and every async view needs all three, designed at the same time as the happy path:

- **Loading** — a skeleton that matches the final layout's dimensions, so nothing jumps.
- **Empty** — say what goes here and give the action that fills it. "No projects yet. Create one."
- **Error** — say what failed and what the user can do. Never a bare "Something went wrong."

## Accessibility, in the first draft

- Semantic elements before ARIA: `<button>`, `<nav>`, `<main>`, `<h1>`–`<h3>` in order.
- Visible focus. Never removed without a replacement.
- Contrast: 4.5:1 for body text, 3:1 for large text and UI boundaries.
- `alt` text describing the content, not the file name.
- Every control reachable and operable by keyboard.

## Performance budget

| | Budget |
| --- | --- |
| LCP | < 2.5s on a mid-range phone |
| CLS | < 0.1 |
| JS shipped | < 150kB for a content page |
| Fonts | ≤ 2 families, ≤ 2 weights each, subset |

- `next/image` or `<img loading="lazy">`, always with explicit dimensions.
- Ship less JavaScript. A server component that renders HTML beats a hydrated island that draws the same HTML.
- No animation library for a hover. CSS transitions are smaller and smoother.
- Self-host fonts. A third-party font request is a third-party dependency and a render delay.

## Metadata

```html
<title>Specific — "Acme — invoice reminders that chase themselves"</title>
<meta name="description" content="One sentence with a real verb and a real noun.">
<meta property="og:title" content="…">
<meta property="og:description" content="…">
<meta property="og:image" content="/og.png">  <!-- 1200×630, under 300kB -->
```

A title of "Home" or a description of "Welcome to our website" is unfinished, not minimal.

## Before you say it is done

1. Read `anti-slop-ui` and run its ten-point acceptance bar. Fix the highest-impact failure. Repeat.
2. Load it at 360px, 768px, 1440px.
3. Tab through the whole page.
4. Turn on the network throttle and reload.
5. Check the console.
6. Read the copy once, out loud. Rewrite anything you would not say to a person.

Report what you checked and what you found. Do not report "looks great".