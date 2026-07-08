# TapCheckers ⛃

A full game of **checkers** (English draughts) against a computer opponent,
built natively for the **RayNeo X3 Pro** AR glasses. Move a cursor with
right-temple **swipes**, **click** to pick up and place a piece — and when a
move isn't legal, TapCheckers **tells you why**.

Part of the X3 game suite (TapChess, Breakthrough, …), so it inherits every
on-device-proven pattern: 640×480 logical canvas, binocular side-by-side
rendering, pure-black waveguide background, one-swipe-per-step navigation,
dwell-to-commit moves, auto-save/resume — zero vendor AARs, zero permissions,
zero binary assets (the whole engine, AI, and sound set are pure Kotlin,
synthesized at runtime).

## The rules (American checkers)

- 8×8 board; each side starts with **12 pieces** on the dark squares.
- **Men** move one square diagonally forward; **kings** move one square
  diagonally in any direction.
- **Capture** by jumping an adjacent enemy piece into the empty square beyond.
- **Captures are forced** — if you can jump, you must, and a jump **keeps going
  with the same piece** until it can't take again (multi-jump).
- A man reaching the far row is **crowned a king** (which ends the turn).
- **Win** by capturing all enemy pieces, or leaving your opponent with no move.

## Controls (right temple pad)

| Gesture | Action |
|---|---|
| **Swipe ↑ ↓ ← →** | Move the cursor one square (menus: navigate) |
| **Click** (temple tap) | Select your piece · place it · confirm |
| **Double-tap** | Open / close **settings** any time |
| Left temple pad | System volume (ignored) |

Select a piece (its legal squares light up), swipe the cursor to a target, wait
for the ring to fill (~2s — the dwell guard against a stray swipe), then click
to commit. During a multi-jump the piece stays selected and the board prompts
you to **keep jumping** until the chain is done. Illegal attempts are explained
instantly. Also plays on a plain touchscreen with the same gestures.

## Computer difficulty

Five tiers, on the title screen (swipe) or in settings:

| Tier | Strength |
|---|---|
| **1 · Novice** | Shallow search, blunders often — a gentle start |
| **2 · Amateur** | Deeper, occasional slips |
| **3 · Skilled** | Plays soundly |
| **4 · Expert** | No mercy |
| **5 · Master** | Deep alpha-beta search |

Alpha-beta search with an evaluation weighing material (kings worth more),
advancement toward promotion, back-row defense, and center control — capped by
a per-move time budget and run on a background thread so the UI never stalls.

## Speed mode

A chess clock in settings: **10:00 / 5:00 / 3:00 / 1:00** per side, ticking on
the mover's turn, flashing red under ten seconds, flagging the game at zero.
Leave it **Off** for untimed play.

## Settings (double-tap)

Difficulty · play as White/Black · speed mode · show legal moves · show
coordinates · sound volume · swipe sensitivity · flip vertical/horizontal ·
safe tap · particles · frame cap · new game · undo move · resign · reset stats ·
reset settings (at the bottom). One-swipe-per-step navigation.

The game auto-saves after every move (and on pause) and resumes on next launch.

## Build & install

```bash
cd ~/Projects/TapCheckers
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A built `TapCheckers.apk` also ships in this repo's root for a quick sideload.

Toolchain: gradle 8.9 · AGP 8.7.3 · Kotlin 2.0.21 · JDK 17 · compileSdk 35 /
minSdk 29.

## X3 specifics honored

- Black is transparency: the board floats as neon light on the world
- No `ar_mode` meta-data (it would halve the display to one lens)
- Temple click read as a KEY event; swipes classified by net displacement on
  finger-up; one gesture = one step everywhere
- `cyttsp6` (left volume pad) filtered out by device *name*
- RayNeo hardware detected by manufacturer/brand/product, not `Build.MODEL`
  (the X3 Pro reports `ARGF20`); SBS auto-defaults on
- Dwell-to-commit guards against finicky swipes; AI on a background thread;
  the sleep button auto-pauses into settings; the game persists after every move
