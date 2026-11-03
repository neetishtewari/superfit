# Save the monthly recap as a video

Neetish, 2026-11-02: the second half of the "Google Photos memories" idea. The whole recap becomes one short video with the month's music, ready for Instagram or WhatsApp.

- There is a "Share as video" button (Neetish 2026-11-02: share, not download; it opens the share sheet straight away) under the recap cards.
- The app captures each story card at its final values and leaves out "Get more from Superfit".
- Each card holds for about 4 seconds, less when there are many, so the video stays under about 45 seconds. As in the app, each card's numbers and bars count up over about 1.1 seconds when it comes on (drawn 12 times a second), then the card swipes to the next over 0.45 seconds. Neetish, 2026-11-02: the first version (still cards with fades) felt like a slideshow.
- The video is 720 x 1280 (9:16) at 24 fps.
- It plays that month's original track, looped to fit, with a fade in and a fade out.
- It is made on the phone with Android's own encoders: H.264 video, AAC audio and MP4. There is no new library, nothing is uploaded and no new permission is needed.
- The file goes to the share sheet, where the user can pick Instagram or WhatsApp, or save it. It is kept in the app's cache until the next share.
- While the video is being made, the screen shows "Making your video… N%".
- If the phone can't encode, a toast says so, and single-card sharing still works.
- Analytics: `share_card_shared` with type `recap_video`.
