package main;

import mindustry.mod.Plugin;
import mindustry.net.Administration;
import mindustry.game.EventType;
import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Time;
import java.util.HashMap;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Planets;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;

public class Main extends Plugin {

    public Integer maxTime = 10800;

    @Override
    public void init() {
        // Initialization code here
        // Sertting up server name and MOTD
        Administration.Config.serverName.set("Foundation PvP");
        Administration.Config.motd.set("Our discord: discord.gg/Hamn9EhyQj");
        // Starting the server
        Events.on(EventType.WorldLoadBeginEvent.class, event -> {
            Log.info("world load");
        });
        // Initializing the cache of teamleaders
        for (Team team : Team.all) {
            Cache.teams_Info.put(team, new TeamInfo());
        }
        Cache.players_Info = new HashMap<>();
        Cache.playerTeams = new HashMap<>();
        Cache.teams_Info = new HashMap<>();
        Cache.teamRequests = new HashMap<>();
        maxTime = 648000;

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
                        return;
                    }
                }
            }
        });

        // When the game begins we destroy the center core and put all the players into
        // team derelict
        Events.on(EventType.GameOverEvent.class, event -> {
            Groups.player.each(p -> {
                p.team(Team.derelict);
            });
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
                pl.team(savedTeam); // back to the team they were on before
            } else {
                pl.team(Team.derelict); // default team for new players
            }
        });
        // When a player leaves the server
        Events.on(EventType.PlayerLeave.class, event -> {

            Player pl = event.player;
            Cache.playerTeams.put(pl.uuid(), pl.team());
            if (!Cache.players_Info.containsKey(pl.uuid())) {
                Cache.players_Info.put(pl.uuid(), new Administration.PlayerInfo());
            }

            Cache.teamRequests.remove(event.player.uuid());
        });
        // When a player clicks on a tile to create a core and command
        Events.on(EventType.TapEvent.class, event -> {
            Player pla = event.player;
            Tile tile = event.tile;
            if (pla.team() == Team.all[0]) {
                if (tile.solid()) {
                    return;
                }
                boolean close = false;
                float mindist = 100f;
                for (var build : Groups.build) {
                    if (build instanceof mindustry.world.blocks.storage.CoreBlock.CoreBuild) {
                        if (tile.dst(build.tile) < mindist * 8) {
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
                    Call.sendMessage("Too close to another core");
                }
            }
        });
        // Replacing vault with core sharped
        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if (event.breaking || event.tile.block() != Blocks.vault)
                return;
            Team builderTeam = event.team;
            Tile tile = event.tile;
            Player player = event.unit != null ? event.unit.getPlayer() : null;
            Time.run(1f, () -> {
                tile.setNet(Blocks.coreShard, builderTeam, 0);
                if (player != null) {
                }
            });
        });

        Events.on(EventType.BlockBuildEndEvent.class, event -> {
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
        Events.on(EventType.PlayEvent.class, event -> {
            Time.run(1f, () -> {
                Groups.build.each(b -> b instanceof CoreBlock.CoreBuild, b -> {
                    b.tile.removeNet();
                    Groups.player.each(p -> {
                        p.team(Team.derelict);
                    });
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
                    Time.run(60f, () -> {
                        Groups.player.each(p -> {
                            p.team(Team.all[0]);
                        });
                    });
                });
            });
        });
    }

    protected void updateMOTD() {
        int hours = maxTime / 3600;
        int minutes = (maxTime % 3600) / 60;
        String timeString = String.format("%02d:%02d", hours, minutes);
        String motd = "Welcome to Foundation PvP!" +
                "Time until round end: " + timeString;
        Administration.Config.desc.set(motd);
    }

    public void registerClientCommands(CommandHandler handler) {
        // Register commands for client here
        handler.<Player>register("restart", "Restarts the game", (args, player) -> {
            if (Cache.restartVotes.contains(player.uuid())) {
                player.sendMessage("You have already voted to restart.");
                return;
            }
            Cache.restartVotes.add(player.uuid());
            int votesNeeded = (int) (Groups.player.size() * 0.6); // 60% of players need to vote
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
            Tile tile = ((Player) player).tileOn();
            Team playerTeam = ((Player) player).team();
            if (tile.build != null && tile.build.team == ((Player) player).team()) {
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
                    Team playerTeam = ((Player) player).team();
                    boolean isLeader = false;
                    // Checking if the player is the leader of the team
                    if (((Player) player).team() != Team.all[0]) {
                        TeamInfo info = Cache.teams_Info.get(playerTeam);
                        if (info != null && info.leaderUuid != null) {
                            if (info.leaderUuid.equals(((Player) player).uuid())) {
                                isLeader = true;
                            }
                        }
                        if (isLeader) {
                            kill_team(((Player) player).team());
                            ((Player) player).team(Team.derelict);
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

        handler.<Player>register("join", "<name...>", "Join a team", (args, player) -> {
            if (args.length == 0) {
                Player target = Groups.player.find(p -> p.name.equalsIgnoreCase(args[0]));
                if (target == null) {
                    player.sendMessage("Player is not  found");
                    return;
                }
                if (target.team() == Team.all[0]) {
                    player.sendMessage("Player is not in a team");
                    return;
                }
                if (player.team() == target.team()) {
                    player.sendMessage("You are already in this team");
                    return;
                }
                // Sending a request to the target player
                Cache.teamRequests.put(player.uuid(), target.uuid());
                player.sendMessage("Request sent to " + target.name);
                target.sendMessage(
                        player.name + " wants to join your team. Type /accept to accept or /decline to decline");
            }
        });

        handler.<Player>register("accept", "Accept a team join request", (args, player) -> {
            Player requester = Groups.player.find(p -> p.name.equalsIgnoreCase(args[0]));
            if (requester != null) {
                requester.team(player.team());
                Cache.teamRequests.remove(requester.uuid());
                requester.sendMessage("You are now in team " + player.team().name);
                player.sendMessage("You accepted " + requester.name + " to your team.");
            } else {
                player.sendMessage("No active requests from this player.");
            }
        });

        handler.<Player>register("kick", "<name...>", "Kick a  player from  your team", (args, player) -> {

            Player target = Groups.player.find(p -> p.name.equalsIgnoreCase(args[0]));
            if (target != null && target.team() == player.team()) {
                if (target == player) {
                    player.sendMessage("You cannot kick yourself");
                    return;
                }
                target.team(Team.derelict);
                target.unit().kill();
                target.sendMessage("You have been kicked from the team by " + player.name);
                player.sendMessage("You kicked " + target.name + " from the team.");
            } else {
                player.sendMessage("Player is not found or not in your team");
            }

        });

        handler.<Player>register("deny", "Decline a team join request", (args, player) -> {
            Player requester = Groups.player.find(p -> p.name.equalsIgnoreCase(args[0]));
            if (requester != null) {
                Cache.teamRequests.remove(requester.uuid());
                requester.sendMessage(
                        "Your request to join " + player.team().name + " has been declined by " + player.name);
                player.sendMessage("You declined " + requester.name + " to your team.");
            } else {
                player.sendMessage("No active requests from this player.");
            }
        });

    }

    public void registerServerCommands(CommandHandler handler) {
        // Register commands for server here
        handler.register("restart", "Restarts the game", args -> {
            restart();
        });
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
        Groups.player.each(p -> {
            p.team(Team.derelict);
        });

    }

    // destrioy all the buildings of a team and send them to derelict
    public void kill_team(Team team) {
        team.data().destroyToDerelict();
        Groups.player.each(p -> p.team() == team, p -> {
            p.team(Team.derelict);
            p.unit().kill();
        });

    }
}