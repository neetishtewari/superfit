# Monthly recap plays like a story, with music

Asked for by Neetish on 2026-11-01 ("autoplay mode with some bg music, similar to how Google Photos packages memories"), approved as the quick win: autoplay in the app first, a shareable video later.

## Autoplay
- Story cards move on by themselves. Each one stays up for 5.5 to 10 seconds, depending on how long its words are (`RecapAutoplay.durationMs`).
- Progress segments at the top show one segment per story card.
- Tap the right two-thirds of a card to go forward and the left third to go back. Hold to pause. Swiping still works.
- Autoplay pauses while a card is being shared, while the app is in the background and while you swipe.
- It stops on the last story card and logs `recap_watched` with the number of cards.
- The "Get more from Superfit" card never auto-plays. A "Get more from Superfit" text button under the cards jumps to it.
- The big number on each card counts up as the card arrives, and its bars grow. Paces and times of day stay as they are. Shared pictures always show the full values.

## Music
- One original 40-second loop (`res/raw/recap_music.ogg`, about 640 KB). It was synthesised from scratch by `tools/music/recap_music.py`, so there are no rights to clear and it is safe in shared videos later.
- It plays quietly at 60% volume and fades in and out. It pauses with the story and stops when the recap is closed.
- It starts muted when the phone is on silent or vibrate, when the media volume is 0, or when another app is playing music. Otherwise it follows the speaker button in the header, which is remembered.
- It uses Android's own MediaPlayer, so there is no new library and no new permission.

## Later
- Save the recap as a video with the music, for Instagram and WhatsApp.
- A track per coach style.
- Optional meal photos (opt-in).
