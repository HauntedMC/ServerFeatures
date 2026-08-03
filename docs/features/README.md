# ServerFeatures Feature Reference

This directory documents all **58** feature modules currently present in ServerFeatures. Each page combines operator-facing behavior with the Paper lifecycle and developer contracts.

## How to use these pages

- Start with **Overview** and **Commands and permissions** when configuring a server.
- Read **Runtime behavior and internals** before changing lifecycle, persistence, messaging, or entity behavior.
- Treat the generated runtime configuration/messages as authoritative for exact defaults.
- Update the relevant page in the same pull request whenever a command, permission, placeholder, message variable, integration, or intrinsic changes.

## Player experience

- [AFK](afk.md) — Tracks active and idle players, supports manual AFK toggling, optional state broadcasts, automatic kicks, and anti-AFK pattern detection.
- [Skins](skins.md) — Allows players to apply another Minecraft account's skin to themselves and lets staff apply or remove skins for online players.
- [Titles](titles.md) — Displays configured title/subtitle content to players when they join or finish logging in.
- [Tablist](tablist.md) — Builds and refreshes the server tab-list header, footer, and player presentation.
- [Bossbar](bossbar.md) — Shows configurable rotating or persistent boss bars to eligible players.
- [Glow](glow.md) — Provides a GUI for choosing a permitted glowing color and persists the active selection through the feature's handler.
- [Balloons](balloons.md) — Offers cosmetic balloon companions through an inventory GUI and maintains their entity attachment while players move between normal gameplay states.
- [Actionbar](actionbar.md) — Continuously or periodically renders configured action-bar information for players.
- [Scoreboard](scoreboard.md) — Renders a configurable sidebar scoreboard for eligible players.
- [NightVision](nightvision.md) — Allows permitted players to toggle a stable Night Vision effect.
- [Nickname](nickname.md) — Stores and applies player nicknames across local display surfaces, with asynchronous persistence and a PlaceholderAPI fallback.
- [Nametags](nametags.md) — Renders multi-line, per-viewer nametags with robust entity attachment, delayed world readiness, refresh, and cleanup behavior.
- [NotifyLogin](notifylogin.md) — Notifies configured recipients when selected players log in.
- [VersionRecommender](versionrecommender.md) — Notifies players when their Minecraft client version differs from the server's recommended version.
- [PlayerLanguage](playerlanguage.md) — Loads and exposes a player's preferred language on the Paper side for localized feature messages.

## World and gameplay

- [Spawn](spawn.md) *(scaffold)* — Reserved feature scaffold for future spawn behavior. The current `Spawn` class contains no runtime implementation.
- [Portals](portals.md) — Provides administrator-defined portal regions that transport players to configured destinations.
- [ItemEdit](itemedit.md) — Adds safe item-editing utilities, including anvil-related editing behavior, for authorized staff or builders.
- [Parcour](parcour.md) — Provides parkour-oriented gameplay helpers and event handling used by HauntedMC course areas.
- [JoinItems](joinitems.md) — Gives players configured utility/menu items when they join and protects the intended slot behavior.
- [DeepHaste](deephaste.md) — Boosts the Haste effect supplied by a beacon when the affected player is below a configured Y level.
- [AutoLapis](autolapis.md) — Automatically supplies lapis behavior for enchanting workflows so players do not need to carry lapis manually.
- [AutoPickup](autopickup.md) — Persistently toggles direct block-drop collection, preserves exact ground overflow, and excludes indirect destruction.
- [Holograms](holograms.md) — Creates and manages configured holographic text displays in worlds.
- [EnderFrame](enderframe.md) — Allows controlled pickup and placement of End Portal Frames outside protected stronghold or claim contexts.
- [InstaSkull](instaskull.md) — Provides immediate player-head/skull handling for configured gameplay interactions.
- [BetterDoors](betterdoors.md) — Improves door interaction behavior, typically synchronizing paired doors and related blocks.
- [BetterCoral](bettercoral.md) — Prevents or customizes coral death behavior under configured conditions.
- [AntiRaidFarm](antiraidfarm.md) — Restricts raid-farm mechanics according to configured limits while providing an administrative control command.
- [SilkSpawners](silkspawners.md) — Allows controlled spawner pickup, placement, and type management with Silk Touch and permission checks.
- [RepairNPC](repairnpc.md) — Provides an NPC-driven repair interaction with configured costs, limits, and item eligibility.
- [CustomRecipes](customrecipes.md) — Registers configured custom crafting recipes and provides administrative recipe operations.
- [LimitSpawners](limitspawners.md) — Limits spawner density or count according to configured world/region rules.
- [SpawnerToggle](spawnertoggle.md) — Allows supported interactions to enable or disable spawner activity without destroying the spawner.
- [Teleportation](teleportation.md) — Provides safe random teleportation and coordinate teleportation with configurable validation and cooldowns.
- [LiquidTank](liquidtank.md) — Implements persistent block-based tanks for water, lava, milk, honey, experience, dragon breath, food/stew types, and empty state.
- [DurabilityAlert](durabilityalert.md) — Warns players when equipped or used items cross configured durability thresholds.
- [VillagerOptimizer](villageroptimizer.md) — Reduces villager processing cost by applying configurable optimization rules without changing intended gameplay more than necessary.
- [WorldEditVisualizer](worldeditvisualizer.md) — Visualizes WorldEdit selections for authorized builders with temporary particles or display markers.

## Moderation and administration

- [Vanish](vanish.md) — Provides staff invisibility with persisted state, visibility filtering, interaction protections, tab-list handling, and cross-feature availability through `VanishAPI`.
- [InvTools](invtools.md) — Provides safe online and offline inventory and ender-chest inspection, editing, clearing, migration, conflict detection, and audit logging.
- [ChatFilter](chatfilter.md) — Filters local chat against configured rules while preserving the server's broader chat pipeline.
- [ChatLayout](chatlayout.md) — Formats Paper chat messages with ranks, names, hover/click content, and signed-chat compatibility.
- [ChatTools](chattools.md) — Provides staff chat-management utilities such as clearing or controlling the local server chat.
- [Whitelist](whitelist.md) — Applies HauntedMC-specific whitelist admission behavior and messages on the Paper server.
- [Sanctions](sanctions.md) — Enforces network sanctions on the Paper server, especially mute/chat restrictions and shared moderation state.
- [StaffChat](staffchat.md) — Receives and displays network staff-chat messages on Paper and can intercept configured local staff-chat input.

## Operations and integration

- [Backup](backup.md) — Runs controlled server backup work from the Paper side, with feature-owned scheduling and shutdown cleanup.
- [Restart](restart.md) — Coordinates announced, cancellable server restarts, blocks unsafe late joins, evacuates connected players, and then performs the configured restart action.
- [Sanitize](sanitize.md) — Applies startup-time hardening and cleanup to server configuration files such as `bukkit.yml` and `spigot.yml`.
- [Broadcast](broadcast.md) — Allows authorized senders to broadcast a formatted message to the current Paper server.
- [Votifier](votifier.md) — Receives vote notifications on the Paper side and publishes or persists them for network reward processing.
- [VoteReward](votereward.md) — Consumes vote events or queued vote state and grants configured rewards, including delayed delivery on join.
- [ChatLog](chatlog.md) — Persists local chat messages and provides a report workflow for moderation review.
- [PlayerCount](playercount.md) — Receives validated, versioned PlayerCount snapshots from ProxyFeatures and exposes local APIs and PlaceholderAPI values without Redis access on placeholder reads.
- [CommandRelay](commandrelay.md) — Relays allowlisted commands between Paper servers/proxy components with authentication, replay protection, result handling, and database audit logging.
- [CommandLogger](commandlogger.md) — Records commands executed on the Paper server for audit and moderation purposes.
- [LagMonitor](lagmonitor.md) — Monitors server performance indicators and surfaces configured lag warnings or diagnostics.

## Coverage contract

The index must contain exactly one page for every feature package under the Paper feature root. A feature with no commands, placeholders, or external integration still receives a page that explicitly says so. Scaffolds are documented as scaffolds rather than described as working functionality.
