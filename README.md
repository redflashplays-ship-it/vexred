# VexRed — VexBot 3.1.0 extension

Target: Minecraft Java 1.21.11, Fabric, Java 21, VexBot `3.1.0+mc1.21.11`.

## Included commands

All commands are nested below `/vexbot vexred`:

- `/vexbot vexred randomchat on|true|yes|off|false|no|toggle|status`
- `/vexbot vexred usesigns on|true|yes|off|false|no|toggle|status`
- `/vexbot vexred kill random <amount> [except <user-or-team> ...]`
- `/vexbot vexred kill teamrandom <amount> <team> [except <user-or-team> ...]`

At most four exclusions are accepted. An exclusion token matching a VexBot team excludes its members; otherwise it is treated as a bot name. Kill commands build candidates from VexBot's tracked-bot registry and re-check bot identity before calling `BotManager.kill`, so they never choose arbitrary real players.

## Chat and signs

- Bots only start conversations when another player/bot is within 8.5 blocks.
- The system uses a local authored dialogue bank and keyword/topic matching; it has no external AI/API dependency.
- Player replies must match the current conversation topic. Unrelated text is ignored and does not consume the pending conversation.
- Bots on different non-allied VexBot teams rarely speak; occasional dialogue is limited to fighting remarks.
- With sign usage enabled, a portion of lines are written to signs. The extension uses a nearby existing sign if it has blank lines, preserving any text already on it; otherwise it attempts to place an oak sign on a nearby solid floor. If it cannot safely use/place a sign, it falls back to chat.
- Sign use is a presentation option for the random-chat system; disabling random chat stops spontaneous conversations.

## Build

1. Install JDK 21 and a current Gradle installation (or generate a Gradle wrapper for your environment).
2. Keep `libs/vexbot-3.1.0mc1.21.11.jar` in place. This local dependency is used for compile-time access to VexBot's public classes.
3. Run `gradle build` from this folder.
4. Install `build/libs/vexred-0.1.0.jar` alongside VexBot and Fabric API on the server.

The dependency versions are declared in `gradle.properties`. If Gradle reports that a particular Yarn/Fabric API artifact version is unavailable, update those version pins to a published 1.21.11 artifact.

## Important status

This is the initial source implementation, not a verified compiled release. The current execution environment has Java 21 but no Gradle installation and cannot reach external Maven repositories, so I could not run Loom compilation or an in-game test here. Please treat the first build as a compatibility pass; compile errors, if any, should be fixed against the exact Fabric/Yarn artifacts used by your server before installing it on a live world.
