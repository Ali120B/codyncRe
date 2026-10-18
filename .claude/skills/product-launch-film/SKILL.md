---
name: product-launch-film
description: Make a product launch film (60–90 s, 1080p60) the way the Codync launch film was made — a white-stage, continuously moving film where every piece of product UI is rebuilt as DOM, type arrives word by word out of blur, and every beat sits on a music grid. Use when asked for a launch video, product film, promo, teaser, release video or App Store preview for an app or developer tool. Built on HyperFrames (one index.html, one GSAP timeline).
---

# Product launch film

The method that produced the Codync launch film (https://youtu.be/awhZJPjJaPc). The film itself
is not the point; the point is the method, which works for any product. Read the whole file
before writing anything.

The film is made in four passes, each with its own deliverable and its own sign-off:

1. **Story**: a beat sheet in `BRIEF.md` (no pixels yet)
2. **Look**: three or four stills of the key frames
3. **Motion**: a 30 fps draft of the whole film
4. **Finish**: sound, timing polish, the 60 fps delivery render

Never skip a pass to save time. A film that is wrong at the story level cannot be saved by motion.

---

## 1. Story: one idea per beat, every claim shown

### The arc

The film is a sequence of beats that each say exactly one thing. The arc that works:

| # | Beat | What it does | Length |
|---|---|---|---|
| 1 | **The pain** | Shows the world without the product as an overwhelming *image* (a pile of windows, a flood of notifications), not as a sentence. | 3–4 s |
| 2 | **The pain, named** | One short card that states it. | 2.5 s |
| 3 | **Meet [icon] Name** | The name lands with the icon, on a bar line, ideally where the music opens up. | 2.5 s |
| 4 | **What it is** | A rotating noun: "Your *A.* / *B.* / *C.* / *the actual thing.*" The last noun is in the accent colour. | 5 s |
| 5 | **What it does** | A wheel of concrete jobs next to a fixed label ("Tell them to …"), one job per beat. | 6–7 s |
| 6 | **The hero flow** | One real task end to end, shown on the real UI: trigger → open → the key interaction → the result → a follow-up action. This is the longest beat and the reason the film exists. | 10–12 s |
| 7 | **Breadth** | The same thing working for every variant side by side (a lineup of devices, integrations, platforms), each doing its own small task. | 6–7 s |
| 8+ | **Section card → capability**, repeated | An eyebrow (`GROUP CHATS`) and one line introduce each further capability, then that capability plays as its own micro-story. | 3 s + 5–10 s each |
| n−3 | **Scale** | A wall of many instances, drifting, then dissolved. | 3 s |
| n−2 | **The tagline** | The one sentence you want remembered, the payoff half in the accent colour. | 2.5 s |
| n−1 | **Proof cards** | Short facts that remove objections ("Built in Rust. Native everywhere.", "Open source. Totally free."), each with icons or a sub-line. | 3 s each |
| n | **Lockup** | Icon and name, the URL underneath, on the last strong bar. | 4 s |

### Rules for the story

- **One idea per beat.** If a card needs a comma to hold two ideas, it is two cards.
- **Show, don't claim.** Every capability named on a card must be *seen working* right after it:
  a tap, typed text, an approval, a result arriving. A claim nobody sees happen is cut.
- **Text and UI alternate.** A card gives the viewer a breath and a frame; the UI beat that
  follows fills it. Never two cards in a row except at the very end, and never two long UI beats
  without a card between them.
- **Real content only.** Real product names, plausible commands, real-looking numbers, consistent
  characters (the same four named bots recur across every scene). Lorem ipsum or "Task 1" kills
  credibility instantly.
- **Each micro-story has a payoff**: a check mark, "Shipped v1.4.2", "✓ 5 checks passed". End
  every UI beat on a visible result, not mid-action.
- **Escalate scope**: one thing → several side by side → a room of them → a wall of them. The
  camera pulls back as the scope grows.
- **The reveal move**: show something full-frame, then pull back to show it was inside something
  bigger (the Mac desktop turns out to be on a phone). Use it once; it is the film's "oh".
- Copy is short and plain: sentence case, a period at the end, at most about 6 words per card.

### Deliverable

`BRIEF.md` with the frontmatter (message, length, fps, bpm) and a beat table: start time,
on-screen text, what the UI does. Get it approved before building. Template:
[template/BRIEF.md](template/BRIEF.md).

---

## 2. Look: the visual grammar

These rules are what make the film feel "silky". Apply all of them; dropping one shows.

**Stage**
- White stage (`#fff`), near-black type (`#0b0b0c`), exactly **one accent colour** (the brand
  colour), used only for the payoff words and one highlight per UI scene.
- One grotesk for everything on cards: ~68 px, weight 500, letter-spacing −0.035em, line-height
  1.16. Eyebrows 19 px, weight 600, tracking 0.16em, accent colour, uppercase.
- Fill or shadow, never a border line on filled shapes.
- Halftone texture on type cards only: dot fields that grow from two opposite corners at ~11 %
  opacity, cross-faded between two variants from card to card, off while UI is on stage.

**Type motion**
- Words enter one by one: from 20 px below, blur 12 → 0, opacity 0 → 1, `power3.out`, 0.7 s,
  75 ms stagger.
- Words leave upward into blur: −22 px, blur 10, `power2.in`, 0.45 s, 40 ms stagger.
- The next line starts while the previous one is still leaving. Nothing ever waits on an empty
  stage.

**Transitions**
- **No hard cuts.** Scenes change by depth of field: the outgoing scene blurs to ~16 px and fades,
  the incoming one resolves from blur ~14 px and scale 0.9.
- Dense scenes (window piles, walls) leave through a **halftone wipe**: white dots on a 44 px grid
  grow until the stage is clean.
- The icon arrives through a **dot burst**: 16 dots fall into it from a ring, then it scales in
  from 0.3 with a −30° turn.

**Camera**
- The camera is never still. Every UI scene sits inside a `.cam` layer that drifts slowly
  (`sine.inOut` over the whole beat) between the deliberate moves.
- Push in on whatever is about to be touched (`expo.inOut`, ~1.3 s, scale ~2), touch it, then
  pull back to show the consequence. The viewer should never have to search for the action.

**UI is rebuilt, not recorded**
- Every screen is DOM built in script: device frames, chats, cards, notifications, lock screen,
  desktop windows. Screen recordings blur when the camera pushes in, cannot be re-worded and
  drift between takes; DOM stays sharp at 2× and is edited like text.
- Port the product's own design tokens: colours from its theme file, its font, its corner radii,
  its real icons and avatars (port drawing code to a canvas if the app draws them).
- Touches are visible: a translucent finger dot travels in, presses (scale 0.76) and leaves a
  ripple. Desktop actions use a real-looking cursor with a click squeeze.
- Messages pop in (`opacity 0, scale 0.9, y 14` → rest, `power3.out`) while the list scrolls up
  by the new item's measured height.

### Deliverable

Build the first scenes, then `./hf snapshot --no-end --at <t1>,<t2>,<t3>` on the key frames (the
pain image, the Meet card, the hero flow's key interaction, the lockup) and get the look approved
on stills before animating the rest.

---

## 3. Motion: the music grid

**Pick the tempo first, then cut to it.** Every beat of the film starts on the grid.

- Choose a bpm (112 worked: unhurried but moving). One beat = 60 / bpm s (0.5357 s at 112);
  one bar = 4 beats. Size the film in whole bars (40 bars = 85.7 s).
- Put the moments that matter on bar lines: the icon landing, the start of the hero flow, the
  lockup. Pin those as absolute times (`T12 = 38 bars`).
- Everything else is **chained**: each scene's start is computed from the previous scene's end
  (`const T6 = tOut4 + 0.5`), so moving one beat moves everything after it consistently.
- Music opening: low-pass the first 2–3 bars and let it open on the bar where the name lands.
- Rotating lists step on the beat or on a fixed fraction of it (the wheel steps every 0.402 s =
  0.75 beat).

**Sound design** (all short, quiet, under the music)
- A soft pitched pop on every text switch, cycling through 4 pitches so a fast sequence plays as
  a little melody (`pop-a..d`, volume ~0.1).
- A soft click on every tap or cursor click (~0.28); a whoosh under big camera moves and wipes
  (~0.16); a pop under each arriving message (~0.16); a chime on success and on the lockup (~0.25).
- `<audio>` start times are static numbers in the HTML; when a beat moves, shift every cue after
  it by the same amount. Read the times off the timeline (`window.__marks`) instead of guessing.

### Deliverable

A 30 fps draft render of the whole film (`./hf render --fps 30 -o renders/draft.mp4`), watched
end to end with the user. Collect notes per beat, apply, re-render.

---

## 4. Finish

- Delivery: `./hf render --fps 60 --quality delivery -o renders/<name>.mp4`.
- Before calling it done, check the **rendered file**, not snapshots:
  - Extract frames around every transition (`ffmpeg -ss <t> -i film.mp4 -frames:v 1`) and look.
  - Flicker test: compare neighbouring frames. A large difference between frame k and k+1 but a
    small one between k and k+3 is flicker (different workers laid text out differently), not motion.
  - Every word readable for at least ~1.2 s after it settles; nothing clipped at the stage edge.
  - The final frame holds on the lockup for at least 2 s.
- Upload, then link the video from the README (a thumbnail image that links to the video; GitHub
  cannot embed players) and from the website.

---

## Build: one file, one timeline

Start from [template/](template/): copy it into the project's video folder (keep that folder out
of git; renders are large), then replace the example beats.

```
template/
  index.html       stage, helpers (word split, wIn/wOut, cam, tap, pop, halftone, wipe, burst, wheel), example beats
  BRIEF.md         the beat sheet to fill in first
  hf               runs the HyperFrames CLI: ./hf snapshot | check | render
  hyperframes.json
  assets/fonts     Geist + Geist Mono (all measured UI uses these)
  assets/sfx       pop-a..d, pop, click-soft, whoosh, chime
  assets/bgm/      create it and put bed.mp3 there (a licensed track at the chosen bpm), then add the <audio id="bgm"> line
```

- **One `index.html`, one paused GSAP timeline** registered as `window.__timelines["main"]`. No
  sub-compositions, no clip elements. Scenes are absolutely positioned `.scene` divs switched with
  `autoAlpha`; every scene that moves has a `.cam` layer inside it.
- All DOM is built in `build()` from small factories (phone, message, card, window), then the
  timeline is written top to bottom in film order, one commented block per beat.
- Positions are **measured**, not hard-coded (`offsetHeight`, `getBoundingClientRect`), so copy
  can change without re-tuning every scroll.
- Expose the chained times (`window.__marks = { T4, tOpen, … }`) for audio cues and snapshots.
- Old cuts go to `versions/index.vN.html.txt` (a second root `.html` with a composition id is a
  lint error).

## Pitfalls that cost hours (all hit for real)

- **Font race = flicker.** Layout is measured in script, so `build()` must wait for every font
  face: `Promise.all([...document.fonts].map(f => f.load()), document.fonts.load("600 16px Geist"), …).then(() => document.fonts.ready).then(build, build)`.
  Otherwise each render worker gets its own line wraps and the film flickers at ~20 Hz with
  clipped bubbles.
- **`system-ui` / `ui-monospace` differ between snapshot and render.** Everything that is measured
  uses the bundled Geist / Geist Mono. For a native terminal look use `local("SFMono-Regular"),
  local("Menlo")` via `@font-face`, never `ui-monospace` (it falls back to a proportional font).
- **`fromTo` renders its from-state at build time.** Anything that must stay invisible until its
  tween (ripples, late elements, later scenes' layers) needs `immediateRender: false`.
- **Snapshots lie about timing-sensitive problems.** Verify from a real render.
- `hf check` reports intentional overlaps (a wheel of items); mark those elements with
  `data-layout-allow-overlap` instead of rearranging them.
- Use `./hf`, never `npx hyperframes` (different version, different renderer).
- Render speed on an M-series Mac: ~50 s for a 30 fps draft, ~1.5 min for 60 fps delivery of an
  85 s film. Iterate on drafts.

## Working with the user

- Show stills before motion and a draft before the delivery render; ask for notes per beat.
- When the user rejects a look, ask what they liked in the rejected version before replacing it,
  then offer the plainest native version first (in the Codync film, plain macOS Terminal windows
  beat every stylised "IDE" treatment).
- Keep the previous cut in `versions/` so any reverted idea is one copy away.

## Worked example

The Codync film's source is at `videos/codync-silk/` in this repo (git-ignored, so only on the
author's machine). If it is there, read it for the full set of factories this template leaves out: the
pile of Terminal windows, the iPhone with chat, approval card and composer, the notification, the lock
screen with a Live Activity, the Mac app window, the landscape phone showing a remote desktop, the wall,
the platform icons.
