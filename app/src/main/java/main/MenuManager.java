package main;

import arc.struct.Seq;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.ui.Menus;

import static main.Cache.teamRequests;

public class MenuManager {

    public static int teamMenuId;
    public int joinMenuId;
    public int acceptMenuId;
    public int denyMenuId;
    public int kickMenuId;

    public void init(){
        // Team menu setup
        teamMenuId = Menus.registerMenu((player, selection) -> {
            if (selection == -1)
                return;
            if (selection == 0) {
                showJoinMenu(player);
            }
            if (!isLeader(player)) {
                player.sendMessage("[#FFC0CB]Only team leaders can manage their teams.");
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
            if (selection == -1)
                return;
            Seq<Player> otherPlayers = getOthers(player);
            Player target = otherPlayers.get(selection);
            teamRequests.put(player.uuid(), target.uuid());
            player.sendMessage("[#F4A460]You have sent a team join request to " + target.name);
            target.sendMessage(player.name + " [#F4A460]has requested to join your team.");
        });

        acceptMenuId = Menus.registerMenu((player, selection) -> {
            if (selection == -1)
                return;
            Seq<Player> requesters = getRequesters(player);
            Player found = requesters.get(selection);

            found.team(player.team());
            teamRequests.remove(found.uuid());
            player.sendMessage("[#32CD32]You accepted " + found.name);
            found.sendMessage("[#32CD32]Your team join request has been accepted by " + player.name);
        });
        denyMenuId = Menus.registerMenu((player, selection) -> {
            if (selection == -1)
                return;
            Seq<Player> requesters = getRequesters(player);
            Player found = requesters.get(selection);

            teamRequests.remove(found.uuid());
            player.sendMessage("[#DC143C]You have denied the request from " + found.name);
            found.sendMessage("[#DC143C]Your team join request has been denied.");
        });
        kickMenuId = Menus.registerMenu((player, selection) -> {
            if (selection == -1)
                return;
            Seq<Player> teammates = getTeammates(player);
            if (selection < teammates.size) {
                Player target = teammates.get(selection);

                target.team(Team.derelict);
                if (target.unit() != null)
                    target.unit().kill();
                target.team(Team.derelict);
                player.sendMessage("[#8B0000]You have kicked " + target.name);
                target.sendMessage("[#8B0000]You have been kicked from the team.");
            }
        });
    }

    private boolean isLeader(Player p) {
        var info = Cache.teams_Info.get(p.team());
        return info != null && info.leaderUuid.equals(p.uuid());
    }

    private void showJoinMenu(Player p) {
        Seq<Player> players = getOthers(p);
        String[][] buttons = new String[players.size][1];
        for (int i = 0; i < players.size; i++)
            buttons[i][0] = players.get(i).name;
        Call.menu(p.con, joinMenuId, "Join to", "Choose a lider", buttons);
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
        for (var entry : teamRequests.entries()) {
            if (entry.value.equals(p.uuid())) {
                Player req = Groups.player.find(found -> found.uuid().equals(entry.key));
                if (req != null)
                    list.add(req);
            }
        }
        return list;
    }

    private void showAcceptMenu(Player p) {
        Seq<Player> players = getRequesters(p);
        if (players.isEmpty()) {
            p.sendMessage("[#F08080]No requests available]");
            return;
        }
        String[][] buttons = new String[players.size][1];
        for (int i = 0; i < players.size; i++)
            buttons[i][0] = players.get(i).name;
        Call.menu(p.con, acceptMenuId, "Accept team request", "Choose a player:", buttons);
    }

    private void showDenyMenu(Player p) {
        Seq<Player> players = getRequesters(p);
        if (players.isEmpty()) {
            p.sendMessage("[#F08080]No requests available.");
            return;
        }
        String[][] buttons = new String[players.size][1];
        for (int i = 0; i < players.size; i++)
            buttons[i][0] = players.get(i).name;
        Call.menu(p.con, denyMenuId, "Deny team request", "Choose a player to deny:", buttons);
    }

    private void showKickMenu(Player p) {
        Seq<Player> players = getTeammates(p);
        if (players.isEmpty()) {
            p.sendMessage("[#8B0000]No teammates available.");
            return;
        }
        String[][] buttons = new String[players.size][1];
        for (int i = 0; i < players.size; i++)
            buttons[i][0] = players.get(i).name;
        Call.menu(p.con, kickMenuId, "Kick player from team", "Choose a player to kick:", buttons);
    }

    private Seq<Player> getTeammates(Player p) {
        Seq<Player> list = new Seq<>();
        Groups.player.each(other -> other.team() == p.team() && other != p, list::add);
        return list;
    }

}