YouTuber Recs - Paper plugin source (Minecraft 1.21.11 target)

IMPORTANT LIMITATIONS:
- This source is not a compiled JAR.
- /npc spawn creates marked Villager-based NPC-like entities, not true player-profile NPCs.
- /npc select selects the nearest marked NPC within range.
- /npc inventory is currently a read-only equipment display; it does not support item transfer.
- /npc kit load equips the selected entity's main hand, offhand, and armor.
- Basic follow/circle/attack behavior uses Paper Mob pathfinding. It is not human-level PvP AI.
- Elytra mace combat was intentionally omitted.
- Verify the Paper API version/build and compile with Java 21.

Build:
1. Install Java 21 and Gradle.
2. In this folder, run: gradle build
3. Find the JAR in build/libs/YouTuberRecs-1.0.0.jar
4. Upload it to your Paper server's plugins folder and restart.

Commands:
 /npc spawn <number> <name>
 /npc select
 /npc inventory
 /npc rename <name>
 /npc follow <player>
 /npc circle <player>
 /npc kill <player>
 /npc stop
 /npc kit
 /npc kit create <name>
 /npc kit load <name>
 /npc kit clear
 /npc kit list
 /npc kit delete <name>

Requires OP or youtuberrecs.admin permission.
