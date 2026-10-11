package main;

import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.ui.Menus;

public class MenuManager {

    private record Shown(int menuId, Seq<String> uuids) {}
    private final ObjectMap<String, Shown> shown = new ObjectMap<>();

    public void init(){
        // Team menu setup
        Cache.teamMenuId = Menus.registerMenu((player, selection) -> {
            switch (selection) {
                case 0 -> showJoinMenu(player);
                case 1 -> showAcceptMenu(player);
                case 2 -> showKickMenu(player);
                case 3 -> showDenyMenu(player);
                case 4 -> showLeaderSetMenu(player);
                default -> { } // -1: menu closed, 5: "close" button
            }
        });

        Cache.joinMenuId = Menus.registerMenu((player, selection) -> {
            Player target = pick(player, Cache.joinMenuId, selection);
            // the target may have lost leadership, or the player may already be in that team
            if (target == null || !isLeader(target) || target.team() == player.team()) return;
            Cache.teamRequests.put(player.uuid(), target.uuid());
            player.sendMessage(Localisation.local(player, "joinMenuSentRequest") + " " + target.name);
            target.sendMessage(player.name + " " + Localisation.local(target, "joinMenuGotRequest"));
        });

        Cache.acceptMenuId = Menus.registerMenu((player, selection) -> {
            Player found = pick(player, Cache.acceptMenuId, selection);
            if (found == null || !isLeader(player) || !hasRequest(found, player)) return;

            Cache.teamRequests.remove(found.uuid());
            if (found.team() == player.team()) return;

            Team oldTeam = found.team();
            boolean wasLeader = isLeader(found);
            found.team(player.team());
            if (found.unit() != null) {
                found.unit().kill();
            }

            player.sendMessage(Localisation.local(player, "teamRequestsAccept")  + " " + found.name);
            found.sendMessage( Localisation.local(found, "teamRequestsAccepted")  + " " + player.name);
            // The leader of the old team left it: an online teammate takes over,
            // and if nobody from that team is online, the team surrenders
            if (wasLeader && passLeadership(oldTeam, found) == null) {
                TeamDestroyTracker.surrenderTeam(oldTeam);
            }
        });

        Cache.denyMenuId = Menus.registerMenu((player, selection) -> {
            Player found = pick(player, Cache.denyMenuId, selection);
            if (found == null || !hasRequest(found, player)) return;

            Cache.teamRequests.remove(found.uuid());
            player.sendMessage(Localisation.local(player, "teamRequestsDeny") + " " + found.name);
            found.sendMessage(Localisation.local(found, "teamRequestsDenied"));
        });

        Cache.kickMenuId = Menus.registerMenu((player, selection) -> {
            Player target = pick(player, Cache.kickMenuId, selection);
            if (target == null || target == player || !isLeader(player) || target.team() != player.team()) return;

            target.team(Team.derelict);
            if (target.unit() != null) target.unit().kill();
            player.sendMessage(Localisation.local(player, "kickMenuKick") + " " + target.name);
            target.sendMessage(Localisation.local(target, "kickMenuKicked"));
        });

        Cache.SetLeaderMenuId = Menus.registerMenu((player, selection) -> {
            Player target = pick(player, Cache.SetLeaderMenuId, selection);
            if (target == null || target == player || !isLeader(player) || target.team() != player.team()) return;

            TeamInfo info = Cache.teamsInfo.get(player.team());
            if (info == null) return;
            setLeader(info, target);
            player.sendMessage(Localisation.local(player, "SetLeaderMenuTransfer") + " " + target.name);
            target.sendMessage(Localisation.local(target, "SetLeaderMenuTransferred"));
        });

        Cache.WelcomeMenuId = Menus.registerMenu((player, selection) ->{
            if(selection == 0){
                showGuideOne(player);
            }
            if(selection == 1){
                Call.openURI(player.con, "https://discord.gg/GMQRKUn8W8");
            }
        });

        Cache.GuideOneMenuId = Menus.registerMenu((player, selection) -> {
            if(selection == 0){
                showGuideTwo(player);
            }
        });

        Cache.GuideTwoMenuId = Menus.registerMenu((player, selection) -> {
            if(selection == 0){
                showGuideThree(player);
            }
        });

        Cache.GuideThreeMenuId = Menus.registerMenu((player, selection) -> {});

        Cache.LeaderBoardMenuId = Menus.registerMenu((player, selection) -> {
            if(selection == 0){
                showLeaderBoard(player);
            }
        });
    }

    public void forget(Player p) {
        shown.remove(p.uuid());
    }

    public void clear() {
        shown.clear();
    }

    public Player passLeadership(Team team, Player except) {
        TeamInfo info = Cache.teamsInfo.get(team);
        if (info == null) return null;

        Seq<Player> candidates = new Seq<>();
        Groups.player.each(p -> p != except && p.team() == team, candidates::add);
        if (candidates.isEmpty()) return null;

        Player newLeader = candidates.random();
        setLeader(info, newLeader);
        newLeader.sendMessage(Localisation.local(newLeader, "teamLeaderLeftMessage"));
        return newLeader;
    }

    private static void setLeader(TeamInfo info, Player newLeader) {
        String oldLeader = info.getLeaderUuid();
        info.setLeaderUuid(newLeader.uuid());
        if (oldLeader == null) return;
        for (var entry : Cache.teamRequests) {
            // replacing the value of an existing key doesn't change the map's structure, so it is safe while iterating
            if (oldLeader.equals(entry.value)) Cache.teamRequests.put(entry.key, newLeader.uuid());
        }
    }

    private boolean isLeader(Player p) {
        TeamInfo info = Cache.teamsInfo.get(p.team());
        return info != null && info.isLeader(p);
    }

    private boolean requireLeader(Player p) {
        if (isLeader(p)) return true;
        p.sendMessage(Localisation.local(p, "teamMenuLeaderException"));
        return false;
    }

    private static boolean hasRequest(Player requester, Player leader) {
        return leader.uuid().equals(Cache.teamRequests.get(requester.uuid()));
    }

    private void showPlayerMenu(Player p, int menuId, String title, String message, Seq<Player> targets) {
        shown.put(p.uuid(), new Shown(menuId, targets.map(Player::uuid)));
        String[][] buttons = new String[targets.size][];
        for (int i = 0; i < targets.size; i++) {
            buttons[i] = new String[]{targets.get(i).name};
        }
        Call.menu(p.con, menuId, title, message, buttons);
    }

    private Player pick(Player p, int menuId, int selection) {
        Shown s = shown.get(p.uuid());
        if (s == null || s.menuId() != menuId) return null;
        shown.remove(p.uuid());
        if (selection < 0 || selection >= s.uuids().size) return null;

        String uuid = s.uuids().get(selection);
        return Groups.player.find(other -> other.uuid().equals(uuid));
    }
    private Seq<Player> getOthers(Player p) {
        Seq<Player> list = new Seq<>();
        Groups.player.each(other -> other != p && other.team() != p.team() && isLeader(other), list::add);
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
        if (!requireLeader(p)) return;
        Seq<Player> players = getRequesters(p);
        if (players.isEmpty()) {
            p.sendMessage(Localisation.local(p, "MenuNoRequest"));
            return;
        }
        showPlayerMenu(p, Cache.acceptMenuId, Localisation.local(p, "acceptMenuTitle"), Localisation.local(p, "acceptMenuMessage"), players);
    }

    public void showDenyMenu(Player p) {
        if (!requireLeader(p)) return;
        Seq<Player> players = getRequesters(p);
        if (players.isEmpty()) {
            p.sendMessage(Localisation.local(p, "MenuNoRequest"));
            return;
        }
        showPlayerMenu(p, Cache.denyMenuId, Localisation.local(p, "denyMenuTitle"), Localisation.local(p, "denyMenuMessage"), players);
    }

    private void showKickMenu(Player p) {
        if (!requireLeader(p)) return;
        Seq<Player> players = getTeammates(p);
        if (players.isEmpty()) {
            p.sendMessage(Localisation.local(p, "teammatesMenuNoRequest"));
            return;
        }
        showPlayerMenu(p, Cache.kickMenuId, Localisation.local(p, "kickMenuTitle"), Localisation.local(p, "kickMenuMessage"), players);
    }

    private Seq<Player> getTeammates(Player p) {
        Seq<Player> list = new Seq<>();
        Groups.player.each(other -> other.team() == p.team() && other != p, list::add);
        return list;
    }

    private void showLeaderSetMenu(Player p) {
        if (!requireLeader(p)) return;
        Seq<Player> players = getTeammates(p);
        if (players.isEmpty()) {
            p.sendMessage(Localisation.local(p, "teammatesMenuNoRequest"));
            return;
        }
        showPlayerMenu(p, Cache.SetLeaderMenuId, Localisation.local(p, "leaderMenuTitle"), Localisation.local(p, "leaderMenuMessage"), players);
    }

    public void showJoinMenu(Player p) {
        Seq<Player> players = getOthers(p);
        if (players.isEmpty()) {
            p.sendMessage(Localisation.local(p, "joinMenuNoTeams"));
            return;
        }
        showPlayerMenu(p, Cache.joinMenuId, Localisation.local(p, "joinMenuTitle"), Localisation.local(p, "joinMenuMessage"), players);
    }

    public void showLeaderBoard(Player p) {
        LeaderBoardManager.getTop(10, topList -> {
            if (!p.isAdded()) return; // left while the query was running

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
        });
    }

    public void showGuide(Player p){
        Call.menu(p.con, Cache.WelcomeMenuId, "",Localisation.local(p, "WelcomeTitle"), new String[][]{
                {Localisation.local(p, "ShowGuide")},
                {Localisation.local(p, "OpenDiscordButton")},
                {Localisation.local(p, "menuCloseButton")},
        });
    }
    private void showGuideOne(Player p){
        String message = Localisation.local(p, "GuideOneMessageOne") + "\n" +  Localisation.local(p, "GuideOneMessageTwo");
        Call.menu(p.con, Cache.GuideOneMenuId, Localisation.local(p, "GuideOneTitle"), message, new String[][]{
                {Localisation.local(p, "NextMenuButton")},
                {Localisation.local(p, "menuCloseButton")},
        });
    }

    private void showGuideTwo(Player p) {
        String message = Localisation.local(p, "GuideTwoMessageOne") + "\n" + Localisation.local(p, "GuideTwoMessageTwo") + "\n" + Localisation.local(p, "GuideTwoMessageThree") + "\n" + Localisation.local(p, "GuideTwoMessageFour");
        Call.menu(p.con, Cache.GuideTwoMenuId, Localisation.local(p, "GuideTwoTitle"), message, new String[][]{
                {Localisation.local(p, "NextMenuButton")},
                {Localisation.local(p, "menuCloseButton")},
        });
    }
    private void showGuideThree(Player p){
        String message = Localisation.local(p, "GuideThreeMessageOne") + "\n" + Localisation.local(p, "GuideThreeMessageTwo") + "\n" + Localisation.local(p, "GuideThreeMessageThree");
        Call.menu(p.con, Cache.GuideThreeMenuId, Localisation.local(p, "GuideThreeTitle"), message, new String[][]{
                {Localisation.local(p, "menuCloseButton")},
        });
    }
}