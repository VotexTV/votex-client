# Minimal Play page checkpoint

The Play route now contains a single character showcase over22 original translucent CSS cubes, a restrained idle body sway, an independent skin-animation switch and one launch dock. A two-way switch selects normal launching or the most recently joined server. Profile/sign-in/offline-name controls live in the dock's rounded popover; news, promotional cards, extra panels and advanced character controls were removed from this route. Other navigation remains available.

The last launched profile is read from the backend once on initial Play load, without repeatedly undoing the user's later profile selection. The last server is derived from successful Core JOIN records for known profiles. A missing record shows an honest empty state and disabled Join button. Join uses the profile which actually joined that server and passes the validated address through a separate QuickPlay argument. It does not invent player counts or a connection result.

Cubes pause for hidden documents, reduced-motion, disabled ambient motion, Ultra Performance and busy/running game states. Skin sway follows the existing idle/performance/reduced-motion conditions and renderer visibility/focus suspension. Skin-off leaves independently enabled cube motion intact. 3D orbit interaction and equipped look remain in the existing SkinPreview renderer; its editing/capture toolbar is absent on Play.

Validation: TypeScript compilation,76 frontend regression tests and Vite build passed. Actual browser UI was inspected at1280x720 and via same-origin iframe views at800x560 and1000x650, including the small-window popover. Escape closes the popover and restores its picker focus; outside interaction dismisses it. DOM observation confirmed22 cubes, running CSS animation and page-scrollHeight equal to clientHeight. Skin switch-off was checked independently from cube motion and restored. Browser preview has no desktop launch authority, so Launch/Join remain disabled there.

The test browser cannot create WebGL. It renders a clearly labelled2D actual-skin fallback instead of a broken white canvas. These browser screenshots prove layout, not native3D rendering or actual QuickPlay process launch. The native paired clients independently proved the new successful-JOIN file; see PLAY-HISTORY.md. No new launcher installer or public website was published.

Internal visual artifacts: work/ingame-goal/play-browser-preview.png and play-options-800x560.png. apps/launcher/play-review.html is a local development-only frame-size harness and is not a Vite production entry or public asset.
