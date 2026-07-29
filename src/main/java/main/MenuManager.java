package main;

import arc.struct.Seq;
import arc.util.Log;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.ui.Menus;

public class MenuManager {

    public void init(){
        // Team menu setup
        Cache.teamMenuId = Menus.registerMenu((player, selection) -> {
            if (selection == -1)
                return;
            if (selection == 0) {
                showJoinMenu(player);
            }
            if (selection == 1) {
                if (!isLeader(player)) {
                    player.sendMessage(Localisation.local(player, "teamMenuLeaderException"));
                    return;
                }
                showAcceptMenu(player);
            }
            if (selection == 2) {
                if (!isLeader(player)) {
                    player.sendMessage(Localisation.local(player, "teamMenuLeaderException"));
                    return;
                }
                showKickMenu(player);
            }
            if (selection == 3) {
                if (!isLeader(player)) {
                    player.sendMessage(Localisation.local(player, "teamMenuLeaderException"));
                    return;
                }
                showDenyMenu(player);
            }
            if (selection == 4) {
                if (!isLeader(player)) {
                    player.sendMessage(Localisation.local(player, "teamMenuLeaderException"));
                    return;
                }
                showLeaderSetMenu(player);
            }
            if (selection == 5) {

            }

        });

        Cache.joinMenuId = Menus.registerMenu((player, selection) -> {
            if (selection == -1)
                return;
            Seq<Player> otherPlayers = getOthers(player);
            Player target = otherPlayers.get(selection);
            Cache.teamRequests.put(player.uuid(), target.uuid());
            player.sendMessage(Localisation.local(player, "joinMenuSentRequest") + " " + target.name);
            target.sendMessage(player.name + " " + Localisation.local(target, "joinMenuGotRequest"));
        });

        Cache.acceptMenuId = Menus.registerMenu((player, selection) -> {
            if (selection == -1) return;

            Seq<Player> requesters = getRequesters(player);
            Player found = requesters.get(selection);

            Team oldTeam = found.team();
            boolean wasLeader = isLeader(found);
            found.team(player.team());
            if (found.unit() != null) {
                found.unit().kill();
            }

            Cache.teamRequests.remove(found.uuid());

            player.sendMessage(Localisation.local(player, "teamRequestsAccept")  + " " + found.name);
            found.sendMessage( Localisation.local(found, "teamRequestsAccepted")  + " " + player.name);
            if (wasLeader) {

                TeamInfo oldInfo = Cache.teams_Info.get(oldTeam);

                if (oldInfo != null) {
                    oldInfo.leaderUuid = "";
                }

                Seq<Player> remainingPlayers = new Seq<>();

                Groups.player.each(p -> {
                    if (p.team() == oldTeam) {
                        remainingPlayers.add(p);
                    }
                });

                if (remainingPlayers.isEmpty()) {
                    kill_team(oldTeam);
                    if (Cache.teams_Info.containsKey(oldTeam)) {
                        Cache.teams_Info.get(oldTeam).leaderUuid = "";
                    }
                } else {
                    Player newLeader = remainingPlayers.random();
                    if (oldInfo != null) {
                        oldInfo.leaderUuid = newLeader.uuid();
                    }
                    newLeader.sendMessage(Localisation.local(player, "teamLeaderLeftMessage"));
                }
            }
        });

        Cache.denyMenuId = Menus.registerMenu((player, selection) -> {
            if (selection == -1)
                return;
            Seq<Player> requesters = getRequesters(player);
            Player found = requesters.get(selection);

            Cache.teamRequests.remove(found.uuid());
            player.sendMessage(Localisation.local(player, "teamRequestsDeny") + found.name);
            found.sendMessage(Localisation.local(found, "teamRequestsDenied"));
        });
        Cache.kickMenuId = Menus.registerMenu((player, selection) -> {
            if (selection == -1)
                return;
            Seq<Player> teammates = getTeammates(player);
            if (selection < teammates.size) {
                Player target = teammates.get(selection);

                target.team(Team.all[0]);
                if (target.unit() != null) target.unit().kill();
                target.team(Team.all[0]);
                player.sendMessage(Localisation.local(player, "kickMenuKick") + " " + target.name);
                target.sendMessage(Localisation.local(target, "kickMenuKicked"));
            }
        });

        Cache.SetLeaderMenuId = Menus.registerMenu((player, selection) -> {
            if (selection == -1)
                return;
            Seq<Player> teammates = getTeammates(player);
            if (selection < teammates.size) {
                Player target = teammates.get(selection);
                TeamInfo info = Cache.teams_Info.get(player.team());
                if (info != null) {
                    info.leaderUuid = target.uuid();
                    player.sendMessage(Localisation.local(player, "SetLeaderMenuTransfer") + " " + target.name);
                    target.sendMessage(Localisation.local(target, "SetLeaderMenuTransferred"));
                }
            }

        });

        Cache.WelcomeMenuId = Menus.registerMenu((player, selection) ->{
            if(selection == 0){
                Call.openURI(player.con, "https://discord.gg/GMQRKUn8W8");
            }
        });

        Cache.LeaderBoardMenuId = Menus.registerMenu((player, selection) -> {
            if(selection == 0){
                showLeaderBoard(player);
            }
            if(selection == 1){
                return;
            }
        });
    }

    public void kill_team(Team team) {
        team.data().destroyToDerelict();
        Groups.player.each(p -> p.team() == team, p -> {
            p.team(Team.all[0]);
            if (p.unit() != null) p.unit().kill();
        });

    }

    private boolean isLeader(Player p) {
        var info = Cache.teams_Info.get(p.team());
        return info != null && info.leaderUuid.equals(p.uuid());
    }

    private Seq<Player> getOthers(Player p) {
        Seq<Player> list = new Seq<>();
        Groups.player.each(other -> {
            if (other == p) return;

            TeamInfo info = Cache.teams_Info.get(other.team());

            if (info != null &&
                    info.leaderUuid != null &&
                    info.leaderUuid.equals(other.uuid())) {

                list.add(other);
            }
        });
        return list;
    }

    private Seq<Player> getRequesters(Player p) {
        Seq<Player> list = new Seq<>();
        for (var entry : Cache.teamRequests.entries()) {
            if (entry.value.equals(p.uuid())) {
                Player req = Groups.player.find(found -> found.uuid().equals(entry.key));
                if (req != null)
                    list.add(req);
            }
        }
        return list;
    }

    public void showAcceptMenu(Player p) {
        Seq<Player> players = getRequesters(p);
        if (players.isEmpty()) {
            p.sendMessage(Localisation.local(p, "MenuNoRequest"));
            return;
        }
        String[][] buttons = new String[players.size][1];
        for (int i = 0; i < players.size; i++)
            buttons[i][0] = players.get(i).name;
        Call.menu(p.con, Cache.acceptMenuId, Localisation.local(p, "acceptMenuTitle"), Localisation.local(p, "acceptMenuMessage"), buttons);

    }

    public void showDenyMenu(Player p) {
        Seq<Player> players = getRequesters(p);
        if (players.isEmpty()) {
            p.sendMessage(Localisation.local(p, "MenuNoRequest"));
            return;
        }
        String[][] buttons = new String[players.size][1];
        for (int i = 0; i < players.size; i++)
            buttons[i][0] = players.get(i).name;
        Call.menu(p.con, Cache.denyMenuId, Localisation.local(p, "denyMenuTitle"), Localisation.local(p, "denyMenuMessage"), buttons);
    }

    private void showKickMenu(Player p) {
        Seq<Player> players = getTeammates(p);
        if (players.isEmpty()) {
            p.sendMessage(Localisation.local(p, "teammatesMenuNoRequest"));
            return;
        }
        String[][] buttons = new String[players.size][1];
        for (int i = 0; i < players.size; i++)
            buttons[i][0] = players.get(i).name;
        Call.menu(p.con, Cache.kickMenuId, Localisation.local(p, "kickMenuTitle"), Localisation.local(p, "kickMenuMessage"), buttons);
    }

    private Seq<Player> getTeammates(Player p) {
        Seq<Player> list = new Seq<>();
        Groups.player.each(other -> other.team() == p.team() && other != p, list::add);
        return list;
    }

    private void showLeaderSetMenu(Player p) {
        Seq<Player> players = getTeammates(p);
        if (players.isEmpty()) {
            p.sendMessage(Localisation.local(p, "teammatesMenuNoRequest"));
            return;
        }
        String[][] buttons = new String[players.size][1];
        for (int i = 0; i < players.size; i++)
            buttons[i][0] = players.get(i).name;
        Call.menu(p.con, Cache.SetLeaderMenuId, Localisation.local(p, "leaderMenuTitle"), Localisation.local(p, "leaderMenuMessage"), buttons);
    }

    public void showJoinMenu(Player p) {
        Seq<Player> players = getOthers(p);
        String[][] buttons = new String[players.size][1];
        for (int i = 0; i < players.size; i++)
            buttons[i][0] = players.get(i).name;
        Call.menu(p.con, Cache.joinMenuId, Localisation.local(p, "joinMenuTitle"), Localisation.local(p, "joinMenuMessage"), buttons);
    }

    public void showLeaderBoard(Player p) {
        var topList = LeaderBoardManager.getTop(10);
        StringBuilder sb = new StringBuilder();

        if (topList.isEmpty()) {
            sb.append(Localisation.local(p, "leaderboardEmptyMessage"));
        } else {
            for (LeaderBoardManager.Player data : topList) {
                String rankColor = switch (data.position) {
                    case 1 -> "[gold]";
                    case 2 -> "[lightgray]";
                    case 3 -> "[accent]";
                    default -> "[white]";
                };

                sb.append(rankColor).append("#").append(data.position).append(" ")
                        .append("[white]").append(data.name)
                        .append(" [gray]- [green]").append(data.points).append(" points\n");
            }
        }
        String[][] options = {
                {Localisation.local(p, "leaderboardUpdate")},
                {Localisation.local(p, "leaderboardClose")}
        };

        Call.menu(
                p.con,
                Cache.LeaderBoardMenuId,
                Localisation.local(p, "leaderboardTitle"),
                sb.toString(),
                options
        );
    }
}