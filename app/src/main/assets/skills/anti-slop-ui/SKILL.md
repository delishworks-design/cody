---
name: anti-slop-ui
description: "Design and review UI that does not look AI-generated. Use when building or reviewing any interface, page, component or visual change — a landing page, a dashboard, a settings screen, a card grid. Encodes the tells that make output read as machine-made, the token discipline that prevents them, and a critique loop that actually converges."
license: Apache-2.0
metadata:
  opencode/slash: "true"
  opencode/autoinvoke: "true"
---

# Anti-slop UI

A model with no direction returns the statistical average of every interface it was
trained on: Inter, a purple-to-indigo gradient, a centred hero, three rounded cards
with icons. That average *is* the look everyone recognises as AI. The fix is not
better taste — it is removing the vacuum. Give exact values and the model becomes an
executor instead of a taste-maker.

## The loop, not the list

A list of banned patterns does not fix anything. Plenty of linters list what is wrong
and stop there. What works is:

1. Build with constraints already fixed (tokens below).
2. **Critique** the result yourself against the bar at the bottom of this file.
3. Fix **the single highest-impact problem**.
4. Re-check. Repeat until it passes or you have spent three rounds.

One fix per round. Three vague fixes produce mush.

## Colour

| Tell | Instead |
| --- | --- |
| `#6366f1` → `#a855f7` gradient on the hero, buttons, glows | One accent you can defend. If you keep a gradient, be able to say what it encodes |
| Gradient text via `background-clip: text` | Weight. It costs legibility and means nothing |
| Dark mode as `#0a0a0a` plus one neon accent | Real midtones. A palette that thinks "dark" is one colour has no hierarchy |
| Warm amber/stone "cozy" wash on beige | Same failure as the purple, one generation later. Equally an absence of decision |
| No dominant colour; everything equal weight | Pick a dominant. Hierarchy is the job |

Define these as tokens before writing components, and give them real values:

```css
:root {
  --color-accent: oklch(62% 0.19 28); /* a real brand colour, not purple */
  --color-bg:     oklch(18% 0.02 265);
  --color-surface:oklch(22% 0.02 265);
  --radius-card:  6px;
  --radius-pill:  999px;   /* only where a pill belongs */
}
```

## Type

- **One display face with a voice**, one body face. Using the body face for headlines is the single most common tell.
- Inter, Roboto, Open Sans, Poppins, Lato as *display*: these have no voice. Fine for body.
- Serif-italic on one word for drama ("Ship *faster*"). Use weight, position, or a line break.
- Playfair, Lora or Cormorant as the body of a dashboard. Reserve serifs for a deliberate editorial voice.
- Wide tracking auto-applied to every uppercase label. It was a style once; now it is a tell, and it hurts readability.
- Emphasis budget: at most one accent per paragraph. When everything is emphasised, nothing is.

## Layout and shape

- `rounded-2xl` on buttons, cards, inputs, avatars, modals, images *and the page wrapper*. Nothing is ever sharp, so hierarchy disappears.
  - Fix: a named radius scale. Pick values so some things are visibly sharper than others.
- Three feature cards in a row. Always three. Let the content decide the number; two, four, or a list is a real answer.
- The centred hero stack: pill badge, 48–72px headline, subhead, two CTAs (one filled, one ghost), `max-w-3xl`, all on the centre line. This one layout is the most recognisable output there is.
- Dead-centre symmetry with equal gutters and identical card heights. No visual tension, no reason to look anywhere first.
- Bento grid on a dark background, every cell wanting to be the hero.
- Glassmorphism — `backdrop-blur-xl bg-white/10 border-white/20` on a card containing a paragraph. Frosted for no reason.
- The same shadow on every card: `blur-2xl opacity-0.1 offset-y-4`. Invisible by design and used anyway.
- Does every section need a box? Often the answer is no, and the answer is what separates a designed page from a component dump.

## Imagery

- Stock: "diverse group of people looking at a laptop in an impossibly well-lit office."
- Floating abstract 3D blobs behind the hero, connected to nothing.
- AI illustrations with a plastic, too-smooth symmetry.
- **Replace with the real thing**: actual product screenshots, actual data, actual output. Specificity reads as confidence.

## Components and content

- A pastel rounded square holding a Lucide glyph that has nothing to do with the content. `Zap` for speed, `Shield` for security, `Sparkles` for anything AI. The glyph should be the obvious one *because of the content*, not because of the icon set.
- Emoji bullets: ✨ 🚀 ⚡ 🎯
- A pulsing green dot labelled "Live" on something that is neither live nor new. A flat dot and a word, if it is real.
- A coloured 4px left border on a rounded card, on every list item.
- A badge above the headline ("Now in beta") — decoration above the thing that matters.
- Fake testimonials: DiceBear silhouettes, "John D., CEO at Acme", three columns.
- A "Trusted by" logo bar for companies the product has no relationship with.
- Copy voice: "It's not just a website — it's an experience." "Say goodbye to clutter." Triads of adjectives, heavy em-dash use, and no noun you would actually say.
- Invented insights ("you spent too much on coffee") presented as findings.

## Copy

Specific beats grand. "Cut the deploy from 9 minutes to 40 seconds" is worth reading; "Scale without limits" is the verbal equivalent of a purple gradient. Write what is true about this product, for these users, in this situation.

## Accessibility is not a later pass

Contrast, focus states, keyboard reachability, `alt` text, and semantic elements are part of the first draft. Adding them at the end is where they get missed, and they are what separates a draft from a component dump.

## Acceptance bar

Do not call the work done until you can answer yes to all ten:

1. Is there **one** accent colour, and can I say why that one?
2. Do I have a display face that is not the body face?
3. Is there a radius scale with real variation, or is everything the same?
4. Are there any gradients? Each one must have a reason.
5. Do any of these appear: purple hero, three cards, emoji bullets, glassmorphism, gradient text, glow, "Trusted by", fake testimonials, "Live" dot, left-border card?
6. Is the hero centred? If so, does it need to be?
7. Is there one point of visual tension, or is it symmetric everywhere?
8. Are the images real screenshots or real data — not stock, not generated art?
9. Does the copy name a real thing, or is it all adjectives?
10. Does the layout survive at 360px with the keyboard up?

If any answer is no, that is the next round — pick the highest-impact one.