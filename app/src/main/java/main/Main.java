package main;

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

public class Main extends Plugin {

    public Integer maxTime = 10800;

    @Override
    public void init() {
        // Initialization code here
        // Setting up server name and MOTD
        Administration.Config.serverName.set("[#5F9EA0]Foundation PvP");
        Administration.Config.motd.set("Our discord: [#00FF00]discord.gg/Hamn9EhyQj");
        // Starting the server
        Events.on(EventType.WorldLoadBeginEvent.class, event -> Log.info("world load"));
        // Initializing the cache of teamleaders
        for (Team team : Team.all) {
            Cache.teams_Info.put(team, new TeamInfo());
        }

        // setting up a timer to restart the game after maxTime seconds
        Time.runTask(0, new Runnable() {
            @Override
            public void run() {
                Time.runTask(60f, this);
                int minutes = maxTime / 60;
                int seconds = maxTime % 60;
                String timeDisplay = String.format("%02d:%02d", minutes, seconds);
                updateMOTD();
                if (maxTime > 0) {
                    Call.setHudText("Until round end: " + timeDisplay);
                } else {
                    Call.setHudText("The round is ending...");
                }

                if (maxTime > 0) {
                    maxTime--;
                } else {
                    if (maxTime == 0) {
                        maxTime = -1;
                        restart();
                    }
                }
            }
        });

        // When the game begins we destroy the center core and put all the players into
        // team derelict
        Events.on(EventType.GameOverEvent.class, event -> {
            Groups.player.each(p -> p.team(Team.derelict));
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
                    pl.team(Team.derelict);
                }
            } else {
                pl.team(Team.derelict); // default team for new players
            }
        });
        // When a player leaves the server
        Events.on(EventType.PlayerLeave.class, event -> {

            Player pl = event.player;
            Cache.playerTeams.put(pl.uuid(), pl.team());
            if (!Cache.players_Info.containsKey(pl.uuid())) {
                Cache.players_Info.put(pl.uuid(), new PlayerInfo());
            }

            teamRequests.remove(event.player.uuid());
        });
        // When a player clicks on a tile to create a core and command
        Events.on(EventType.TapEvent.class, event -> {
            Player pla = event.player;
            Tile tile = event.tile;
            if (pla.team() == Team.all[0] || pla.team() == Team.all[1]) {
                if (tile.solid()) {
                    return;
                }
                boolean close = false;
                float mindist = 100f;
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
                    pla.team(new_team);
                    if (Cache.teams_Info.containsKey(new_team)) {
                        Cache.teams_Info.get(new_team).leaderUuid = pla.uuid();
                        TeamInfo info = Cache.teams_Info.get(new_team);
                        info.leaderUuid = pla.uuid();
                    }
                } else {
                    pla.sendMessage("[#8B0000]Too close to another core");
                }
            }
        });
        // Replacing vault with core sharped
        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if (event.breaking || event.tile.block() != Blocks.vault)
                return;
            Team builderTeam = event.team;
            Tile tile = event.tile;
            Time.run(1f, () -> tile.setNet(Blocks.coreShard, builderTeam, 0));
        });

        Events.on(EventType.BlockDestroyEvent.class, event -> {
            Team team = event.tile.team();
            if (event.tile.block() instanceof CoreBlock && team != Team.derelict) {
                Time.run(10f, () -> {
                    if (team.cores().isEmpty()) {
                        kill_team(team);
                        Groups.player.each(p -> p.team() == team, p -> {
                            p.team(Team.derelict);
                            if (Cache.teams_Info.containsKey(team)) {
                                Cache.teams_Info.get(team).leaderUuid = "";
                            }
                        });
                    }
                });
            }

        });
        Events.on(EventType.PlayEvent.class, event -> Time.run(1f, () -> Groups.build.each(b -> b instanceof CoreBlock.CoreBuild, b -> {
            b.tile.removeNet();
            Groups.player.each(p -> p.team(Team.derelict));
            maxTime = 10800;
            Vars.state.rules.pvp = true;
            Vars.state.rules.pvpAutoPause = false;
            Vars.state.rules.canGameOver = false;
            Vars.state.rules.waves = false;
            Vars.state.rules.planet = Planets.sun;
            Vars.state.rules.defaultTeam = Team.derelict;
            Vars.state.rules.buildCostMultiplier = 0.75f;
            Vars.state.rules.unitDamageMultiplier = 1.414f;
            Vars.state.rules.unitBuildSpeedMultiplier = 0.33f;
            Vars.state.rules.unitHealthMultiplier = 1.414f;
            Log.info("New game started");
            Call.setRules(Vars.state.rules);
            Time.run(60f, () -> Groups.player.each(p -> p.team(Team.all[0])));
        })));
    }

    protected void updateMOTD() {
        int hours = maxTime / 3600;
        int minutes = (maxTime % 3600) / 60;
        String timeString = String.format("%02d:%02d", hours, minutes);
        String motd = "Welcome to Foundation PvP!" + "Time until round end: " + timeString;
        Administration.Config.desc.set(motd);
    }

    public void registerClientCommands(CommandHandler handler) {
        // Register commands for client here
        handler.<Player>register("restart", "Restarts the game", (args, player) -> {
            if (Cache.restartVotes.contains(player.uuid())) {
                player.sendMessage("[#F08080]You have already voted to restart.");
                return;
            }
            Cache.restartVotes.add(player.uuid());
            int votesNeeded = (int) ((Groups.player.size() * 0.6) + 1); // 60% of players need to vote
            int currentVotes = Cache.restartVotes.size;
            Call.sendMessage(
                    player.name + " has voted to restart the game. (" + currentVotes + "/" + votesNeeded + " votes)");
            if (currentVotes >= votesNeeded) {
                Call.sendMessage("Restarting the game...");
                Time.run(60f, () -> {
                    restart();
                    Cache.restartVotes.clear();
                });
            }

        });
        handler.<Player>register("destroy", "Destroys your building", (args, player) -> {
            Tile tile = player.tileOn();
            Team playerTeam = player.team();
            if (tile.build != null && tile.build.team == player.team()) {
                tile.build.kill();
                if (playerTeam.cores().isEmpty()) {
                    kill_team(playerTeam);
                    player.unit().kill();
                    Groups.player.each(p -> p.team() == playerTeam, p -> {
                        p.team(Team.derelict);
                        if (Cache.teams_Info.containsKey(playerTeam)) {
                            Cache.teams_Info.get(playerTeam).leaderUuid = "";
                        }
                    });
                }
            }
        });

        handler.<Player>register("spectate", "Destroys all your buildings and sends you to speactators",
                (args, player) -> {
                    Team playerTeam = player.team();
                    boolean isLeader = false;
                    // Checking if the player is the leader of the team
                    if (player.team() != Team.all[0]) {
                        TeamInfo info = Cache.teams_Info.get(playerTeam);
                        if (info != null && info.leaderUuid != null) {
                            if (info.leaderUuid.equals(player.uuid())) {
                                isLeader = true;
                            }
                        }
                        if (isLeader) {
                            kill_team(player.team());
                            player.team(Team.derelict);
                            player.unit().kill();
                            Groups.player.each(p -> p.team() == playerTeam, p -> {
                                p.team(Team.derelict);
                                p.unit().kill();
                            });
                            if (Cache.teams_Info.containsKey(playerTeam)) {
                                Cache.teams_Info.get(playerTeam).leaderUuid = "";
                            }
                        } else {
                            player.team(Team.derelict);
                            player.unit().kill();
                        }
                    }
                });

        handler.<Player>register("team", "Team managment", (args, player) -> {
            String[][] buttons = {
                    { "[green]Join" },
                    { "[blue]Accept" },
                    { "[orange]Kick" },
                    { "[red]Deny" }
            };
            // Open the team management menu for the player
            Call.menu(player.con, MenuManager.teamMenuId, "[accent]Team Menu", "Choose an action:", buttons);
        });
    }

    public void registerServerCommands(CommandHandler handler) {
        // Register commands for server here
        handler.register("restart", "Restarts the game", args -> restart());
    }

    // creating a new team
    public Team takeNewTeam() {
        for (Team team : Team.all) {
            if (!team.active() && team.id > 5) {
                return team;
            }
        }
        // returning a team
        return Team.all[0];
    }

    // Restarting the game
    public void restart() {
        Cache.restartVotes.clear();
        Events.fire(new EventType.GameOverEvent(Team.derelict));
        Groups.player.each(p -> p.team(Team.derelict));

    }

    // destroy all the buildings of a team and send them to derelict
    public void kill_team(Team team) {
        team.data().destroyToDerelict();
        Groups.player.each(p -> p.team() == team, p -> {
            p.team(Team.derelict);
            p.unit().kill();
        });

    }
}