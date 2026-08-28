package main;

import mindustry.content.Items;
import mindustry.core.GameState;
import mindustry.entities.Units;
import mindustry.mod.Plugin;
import mindustry.net.Administration;
import mindustry.game.EventType;
import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Planets;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import static main.Cache.teamRequests;
import static main.Resources.*;

public class Main extends Plugin {

    private Boolean isPause = false;

    @Override
    public void init() {
        // Initialization code here
        // Setting up server name and MOTD
        Administration.Config.serverName.set("[#5F9EA0]Foundation PvP");
        Administration.Config.motd.set("");
        // Starting the server
        Events.on(EventType.WorldLoadBeginEvent.class, event -> Log.info("world load"));
        // Initializing the cache of teamleaders
        for (Team team : Team.all) {
            Cache.teams_Info.put(team, new TeamInfo());
        }
        MapVote.init();
        new MenuManager().init();
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
            Groups.build.each(b -> {
                if (b instanceof mindustry.world.blocks.storage.CoreBlock.CoreBuild) {
                    b.kill();
                }
            });
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
            MenuManager manager = new MenuManager();
            manager.showGuide(pl);
            //unpausing server if it was paused
            if (isPause || Vars.state.isPaused()) {
                Vars.state.set(GameState.State.playing);
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
                Vars.state.set(GameState.State.paused);
                isPause = true;
                Log.info("server is paused");
            }
        });

        Events.on(EventType.UnitSpawnEvent.class, event -> {
            if (event.unit != null && event.unit.team == Team.crux) {
                event.unit.controller(new DynamicCruxAi.dynamicCruxAI());
            }
        });

        // When a player clicks on a tile to create a core and command
        Events.on(EventType.TapEvent.class, event -> {
            Player pla = event.player;
            Tile tile = event.tile;
            if (pla.team() == Team.all[0]|| pla.team() == Team.all[1]) {
                if (tile.solid()) {
                    return;
                }
                boolean close = false;
                float mindist = 200f;
                for (var build : Groups.build) {
                    if (build instanceof mindustry.world.blocks.storage.CoreBlock.CoreBuild) {
                        if (tile.dst(build.tile) < mindist * 5) {
                            close = true;
                            break;
                        }
                    }
                }
                if (!close) {
                    // creating a new team
                    Team new_team = takeNewTeam();
                    if (!Cache.teams_Info.containsKey(new_team)) {
                        Cache.teams_Info.put(new_team, new TeamInfo());
                    }
                    tile.setNet(Blocks.coreNucleus, new_team, 0);
                    Time.run(1f, () -> giveStartingResources(new_team));
                    pla.team(new_team);
                    if (Cache.teams_Info.containsKey(new_team)) {
                        Cache.teams_Info.get(new_team).leaderUuid = pla.uuid();
                        TeamInfo info = Cache.teams_Info.get(new_team);
                        info.leaderUuid = pla.uuid();
                    }
                } else {
                    pla.sendMessage(Localisation.local(pla, "tooCloseCoreWarning"));
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
            Vars.state.rules.pvp = true;
            Vars.state.rules.pvpAutoPause = false;
            Vars.state.rules.canGameOver = false;
            Vars.state.rules.waveTeam = Team.crux;
            Vars.state.rules.randomWaveAI = true;
            Vars.state.rules.unitCap = 8;
            Vars.state.rules.planet = Planets.sun;
            Vars.state.rules.defaultTeam = Team.all[0];
            Vars.state.rules.unitCostMultiplier = 0.75f;
            Vars.state.rules.unitDamageMultiplier = 1.414f;
            Vars.state.rules.unitBuildSpeedMultiplier = 0.33f;
            Vars.state.rules.unitPayloadUpdate = true;
            Vars.state.rules.reactorExplosions = true;
            Vars.state.rules.logicUnitBuild = true;
            Call.setRules(Vars.state.rules);
            Time.run(2f, () -> {
                Groups.build.each(b -> b instanceof CoreBlock.CoreBuild, b -> b.tile.removeNet());
                Groups.player.each(p -> p.team(Team.all[0]));
            });
            if(isPause){
                if(Groups.player.size() == 0){
                    Vars.state.set(GameState.State.paused);
                    Log.info("server is paused");
                }
                else{
                    Vars.state.set(GameState.State.playing);
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
        team.core().items.add(Items.copper, bonus + 600);
        team.core().items.add(Items.lead, bonus);
        if (maxTime < 150) team.core().items.add(Items.graphite,  Math.max(0, bonus - 200));
        if (maxTime < 150) team.core().items.add(Items.beryllium,  Math.max(0, bonus - 200));
        if (maxTime < 300) team.core().items.add(Items.silicon, Math.max(0, bonus - 200));
        if (maxTime < 300) team.core().items.add(Items.metaglass, Math.max(0, bonus - 200));
        if (maxTime < 600) team.core().items.add(Items.titanium, Math.max(0, bonus - 500));
        if (maxTime < 900) team.core().items.add(Items.thorium, Math.max(0, bonus - 1000));
        if (maxTime < 900) team.core().items.add(Items.plastanium, Math.max(0, bonus - 1000));
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
                        if (Cache.teams_Info.containsKey(playerTeam)) {
                            Cache.teams_Info.get(playerTeam).leaderUuid = "";
                        }
                    });
                }
            }
        });

        handler.<Player>register("spectate", "Destroys all your buildings and sends you to speactators/Уничтожает все постройки команды и переводит в наблюдателей",
                (args, player) -> {
                    Team playerTeam = player.team();
                    if (playerTeam == Team.all[0]) return;
                    boolean isLeader = false;
                    TeamInfo info = Cache.teams_Info.get(playerTeam);
                    if (info != null && info.leaderUuid != null) {
                        if (info.leaderUuid.equals(player.uuid())) {
                            isLeader = true;
                        }
                    }
                    if (isLeader) {
                        TeamDestroyTracker.surrenderTeam(playerTeam);
                    } else {
                        player.team(Team.all[0]);
                        if (player.unit() != null) player.unit().kill();
                    }
                });

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
            MenuManager menuManager = new MenuManager();
            menuManager.showJoinMenu((Player) player);
        });

        handler.<Player>register("accept", "accept a player to foin your team/Принять игрока в команду",  (args, player) -> {
            MenuManager menuManager = new MenuManager();
            menuManager.showAcceptMenu((Player) player);
        });

        handler.<Player>register("deny", "deny a player/Отклонить запрос на вступление в команду",   (args, player) -> {
            MenuManager menuManager = new MenuManager();
            menuManager.showDenyMenu((Player) player);
        });

        handler.<Player>register("top", "Show a leaderboard/Показать лидерборд игроков",   (args, player) -> {
            MenuManager menuManager = new MenuManager();
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
                            Localisation.local(player, "StatisticsPoints") + "" + stats.points
            );
        });
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
}