# Social invitation feedback,2026-10-24

The outgoing invitation used to remain visible while the consenting players were already shaking hands. SocialFeedback now associates that notice with its target UUID. Scheduled local acceptance clears only that invitation; an unrelated later error is retained. Expiration and connection reset remove stale feedback, preventing a prior server's notice from appearing in the next session. Incoming consent still takes precedence and explicitly asks for G.

Native social notices use the same physical text density as Votex workspaces, a rounded glass surface, a smooth icon, wrapped text and space above the native hotbar/XP area. Their entrance eases into position and is immediate when menu animations are disabled. The device/voice consent policy is unchanged.

Three new unit tests cover matching/wrong-peer acceptance, intervening errors, expiration/reset and layout bounds/readability/hotbar clearance over768x480 through4K at GUI1–4. Java100 tests pass; production/gametest/JAR/source builds succeed.

Native chat-density-run-03 exited0. Its socialFeedback proof covers four real render sizes:768x480 GUI1/3,1280x720 GUI2 and1920x1080 GUI3. The test delivers synthetic invitation/emote packets through the actual receive/tick path and verifies outgoing notice removal, unrelated error retention, reset and reduced-motion rendering. All seven social PNGs were inspected, including visible-before/absent-after acceptance and a wrapped two-line error. The private client saved all dimensions and stopped normally.

This is a single-client synthetic-packet/UI test. It does not replace paired05's real TCP/G-consent proof or claim a new two-player test after this UI-only change. It opens no microphone and sends no public chat. Exact proof: work/ingame-goal/chat-density-run-03/clientGameTest/config/votex/goal-evidence/native-chat-density-proof.json; its launch-result.json records exact source hashes. Gametest classes and its private clipboard mixin stay out of the production Core. Source is newer than immutable Preview2; no EXE or website was replaced.
