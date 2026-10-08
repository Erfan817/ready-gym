# Exercise media provenance

## Included original

- Local asset: `app/src/main/assets/exercise-demos/squats-wensceslao.gif`
- Mapped stable ID: `rg-v1-bodyweight-squat` (徒手深蹲), no equipment visible.
- Title: **Squats.gif**; author: **Wensceslao**.
- Source/author permission: https://commons.wikimedia.org/wiki/File:Squats.gif
- Source wikitext checked: https://commons.wikimedia.org/wiki/File:Squats.gif?action=raw declares `{{self|cc-by-sa-4.0}}`.
- Commons mirrored file description checked: https://en.wikipedia.org/wiki/File:Squats.gif explicitly names CC BY-SA 4.0 International.
- License: **Creative Commons Attribution-ShareAlike 4.0 International** — https://creativecommons.org/licenses/by-sa/4.0/
- Original file: https://upload.wikimedia.org/wikipedia/commons/e/e6/Squats.gif
- Original uploaded: 2020-05-19 22:48 UTC; original pixels/animation unchanged. Renamed locally only; runtime aspect-fit scaling is not an edited asset.
- 559677 bytes; SHA-256 `a5d98e1abccc82eaf946bacf65af914c9def49f5e5de8d6e9018ab9f1d38cdaf`.
- Pillow verified 640×480, 66 distinct frames, 40ms per frame, 2640ms loop, GIF loop=0 (indefinite).
- Six sampled original frames: `build/verification/media/squat-contact-sheet.png`. Visual review identifies an unweighted squat sequence. Schematic outline does not establish precise joint tracking or professional execution.
- No author endorsement, clinical validation, or professional coach review is implied. GIF is an identification reference, not a prescription; Chinese cues are locally authored, not quotations attributed to Wensceslao.
- Any distributed edited derivative of this GIF must retain applicable attribution and CC BY-SA obligations. The included file remains under CC BY-SA 4.0 independently of application code licensing.

## Missing assets — explicitly not replaced

The following current template IDs have no bundled matched licensed GIF in this stage:

| Stable ID | Exercise | Gap |
|---|---|---|
| `rg-v1-knee-push-up` | 跪姿俯卧撑 | Wall-push-up and standard push-up are not matching demonstrations |
| `rg-v1-glute-bridge` | 徒手臀桥 | No matched animated asset with verified file-level license included |
| `rg-v1-dead-bug` | 死虫式核心练习 | No matched animated asset with verified file-level license included |
| `rg-v1-dumbbell-row` | 双侧俯身哑铃划船 | Bench-supported one-arm row/resistance-band row is not this movement |
| `rg-v1-barbell-row` | 俯身杠铃划船 | No matched GIF included; a Commons video requires separate license/visual/conversion verification |

`hasaneyldrm/exercises-dataset` GIFs were NOT used: its MIT license excludes Gym Visual media, which requires separate permission. A request to download is not permission from its copyright holder. No hotlinked media, INTERNET permission, model calls, user body records, or secrets are required.

## Integration

Use `ExerciseDemo(stableId: String, modifier: Modifier = Modifier)` under the app's `ReadyGymTheme`. The parent plan UI owns selection/expansion; prefer rendering only the current expanded exercise. `ExerciseMediaCatalog.find(stableId)` returns authored muscles, cues, cautions, media status, and source attribution without referencing or mutating the domain catalog.

Controls default to no loading/no animation until a user taps play. Missing/unknown IDs and decode failure remain visibly unavailable, not successful loading. Pause and leaving the screen release scheduled animation. Native decoder: API 28+ `ImageDecoder` / `AnimatedImageDrawable`; Android 26–27 `Movie` with a time-driven custom View. Host native Robolectric is not device execution. API35 native `ImageDecoder` start/stop and Compose tap/pause are exercised. Legacy `Movie` is NOT verified by this Robolectric version: its native decoder is not shadowed and returns null in the API35 host sandbox. The fallback test is explicitly ignored, with its original real failure retained in `build/verification/media-final-green.log` (despite that provisional filename, that log failed; acceptance uses `media-completed-verification.log`). Verify API26–27 on a real device before asserting legacy runtime support. The initial extended run was interrupted at asset preparation by a tool timeout and has no test verdict.
