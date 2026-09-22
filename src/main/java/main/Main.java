package main;

import arc.struct.Seq;
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
import static main.Cache.teamRequests;
import static main.Resources.*;
import static mindustry.Vars.state;
import static mindustry.Vars.tilesize;

public class Main extends Plugin {

    private Boolean isPause = false;
    private MenuManager menuManager;

    @Override
    public void init() {
        // Initialization code here
        // Setting up server name and MOTD
        Administration.Config.serverName.set("[#5F9EA0]Foundation PvP");
        Administration.Config.motd.set("");
        // Starting the server
        Events.on(EventType.WorldLoadBeginEvent.class, event -> Log.info("world load"));
        // Initializing the cache of teamleaders
//        for (Team team : Team.all) {
//            Cache.teamsInfo.put(team, new TeamInfo());
//        }
        //removed because Team.all returns all 256 teams instead of active ones

        MapVote.init();
        menuManager = new MenuManager();
        menuManager.init();
        TeamDestroyTracker.init();
        LeaderBoardManager.init();
        Blocks.coreShard.unitCapModifier = 1;
        Blocks.coreFoundation.unitCapModifier = 2;
        Blocks.coreNucleus.unitCapModifier = 4;
        // setting up a timer to restart the game after maxTime seconds
        Time.runTask(0, new Runnable() {
            @Override
            public void run() {
                Time.runTask(60f, this);
                int hours = maxTime / 3600;
                int minutes = (maxTime % 3600) / 60;
                int seconds = (maxTime % 3600) % 60;
                Groups.player.each(player -> {
                    Team team = player.team();
                    if(team == Team.all[0]){
                        String timeDisplay = """
                [#5F9EA0]Foundation PvP
                [white]Time until end of round: %02d:%02d:%02d
                """.formatted(hours, minutes, seconds);
                        Call.setHudText(player.con, timeDisplay);
                    }
                    else {
                        int unitCap = Units.getCap(team);
                        String timeDisplay = """
                                [#5F9EA0]Foundation PvP
                                [white]Time until end of round: %02d:%02d:%02d
                                Max units: %d
                                """.formatted(hours, minutes, seconds, unitCap);
                        Call.setHudText(player.con, timeDisplay);
                    }
                });
                updateMOTD();
                if (maxTime > 0) {
                    maxTime--;
                } else if (maxTime == 0) {
                    maxTime = -1;
                    Restart.DoingRestart();
                }
            }
        });

        // When the game begins we destroy the center core and put all the players into
        // team derelict
        Events.on(EventType.GameOverEvent.class, event -> {
            Groups.player.each(p -> p.team(Team.all[0]));
        });
        // When a player joins the server
        Events.on(EventType.PlayerJoin.class, event -> {
            // creating a new player info object and putting it in the cache
            Player pl = event.player;
            if (Cache.playerTeams.containsKey(pl.uuid())) {
                Team savedTeam = Cache.playerTeams.get(pl.uuid());
                if (savedTeam != null && !savedTeam.cores().isEmpty()) {
                    pl.team(savedTeam);
                } else {
                    pl.team(Team.all[0]);
                }
            } else {
                pl.team(Team.all[0]); // default team for new players
            }

            menuManager.showGuide(pl);
            //unpausing server if it was paused
            if (isPause || state.isPaused()) {
                state.set(GameState.State.playing);
                isPause = false;
                Log.info("server is unpaused");
            }

        });
        // When a player leaves the server
        Events.on(EventType.PlayerLeave.class, event -> {
            Player pl = event.player;
            Cache.playerTeams.put(pl.uuid(), pl.team());
            teamRequests.remove(event.player.uuid());
            //pausing server
            if(Groups.player.size() == 1) {
                state.set(GameState.State.paused);
                isPause = true;
                Log.info("server is paused");
            }
            if (Cache.teamsInfo.get(pl.team()) != null) Cache.teamsInfo.get(pl.team()).removePlayer(pl);
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
            if (player.team() == Team.all[0] || player.team() == Team.all[1]) {
                if (tile.block().solid) return;
                float minDistance = 150f;
                //Remade this slow section with better method nearAnyCore :)
                // I guess commented code should be deleted next commit
                boolean close = nearAnyCore(tile, minDistance);
//                float minDistanceSquared = 4000f;
//                for (Team team : Cache.teamsInfo.keys()) {
//                    Seq<CoreBlock.CoreBuild> cores = team.cores();
//                    for (CoreBlock.CoreBuild core : cores) {
//                        if (core.dst2(tile.x, tile.y) < minDistanceSquared*8) {
//                            close = true;
//                            break;
//                        }
//                    }
//                }
//                for (var build : Groups.build) {
//                    if (build instanceof mindustry.world.blocks.storage.CoreBlock.CoreBuild) {
//                        if (tile.dst(build.tile) < minDistance * 8) {
//                            close = true;
//                            break;
//                        }
//                    }
//                }
                if (!close) {
                    // creating a new team
                    Team new_team = takeNewTeam();
                    if (!Cache.teamsInfo.containsKey(new_team)) {
                        Cache.teamsInfo.put(new_team, new TeamInfo(player.uuid(), new Seq<Player>().add(player)));
                    }
                    tile.setNet(Blocks.coreNucleus, new_team, 0);
                    Time.run(1f, () -> giveStartingResources(new_team));
                    player.team(new_team);
                    if (Cache.teamsInfo.containsKey(new_team)) {
                        Cache.teamsInfo.get(new_team).setLeaderUuid(player.uuid());
                        TeamInfo info = Cache.teamsInfo.get(new_team);
                        info.setLeaderUuid(player.uuid());
                    }
                } else {
                    player.sendMessage(Localisation.local(player, "tooCloseCoreWarning"));
                }
            }
        });
        // Replacing vault with core sharped
        Events.on(EventType.BlockBuildEndEvent.class, event -> {

            boolean close = false;
            float mindist = 150f;
            for (var build : Groups.build) {
                if (build instanceof mindustry.world.blocks.storage.CoreBlock.CoreBuild & build.team() != event.team) {
                    if (event.tile.dst(build.tile) < mindist * 5) {
                        close = true;
                        break;
                    }
                }
            }
            if (event.breaking || event.tile.block() != Blocks.vault)
                return;
            Team builderTeam = event.team;
            Tile tile = event.tile;
            if (close){
                return;
            }
            Time.run(1f, () -> tile.setNet(Blocks.coreShard, builderTeam, 0));
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
            state.rules.unitBuildSpeedMultiplier = 0.33f;
            state.rules.unitPayloadUpdate = true;
            state.rules.reactorExplosions = true;
            state.rules.logicUnitBuild = true;
            state.rules.loadout.clear();
            state.rules.loadout.add(new ItemStack(Items.copper, 600));
            state.rules.loadout.add(new ItemStack(Items.lead, 600));
            state.rules.loadout.add(new ItemStack(Items.metaglass, 100));
            state.rules.loadout.add(new ItemStack(Items.beryllium, 100));
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
        Administration.Config.desc.set(motd);
    }

    private void giveStartingResources(Team team){
        int bonus = getTeamResourceBonus();
        // Before it caused crashed, so i had to add a check
        if (team.core() != null)
        {
            team.core().items.add(Items.copper, bonus + 600);
            team.core().items.add(Items.lead, bonus);
            if (maxTime < 150) team.core().items.add(Items.graphite, Math.max(0, bonus - 200));
            if (maxTime < 150) team.core().items.add(Items.beryllium, Math.max(0, bonus - 200));
            if (maxTime < 300) team.core().items.add(Items.silicon, Math.max(0, bonus - 200));
            if (maxTime < 300) team.core().items.add(Items.metaglass, Math.max(0, bonus - 200));
            if (maxTime < 600) team.core().items.add(Items.titanium, Math.max(0, bonus - 500));
            if (maxTime < 900) team.core().items.add(Items.thorium, Math.max(0, bonus - 1000));
            if (maxTime < 900) team.core().items.add(Items.plastanium, Math.max(0, bonus - 1000));
        }
    }
    private int getTeamResourceBonus() {
        int elapsed = 10800 - maxTime;

        return Math.min(
                10000,
                elapsed / 600 * 1000
        );
    }
    public void registerClientCommands(CommandHandler handler) {
        // Register commands for client here
        handler.<Player>register("restart", "Restart the game/Перезапустить игру", (args, player) -> {
            if(Groups.player.size() == 1) Restart.DoingRestart();
            else Restart.AddVotes(player);

        });
        handler.<Player>register("destroy", "Destroy your building/Уничтожить строение", (args, player) -> {
            Tile tile = player.tileOn();
            Team playerTeam = player.team();
            if (tile.build != null && tile.build.team == player.team()) {
                tile.build.kill();
                if (playerTeam.cores().isEmpty()) {
                    TeamDestroyTracker.surrenderTeam(playerTeam);
                    if(player.unit() != null) player.unit().kill();
                    Groups.player.each(p -> p.team() == playerTeam, p -> {
                        p.team(Team.all[0]);
                        if (Cache.teamsInfo.containsKey(playerTeam)) {
                            Cache.teamsInfo.get(playerTeam).setLeaderUuid("");
                        }
                    });
                }
            }
        });



        handler.<Player>register("spectate", "Destroys all your buildings and sends you to spectators/Уничтожает все постройки команды и переводит в наблюдателей",
                (args, player) -> spectateCommand(player));
        handler.<Player>register("gg", "/spectate alias",
                (args, player) -> spectateCommand(player));
        handler.<Player>register("die", "/spectate alias",
                (args, player) -> spectateCommand(player));
        handler.<Player>register("s", "/spectate alias",
                (args, player) -> spectateCommand(player));

        handler.<Player>register("team", "Team management/Управление командой", (args, player) -> {
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
        });

        handler.<Player>register("join", "Join other command/Присоедениться к другой команде",  (args, player) -> {
            menuManager.showJoinMenu((Player) player);
        });

        handler.<Player>register("accept", "accept a player to foin your team/Принять игрока в команду",  (args, player) -> {
            menuManager.showAcceptMenu((Player) player);
        });

        handler.<Player>register("deny", "deny a player/Отклонить запрос на вступление в команду",   (args, player) -> {
            menuManager.showDenyMenu((Player) player);
        });

        handler.<Player>register("top", "Show a leaderboard/Показать лидерборд игроков",   (args, player) -> {
            menuManager.showLeaderBoard((Player) player);
        });

        handler.<Player>register("rank", "Check leaderboard position/Узнать место в топе",   (args, player) -> {
            LeaderBoardManager.Player stats = LeaderBoardManager.getPlayerStats(player.uuid());
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

        // Admin commands

        // Force restart the game
        handler.<Player>register("forcerestart", "Force restart the game", (args, player) -> {
            if (!player.admin()) {
                player.sendMessage("[red]Access denied.");
                return;
            }
            Restart.DoingRestart();
        });

        // Change team for yourself or another player
        handler.<Player>register("changeteam", "Changes specified player's team.(Yours if player is unspecified.)", (args, player) -> {
           if (!player.admin()) {
               player.sendMessage("[red]Access denied.");
               return;
           }
           switch(args.length) {
               case 1:
                   try {
                       Team team = Team.all[Integer.parseInt(args[0])];
                       player.team(team);
                   } catch (Exception e) {
                       player.sendMessage("[red]Invalid team ID.");
                       player.sendMessage("[yellow]Available teams:");
                       Cache.playerTeams.forEach(entry -> player.sendMessage("[yellow]" + entry.value.id));
                       return;
                   }
               case 2:
                   try {
                       Team team = Team.all[Integer.parseInt(args[0])];
                       String targetName = args[1].toLowerCase();
                       try {
                           Player target = Groups.player.find(p -> p.plainName().toLowerCase().equals(targetName));
                           if (target == null) throw new IllegalArgumentException("Player not found.");
                           target.team(team);
                       } catch (Exception e) {
                           player.sendMessage("[red]Invalid player name.");
                           return;
                       }
                   } catch (Exception e) {
                       player.sendMessage("[red]Invalid team ID.");
                       player.sendMessage("[yellow]Available teams:");
                       Cache.playerTeams.forEach(entry -> player.sendMessage("[yellow]" + entry.value.id));
                       return;
                   }
               default:
                   player.sendMessage("Invalid arguments, usage: /changeteam <teamID> [player]");
           }
        });


    }
    private static void spectateCommand(Player player) {
        Team playerTeam = player.team();
        if (playerTeam == Team.all[0]) return;
        boolean isLeader = false;
        TeamInfo info = Cache.teamsInfo.get(playerTeam);
        if (info != null && info.getLeaderUuid() != null) {
            if (info.getLeaderUuid().equals(player.uuid())) {
                isLeader = true;
            }
        }
        if (isLeader) {
            TeamDestroyTracker.surrenderTeam(playerTeam);
        } else {
            player.team(Team.all[0]);
            if (player.unit() != null) player.unit().kill();
        }
    }


    public void registerServerCommands(CommandHandler handler) {
        // Register commands for server here
        handler.register("restart", "Restarts the game", args -> Restart.DoingRestart());
    }

    // creating a new team
    public Team takeNewTeam() {
        for (Team team : Team.all) {
            if (!team.active() && team.id > 6) {
                return team;
            }
        }
        // returning a team
        return Team.all[0];
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

    private boolean isValidSpawn(Tile tile, float minCoreDistance) {
        int x = tile.x, y = tile.y;
        // 5x5 area check instead of one tile check.
        // sadly no more walls breaking.
        for (int i = x - 2; i < (x+2); i++) {
            for (int j = y - 2; j < (y+2); j++) {
                Tile curTile = world.tile(i,j);
                if (curTile == null || curTile.floor() == null
                        || !curTile.floor().solid || !curTile.block().isAir()) return false;
            }
        }
        return !nearAnyCore(tile, minCoreDistance);
    }
}