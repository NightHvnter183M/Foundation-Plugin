package main;

import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Strings;
import mindustry.content.Items;
import mindustry.core.GameState;
import mindustry.entities.Units;
import mindustry.game.Teams;
import mindustry.mod.Plugin;
import mindustry.net.Administration;
import mindustry.game.EventType;
import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Time;
import mindustry.content.Blocks;
import mindustry.content.Planets;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.type.ItemStack;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;

import java.util.HashSet;
import java.util.Set;

import static main.Cache.teamRequests;
import static main.Resources.*;
import static mindustry.Vars.*;

public class Main extends Plugin {

    private Boolean isPause = false;
    private MenuManager menuManager;
    private static final Team DEFAULT_TEAM = Team.derelict;
    /** Minimal distance from a new core to any other core, in tiles. */
    private static final float MIN_CORE_DISTANCE = 150f;
    private final Seq<HelpEntry> helpEntries = new Seq<>();
    private String lastMotd;

    @Override
    public void init() {
        // Initialization code here
        // Setting up server name and MOTD
        Administration.Config.serverName.set("[#5F9EA0]Foundation PvP");
        Administration.Config.motd.set("");

        MapVote.init();
        PayloadVote.init();
        menuManager = new MenuManager();
        menuManager.init();
        TeamDestroyTracker.init();
        PayloadWeapons.init();
        LeaderBoardManager.init();
        Blocks.coreShard.unitCapModifier = 1;
        Blocks.coreFoundation.unitCapModifier = 2;
        Blocks.coreNucleus.unitCapModifier = 4;

        // A joining player gets a team here, before the world is sent to them:
        // their own team if it is still alive, otherwise spectators.
        // (The vanilla PvP assigner would first put them into the team with the fewest players.)
        netServer.assigner = (player, players) -> {
            Team saved = Cache.playerTeams.get(player.uuid());
            return saved != null && saved.active() ? saved : DEFAULT_TEAM;
        };

        // All of this belongs to one round: team ids are reused on the next map
        Events.on(EventType.ResetEvent.class, event -> {
            Cache.teamsInfo.clear();
            Cache.teamRequests.clear();
            Cache.playerTeams.clear();
            menuManager.clear();
            MapVote.reset();
            Restart.reset();
        });

        // setting up a timer to restart the game after maxTime seconds
        Time.runTask(0, new Runnable() {
            @Override
            public void run() {
                Time.runTask(60f, this);
                int hours = maxTime / 3600;
                int minutes = (maxTime % 3600) / 60;
                int seconds = (maxTime % 3600) % 60;
                String timeDisplay = """
                        [#5F9EA0]Foundation PvP
                        [white]Time until end of round: %02d:%02d:%02d
                        """.formatted(hours, minutes, seconds);
                Groups.player.each(player -> {
                    Team team = player.team();
                    if (team == DEFAULT_TEAM) {
                        Call.setHudText(player.con, timeDisplay);
                    } else {
                        Call.setHudText(player.con, timeDisplay + "Max units: " + Units.getCap(team) + "\n");
                    }
                });
                PointsManager.updateRatings();
                updateMOTD();
                if (maxTime > 0) {
                    maxTime--;
                } else if (maxTime == 0) {
                    maxTime = -1;
                    Restart.DoingRestart();
                }
            }
        });

        // When the game ends, we destroy the center core and put all the players into
        // team derelict
        Events.on(EventType.GameOverEvent.class, event -> {
            Groups.player.each(p -> p.team(Team.all[0]));
        });

        // When a player joins the server (their team was already chosen by netServer.assigner)
        Events.on(EventType.PlayerJoin.class, event -> {
            Player pl = event.player;
            menuManager.showGuide(pl);
            //unpausing server if it was paused
            if (isPause || state.isPaused()) {
                state.set(GameState.State.playing);
                isPause = false; //why?
                Log.info("Server's been unpaused");
            }
        });
        // When a player leaves the server
        Events.on(EventType.PlayerLeave.class, event -> {
            Player pl = event.player;
            Team team = pl.team();
            // Remember the team only while it is alive: later its id can be given to a new team
            if (TeamInfo.isPlayerTeam(team) && team.active()) {
                Cache.playerTeams.put(pl.uuid(), team);
            } else {
                Cache.playerTeams.remove(pl.uuid());
            }
            teamRequests.remove(pl.uuid());
            menuManager.forget(pl);
            // The leader left: an online teammate takes over (if nobody is online, the leader keeps the role)
            TeamInfo info = Cache.teamsInfo.get(team);
            if (info != null && info.isLeader(pl)) {
                menuManager.passLeadership(team, pl);
            }
            //pausing server
            if(Groups.player.size() == 1) {
                state.set(GameState.State.paused);
                isPause = true; //whyy?
                Log.info("Server is paused");
            }
        });

        Events.on(EventType.UnitSpawnEvent.class, event -> {
            if (event.unit != null && event.unit.team == Team.crux) {
                event.unit.controller(new DynamicCruxAi.dynamicCruxAI());
            }
        });

        // When a player clicks on a tile to create a core and command
        Events.on(EventType.TapEvent.class, event -> {
            Player player = event.player;
            Tile tile = event.tile;
            if (tile == null || player.team() != DEFAULT_TEAM) return;
            if (!isValidSpawn(tile, MIN_CORE_DISTANCE)) {
                player.sendMessage(Localisation.local(player, "tooCloseCoreWarning"));
                return;
            }
            if (nearWaveSpawn(tile)) {
                player.sendMessage(Localisation.local(player, "tooCloseSpawnWarning"));
                return;
            }

            // creating a new team
            Team newTeam = takeNewTeam();
            if (newTeam == null) {
                player.sendMessage(Localisation.local(player, "noFreeTeams"));
                return;
            }
            // Always a fresh TeamInfo: this id may have belonged to an eliminated team
            Cache.teamsInfo.put(newTeam, new TeamInfo(player.uuid()));
            tile.setNet(Blocks.coreNucleus, newTeam, 0);
            Time.run(1f, () -> giveStartingResources(newTeam));
            player.team(newTeam);
        });
        // Replacing vault with core sharped
        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if (event.breaking || event.tile == null || event.tile.block() == null) return;
//            if (event.tile.block() == Blocks.coreNucleus || event.tile.block() == Blocks.coreFoundation) {
//                event.team.data().unitCap = Math.min(event.team.data().unitCap, 500);
//                return;
//            }
            if (event.tile.block() != Blocks.vault) return;

            Team builderTeam = event.team;
            Tile tile = event.tile;

            if (nearEnemyCore(tile, MIN_CORE_DISTANCE, builderTeam)) return;

            Time.run(1f, () -> {
                // During this tick the vault may have been removed or the team eliminated
                if (tile.block() != Blocks.vault || tile.team() != builderTeam || !builderTeam.active()) return;
                tile.setNet(Blocks.coreShard, builderTeam, 0);
            });
        });
        //Killing team is now in teamDestroyTracker.java
        Events.on(EventType.PlayEvent.class, event -> {
            maxTime = 10800;
            state.rules.pvp = true;
            state.rules.pvpAutoPause = false;
            state.rules.canGameOver = false;
            state.rules.waveTeam = Team.crux;
            state.rules.randomWaveAI = true;
            state.rules.unitCap = 8;
            state.rules.planet = Planets.sun;
            state.rules.defaultTeam = Team.all[0];
            state.rules.unitCostMultiplier = 0.75f;
            state.rules.unitDamageMultiplier = 1.414f;
            state.rules.unitBuildSpeedMultiplier = 0.334f;
            // payload update: the default until this round's vote ends (PayloadVote)
            PayloadVote.roundStarted();
            state.rules.reactorExplosions = true;
            state.rules.logicUnitBuild = true;
            // Some maps are saved with polygon core protection: it splits the whole map between cores
            // and forbids building on the "enemy" side of the border, which doesn't fit free-for-all PvP
            state.rules.polygonCoreProtection = false;
            // starting items are given to every new core in giveStartingResources()
            Call.setRules(state.rules);
            Time.run(2f, () -> {
                Groups.build.each(b -> b instanceof CoreBlock.CoreBuild, b -> b.tile.removeNet());
                Groups.player.each(p -> p.team(Team.all[0]));
            });
            if(isPause){
                if(Groups.player.isEmpty()){
                    state.set(GameState.State.paused);
                    Log.info("server is paused");
                }
                else{
                    state.set(GameState.State.playing);
                    isPause = false;
                    Log.info("server is unpaused");
                }
            }
        });
    }

    protected void updateMOTD() {
        int hours = maxTime / 3600;
        int minutes = (maxTime % 3600) / 60;
        String timeString = String.format("%02d:%02d", hours, minutes);
        String motd = custommotd + timeString;
        // Only hours:minutes are shown, so the text actually changes once a minute
        if (!motd.equals(lastMotd)) {
            Administration.Config.desc.set(motd);
            lastMotd = motd;
        }
    }

    private void giveStartingResources(Team team){// Null-check is necessary.
        if (team.core() == null) return;
        // Built here, not in a static field: the plugin class is loaded before game content exists,
        // so Items.copper etc. would still be null in a static initializer
        ItemStack[] startItems = ItemStack.with(
                Items.copper, 600,
                Items.lead, 600,
                Items.metaglass, 100,
                Items.beryllium, 100
        );
        for (ItemStack stack : startItems) {
            team.core().items.set(stack.item, stack.amount);
        }
        // It wasn't working anyways iirc lol
//        int bonus = getTeamResourceBonus();
//        team.core().items.add(Items.copper, bonus + 600);
//        team.core().items.add(Items.lead, bonus);
//        if (maxTime < 150) team.core().items.add(Items.graphite, Math.max(0, bonus - 200));
//        if (maxTime < 150) team.core().items.add(Items.beryllium, Math.max(0, bonus - 200));
//        if (maxTime < 300) team.core().items.add(Items.silicon, Math.max(0, bonus - 200));
//        if (maxTime < 300) team.core().items.add(Items.metaglass, Math.max(0, bonus - 200));
//        if (maxTime < 600) team.core().items.add(Items.titanium, Math.max(0, bonus - 500));
//        if (maxTime < 900) team.core().items.add(Items.thorium, Math.max(0, bonus - 1000));
//        if (maxTime < 900) team.core().items.add(Items.plastanium, Math.max(0, bonus - 1000));
    }
    private int getTeamResourceBonus() {
        int elapsed = 10800 - maxTime;

        return Math.min(
                10000,
                elapsed / 600 * 1000
        );
    }


    public void registerClientCommands(CommandHandler handler) {
        CommandHandler.CommandRunner<Player> restartCom =(args, player) -> {
            if(Groups.player.size() == 1) Restart.DoingRestart();
            else Restart.AddVotes(player);
        };

        CommandHandler.CommandRunner<Player> destroyCom = (args, player) -> {
            Tile tile = player.tileOn();
            // Only a building of your own team (spectators can't destroy anything).
            // If it was the team's last nucleus/foundation, TeamDestroyTracker eliminates the team as usual.
            if (tile == null || tile.build == null || !TeamInfo.isPlayerTeam(player.team()) || tile.build.team != player.team()) return;
            tile.build.kill();
        };

        CommandHandler.CommandRunner<Player> spectateCom = (args, player) -> spectateCommand(player);

        CommandHandler.CommandRunner<Player> teamCom = (args, player) -> {
            String[][] buttons = {
                    { Localisation.local(player, "teamMenuJoinButton") },
                    { Localisation.local(player, "teamMenuAcceptButton") },
                    { Localisation.local(player, "teamMenuKickButton") },
                    { Localisation.local(player, "teamMenuDenyButton") },
                    { Localisation.local(player, "teamMenuLeadButton") },
                    { Localisation.local(player, "menuCloseButton") },
            };
            // Open the team management menu for the player
            Call.menu(player.con,  Cache.teamMenuId, Localisation.local(player, "teamMenuTitle"), Localisation.local(player, "teamMenuMessage"), buttons);
        };

        CommandHandler.CommandRunner<Player> joinCom = (args, player) -> menuManager.showJoinMenu(player);

        CommandHandler.CommandRunner<Player> acceptCom = (args, player) -> menuManager.showAcceptMenu(player);

        CommandHandler.CommandRunner<Player> denyCom = (args, player) -> menuManager.showDenyMenu(player);

        CommandHandler.CommandRunner<Player> topCom = (args, player) -> menuManager.showLeaderBoard(player);

        CommandHandler.CommandRunner<Player> lobbyCom = (args, player) -> Call.connect(player.con, "c15.play2go.cloud", 20042);

        CommandHandler.CommandRunner<Player> rankCom = (args, player) -> LeaderBoardManager.getPlayerStats(player.uuid(), stats -> {
            if (!player.isAdded()) return; // left while the query was running
            if (stats == null) {
                player.sendMessage(Localisation.local(player, "leaderboardPositionNotFound"));
                return;
            }
            player.sendMessage(
                    Localisation.local(player, "StatisticsMessage") + "\n" +
                            Localisation.local(player, "StatisticsPosition") + " " + stats.position + "\n" +
                            Localisation.local(player, "StatisticsPoints") + " " + stats.points
            );
        });

        CommandHandler.CommandRunner<Player> payloadCom = (args, player) -> PayloadVote.command(player);

        CommandHandler.CommandRunner<Player> forceCom = (args, player) -> {
            if (!player.admin()) {
                player.sendMessage("[red]Access denied.");
                return;
            }
            Restart.DoingRestart();
        };

        CommandHandler.CommandRunner<Player> cTeamCom = (args, player) -> {
            if (!player.admin()) {
                player.sendMessage("[red]Access denied.");
                return;
            }
            int teamId = Strings.canParseInt(args[0]) ? Strings.parseInt(args[0]) : -1;
            if (teamId < 0 || teamId >= Team.all.length) {
                player.sendMessage("[red]Invalid team ID.");
                sendAvailableTeams(player);
                return;
            }
            Player target = player;
            if (args.length > 1) {
                String targetName = args[1].toLowerCase();
                target = Groups.player.find(p -> p.plainName().toLowerCase().equals(targetName));
                if (target == null) {
                    player.sendMessage("[red]Invalid player name.");
                    return;
                }
            }
            target.team(Team.all[teamId]);
        };

        ///And finally registering commands with previous logic(kill me, please)
        // No "t" and "a" aliases: those are the vanilla team and admin chat,
        // and the client's team chat mode sends its messages as "/t ..."
        helpEntries.clear();
        addCommand(handler, "Back to lobby/Перейти в лобби", lobbyCom, "lobby", "back");
        addCommand(handler,"Restart the game/Перезапустить игру", restartCom, "restart", "r");
        addCommand(handler, "Destroy your building/Уничтожить строение", destroyCom, "destroy", "dr");
        addCommand(handler, "Destroys all your buildings and sends you to spectators/Уничтожает все постройки команды и переводит в наблюдателей", spectateCom, "spectate", "s", "gg", "die");
        addCommand(handler, "Team management/Управление командой", teamCom, "team", "tm");
        addCommand(handler, "Join other team/Присоедениться к другой команде", joinCom, "join", "j");
        addCommand(handler, "Accept join request/Принять игрока в команду", acceptCom, "accept", "ac");
        addCommand(handler, "Deny join request/Отклонить запрос на вступление в команду", denyCom, "deny", "d");
        addCommand(handler, "Show a leaderboard/Показать лидерборд игроков", topCom, "top", "leaderboard", "lb");
        addCommand(handler, "Check leaderboard position/Узнать место в топе", rankCom, "rank", "place", "pl");
        addCommand(handler, "Payload update vote/Голосование за работу груза", payloadCom, "payload");
        addCommand(handler, "[ADMIN ONLY]Force restart the game", forceCom, "forcerestart", "frestart", "force");
        addCommand(handler,"<teamID> [player]" , "[ADMIN ONLY] Changes specified player's team.(Yours if player is unspecified.)", cTeamCom, "cteam", "changeteam");
        handler.register("help", "[page]", "Commands", this::sendHelp);




    }
    private static void spectateCommand(Player player) {
        Team playerTeam = player.team();
        if (playerTeam == DEFAULT_TEAM) return;
        TeamInfo info = Cache.teamsInfo.get(playerTeam);
        if (info != null && info.isLeader(player)) {
            TeamDestroyTracker.surrenderTeam(playerTeam);
        } else {
            player.team(DEFAULT_TEAM);
            if (player.unit() != null) player.unit().kill();
        }
    }

    private static void sendAvailableTeams(Player player) {
        player.sendMessage("[yellow]Available teams:");
        Set<Integer> teamIds = new HashSet<>();
        Groups.player.forEach(p -> teamIds.add(p.team().id));
        Cache.playerTeams.forEach(entry -> teamIds.add(entry.value.id));
        teamIds.forEach(id -> player.sendMessage("[yellow]" + id));
    }


    public void registerServerCommands(CommandHandler handler) {
        // Register commands for server here
        handler.register("restart", "Restarts the game", args -> Restart.DoingRestart());
    }

    // creating a new team; null if every player team id is taken
    public Team takeNewTeam() {
        for (Team team : Team.all) {
            if (TeamInfo.isPlayerTeam(team) && !team.active()) {
                return team;
            }
        }
        return null;
    }

    //Now it checks only cores, no all the buildings over the map
    private boolean nearAnyCore(Tile tile, float distance) {
        float x = tile.worldx(), y = tile.worldy();
        float radius = distance * tilesize;
        for (Teams.TeamData data : state.teams.active) {
            for (CoreBlock.CoreBuild core : data.cores) {
                if (core.within(x, y, radius)) return true;
            }
        }
        return false;
    }

    private boolean nearEnemyCore(Tile tile, float distance, Team team) {
        float x = tile.worldx(), y = tile.worldy();
        float radius = distance * tilesize;
        for (Teams.TeamData data : state.teams.active) {
            if (data.team == team) continue;
            for (CoreBlock.CoreBuild core : data.cores) {
                if (core.within(x, y, radius)) return true;
            }
        }
        return false;
    }

    private boolean isValidSpawn(Tile tile, float minCoreDistance) {
        int x = tile.x, y = tile.y;
        // 5x5 area check instead of one tile check.
        // sadly no more walls breaking.
        for (int i = x - 2; i <= (x+2); i++) {
            for (int j = y - 2; j <= (y+2); j++) {
                Tile curTile = world.tile(i,j);
                if (curTile == null || curTile.floor() == null
                        || curTile.floor().solid || curTile.floor().isDeep() || !curTile.block().isAir()) return false;
            }
        }
        return !nearAnyCore(tile, minCoreDistance);
    }

    // Every ground wave destroys all buildings in the drop zone around its spawn point
    private boolean nearWaveSpawn(Tile tile) {
        if (!state.rules.waves) return false;
        float radius = state.rules.dropZoneRadius + Blocks.coreNucleus.size * tilesize / 2f;
        for (Tile spawn : spawner.getSpawns()) {
            if (spawn.within(tile.worldx(), tile.worldy(), radius)) return true;
        }
        return false;
    }

    private void addCommand(CommandHandler handler, String description, CommandHandler.CommandRunner<Player> logic, String... names){
        helpEntries.add(new HelpEntry(description, names));
        for (String name : names) {
            handler.register(name, description, logic);
        }
    }
    private void addCommand(CommandHandler handler, String params, String description, CommandHandler.CommandRunner<Player> logic, String... names){
        helpEntries.add(new HelpEntry(description, names));
        for (String name : names) {
            handler.register(name, params, description, logic);
        }
    }

    public static class HelpEntry{
        final String[] names;
        final String description;
        HelpEntry(String description, String... names){
            this.description = description;
            this.names = names;
        }
    }

    private void sendHelp(String[] args, Player player){
        int commaPerPage = 15;
        int pages = Math.max(1, Mathf.ceil(helpEntries.size / (float) commaPerPage));
        int page = 1;

        if (args.length > 0) {
            if (!Strings.canParseInt(args[0])) {
                player.sendMessage("[scarlet]Page is number.");
                return;
            }
            page = Strings.parseInt(args[0]);
        }

        if (page < 1 || page > pages) {
            player.sendMessage("[scarlet]Wrong page.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[orange]-- Page ").append(page).append('/').append(pages).append(" --\n");

        int start = (page - 1) * commaPerPage;
        int end = Math.min(start + commaPerPage, helpEntries.size);

        for (int i = start; i < end; i++) {
            HelpEntry entry = helpEntries.get(i);

            ///"/play, /p": commands are orange, the comma is white
            sb.append("[orange]");
            for (int n = 0; n < entry.names.length; n++) {
                if (n > 0) sb.append("[white], [orange]");
                sb.append('/').append(entry.names[n]);
            }

            ///" - description" is white
            sb.append("[white] - ").append(entry.description).append('\n');
        }

        player.sendMessage(sb.toString());
    }
}
