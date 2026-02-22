package main;

import mindustry.mod.Plugin;
import mindustry.net.Administration;
import mindustry.ui.Menus;
import mindustry.game.EventType;
import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.Seq;
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
    public int teamMenuId, mainMenuId, joinMenuId, acceptMenuId, denyMenuId, kickMenuId;
    public static ObjectMap<String, String> teamRequests = new ObjectMap<>();

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

        //Team menu setup
        teamMenuId = Menus.registerMenu((player, selection) -> {
            if (selection == -1) return;
            if(selection == 0){
                showJoinMenu(player);
            }
            if (!isLeader(player)) {
            player.sendMessage("Only team leaders can manage their teams.");
         return;
            }
            if (selection == 1) {
                showAcceptMenu(player);
            } 
            if (selection == 2) {
                showKickMenu(player);
            } 
            if (selection == 3) {
                showDenyMenu(player);
            }
            
        });



        joinMenuId = Menus.registerMenu((player, selection) -> {
            if (selection == -1) return;
            Seq<Player> otherPlayers = getOthers(player);
            Player target = otherPlayers.get(selection);
            teamRequests.put(player.uuid(), target.uuid());
            player.sendMessage("You have sent a team join request to " + target.name);
            target.sendMessage(player.name + " has requested to join your team.");
        });

        acceptMenuId = Menus.registerMenu((player, selection) -> {
            if (selection == -1) return;
             Seq<Player> requesters = getRequesters(player);
            Player found = requesters.get(selection);
        
            found.team(player.team());
            teamRequests.remove(found.uuid());
            player.sendMessage("You accepted " + found.name);
            found.sendMessage("Your team join request has been accepted by " + player.name);
        });
        denyMenuId = Menus.registerMenu((player, selection) -> {
        if (selection == -1) return;
        Seq<Player> requesters = getRequesters(player);
        Player found = requesters.get(selection);
        
        teamRequests.remove(found.uuid());
        player.sendMessage("[orange]You have denied the request from " + found.name);
        found.sendMessage("[red]Your team join request has been denied.");
        });
        kickMenuId = Menus.registerMenu((player, selection) -> {
        if (selection == -1) return;
        Seq<Player> teammates = getTeammates(player);
        if (selection < teammates.size) {
        Player target = teammates.get(selection);
        
        target.team(Team.derelict);
        if(target.unit() != null) target.unit().kill();
        target.team(Team.derelict);
        player.sendMessage("[red]You have kicked " + target.name);
        target.sendMessage("[red]You have been kicked from the team.");
        }
        });

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
            if (pla.team() == Team.all[0] || pla.team() == Team.all[1]) {
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
                    pla.sendMessage("Too close to another core");
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

    private Seq<Player> getOthers(Player p) {
    Seq<Player> list = new Seq<>();
    Groups.player.each(other -> {
        if (other != p && other.team() != Team.derelict) {
            list.add(other);
        }
    });
    return list;
    }

    private Seq<Player> getRequesters(Player p) {
    Seq<Player> list = new Seq<>();
    for(var entry : teamRequests.entries()){
        if(entry.value.equals(p.uuid())){
            Player req = Groups.player.find(found -> found.uuid().equals(entry.key));
            if(req != null) list.add(req);
        }
    }
    return list;
    }

    private Seq<Player> getTeammates(Player p) {
    Seq<Player> list = new Seq<>();
    Groups.player.each(other -> other.team() == p.team() && other != p, list::add);
    return list;
    }

    private void showJoinMenu(Player p) {
    Seq<Player> players = getOthers(p);
    String[][] buttons = new String[players.size][1];
    for(int i = 0; i < players.size; i++) buttons[i][0] = players.get(i).name;
    Call.menu(p.con, joinMenuId, "Join to", "Choose a lider", buttons);
    }

    private void showAcceptMenu(Player p) {
    Seq<Player> players = getRequesters(p);
    if(players.isEmpty()){ p.sendMessage("No requests available]"); return; }
    String[][] buttons = new String[players.size][1];
    for(int i = 0; i < players.size; i++) buttons[i][0] = players.get(i).name;
    Call.menu(p.con, acceptMenuId, "Accept team request", "Choose a player:", buttons);
    }

    private void showDenyMenu(Player p) {
    Seq<Player> players = getRequesters(p);
    if(players.isEmpty()){ p.sendMessage("No requests available."); return; }
    String[][] buttons = new String[players.size][1];
    for(int i = 0; i < players.size; i++) buttons[i][0] = players.get(i).name;
    Call.menu(p.con, denyMenuId, "Deny team request", "Choose a player to deny:", buttons);
    }

    private void showKickMenu(Player p) {
    Seq<Player> players = getTeammates(p);
    if(players.isEmpty()){ p.sendMessage("[red]No teammates available."); return; }
    String[][] buttons = new String[players.size][1];
    for(int i = 0; i < players.size; i++) buttons[i][0] = players.get(i).name;
    Call.menu(p.con, kickMenuId, "Kick player from team", "Choose a player to kick:", buttons);
    }
    private boolean isLeader(Player p) {
    var info = Cache.teams_Info.get(p.team());
    return info != null && info.leaderUuid.equals(p.uuid());
    }


    public void handleMenuSelection(Player player, int selection) {
        switch (selection) {
            case 0:
                // Handle first menu option
                player.sendMessage("You selected option to join a team.");
                break;
            case 1:
                // Handle second menu option
                player.sendMessage("You selected option to accept a team join request.");
                break;
            case 2:
                // Handle third menu option
                player.sendMessage("You selected option to kick a player from your team.");
                break;
            case 3:
                // Handle fourth menu option
                player.sendMessage("You selected option to decline a team join request.");
                break;
        }
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

       handler.<Player>register("team", "Team managment", (args, player) -> {
            String[][] buttons = {
                {"[green]Join"}, 
                {"[blue]Accept"},
                {"[orange]Kick"},
                {"[red]Deny"}
            };
            // Open the team management menu for the player
            Call.menu(player.con, teamMenuId, "[accent]Team Menu", "Choose an action:", buttons);
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