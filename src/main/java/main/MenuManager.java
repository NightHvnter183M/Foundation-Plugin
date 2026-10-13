package main;

import arc.struct.ObjectIntMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Strings;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Iconc;
import mindustry.gen.Player;
import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiBuilder.ButtonBuilder;
import mindustry.ui.builder.UiBuilder.TableBuilder;

import java.util.List;

import static main.Localisation.local;
import static mindustry.Vars.state;
import static mindustry.ui.builder.UiBuilder.*;

public class MenuManager {

    private static final String DISCORD = "https://discord.gg/GMQRKUn8W8";
    private static final float WIDTH = Ui.WIDTH;
    private static final float ROW_BUTTON_HEIGHT = 44f;
    private static final float JOIN_BUTTON = 120f;
    private static final float LEAD_BUTTON = 200f;
    private static final float BUTTON_HEIGHT = 48f;
    private static final String[][] GUIDE = {
            {"GuideOneTitle", "GuideOneMessageOne", "GuideOneMessageTwo"},
            {"GuideTwoTitle", "GuideTwoMessageOne", "GuideTwoMessageTwo", "GuideTwoMessageThree", "GuideTwoMessageFour"},
            {"GuideThreeTitle", "GuideThreeMessageOne", "GuideThreeMessageTwo", "GuideThreeMessageThree"},
    };

    private static BuilderMenu teamMenu, leaderboardMenu, guideMenu;
    private static final ObjectSet<String> confirmingSurrender = new ObjectSet<>();
    private static final ObjectIntMap<String> guidePages = new ObjectIntMap<>();

    public static void init() {
        teamMenu = new BuilderMenu(false, true, MenuManager::teamAction);
        leaderboardMenu = new BuilderMenu(false, true, (player, action, result) -> {
            if ("refresh".equals(action)) showLeaderBoard(player);
        });
        guideMenu = new BuilderMenu(false, false, MenuManager::guideAction);
    }

    public static void forget(Player p) {
        teamMenu.forget(p);
        leaderboardMenu.forget(p);
        guideMenu.forget(p);
        confirmingSurrender.remove(p.uuid());
        guidePages.remove(p.uuid(), 0);
    }

    public static void reset() {
        teamMenu.hideAll();
        confirmingSurrender.clear();
    }

    public static void showTeamPanel(Player p) {
        teamMenu.show(p, local(p, "uiTeamsTitle"), table()
                .add(pane().id("teams").align("top").width(Ui.PANE_WIDTH).growY().add(teamContent(p))));
    }

    public static void refreshTeamPanels() {
        refreshTeamPanels(null);
    }

    private static void refreshTeamPanels(Player except) {
        Groups.player.each(p -> p != except, p -> teamMenu.update(p, "teams", table().add(teamContent(p))));
    }

    private static TableBuilder teamContent(Player p) {
        TableBuilder t = table();
        Team team = p.team();
        TeamInfo info = Cache.teamsInfo.get(team);
        boolean inTeam = info != null && TeamInfo.isPlayerTeam(team) && team.active();
        boolean leader = inTeam && info.isLeader(p);

        if (inTeam) {
            title(t, Ui.colored(team, local(p, "uiYourTeam")));
            for (Player member : members(team)) {
                String name = (info.isLeader(member) ? "[accent]" + Iconc.star + "[] " : "") + member.name
                        + (member == p ? " [lightgray](" + local(p, "uiYou") + ")" : "");
                name(t, name);
                if (leader && member != p) {
                    t.add(actions(local(p, "uiBtnMakeLeader"), "lead:" + member.uuid(), LEAD_BUTTON,
                            "[scarlet]" + local(p, "uiBtnKick"), "kick:" + member.uuid())).row();
                }
            }

            Seq<Player> requesters = leader ? requesters(p) : new Seq<>();
            if (!requesters.isEmpty()) {
                title(t, "[accent]" + local(p, "uiRequests"));
                for (Player requester : requesters) {
                    name(t, requester.name);
                    t.add(actions("[green]" + local(p, "uiBtnAccept"), "accept:" + requester.uuid(), WIDTH / 2f - 2f,
                            "[scarlet]" + local(p, "uiBtnDeny"), "deny:" + requester.uuid())).row();
                }
            }
        } else {
            t.add(label("[lightgray]" + local(p, "uiSpectatorHint")).wrap().labelAlign("left").width(WIDTH)).row();
        }

        title(t, "[accent]" + local(p, "uiOtherTeams"));
        Seq<Team> others = otherTeams(p);
        if (others.isEmpty()) {
            t.add(label("[lightgray]" + local(p, "uiNoOtherTeams")).wrap().labelAlign("left").width(WIDTH)).row();
        }
        String requestedLeader = Cache.teamRequests.get(p.uuid());
        for (Team other : others) {
            Player otherLeader = Ui.onlineLeader(other);
            boolean requested = otherLeader.uuid().equals(requestedLeader);
            TableBuilder row = table();
            row.add(label(Ui.colored(other, otherLeader.name)).wrap().labelAlign("left").width(WIDTH - JOIN_BUTTON - 4f));
            row.add(button(local(p, requested ? "uiBtnCancelRequest" : "uiBtnRequest")).style("flatt")
                    .clicked((requested ? "unrequest:" : "join:") + other.id).width(JOIN_BUTTON).height(ROW_BUTTON_HEIGHT).padLeft(4f));
            t.add(row.padTop(2f)).row();
        }

        if (inTeam) {
            if (confirmingSurrender.contains(p.uuid())) {
                t.add(label("[scarlet]" + local(p, "uiSurrenderConfirm")).wrap().labelAlign("center").width(WIDTH).padTop(16f)).row();
                TableBuilder yesNo = table();
                yesNo.add(button("[scarlet]" + local(p, "uiBtnSurrenderYes")).style("flatt").clicked("surrender-yes").width(WIDTH / 2f - 2f).height(BUTTON_HEIGHT));
                yesNo.add(button(local(p, "uiBtnCancel")).style("flatt").clicked("surrender-no").width(WIDTH / 2f - 2f).height(BUTTON_HEIGHT).padLeft(4f));
                t.add(yesNo.padTop(4f)).row();
            } else {
                // a leader alone online can't leave the team without a leader: only surrendering is offered
                if (!leader || members(team).size > 1) {
                    t.add(button(local(p, "uiBtnLeave")).style("flatt").clicked("leave").width(WIDTH).height(BUTTON_HEIGHT).padTop(16f)).row();
                }
                if (leader) {
                    t.add(button("[scarlet]" + local(p, "uiBtnSurrender")).style("flatt").clicked("surrender").width(WIDTH).height(BUTTON_HEIGHT).padTop(4f)).row();
                }
            }
        }
        return t;
    }

    private static void title(TableBuilder t, String text) {
        t.add(label(text).wrap().labelAlign("left").width(WIDTH).padTop(12f).padBottom(2f)).row();
    }

    private static void name(TableBuilder t, String text) {
        t.add(label(text).wrap().labelAlign("left").width(WIDTH).padTop(4f)).row();
    }

    private static TableBuilder actions(String text1, String action1, float width1, String text2, String action2) {
        TableBuilder row = table();
        row.add(button(text1).style("flatt").clicked(action1).width(width1).height(ROW_BUTTON_HEIGHT));
        row.add(button(text2).style("flatt").clicked(action2).width(WIDTH - width1 - 4f).height(ROW_BUTTON_HEIGHT).padLeft(4f));
        return row.padTop(2f);
    }

    private static void teamAction(Player p, String action, MenuResult result) {
        if (action == null) return;
        int split = action.indexOf(':');
        String verb = split < 0 ? action : action.substring(0, split);
        String arg = split < 0 ? "" : action.substring(split + 1);

        switch (verb) {
            case "join" -> requestJoin(p, teamById(arg));
            case "unrequest" -> cancelRequest(p, teamById(arg));
            case "accept" -> accept(p, playerByUuid(arg));
            case "deny" -> deny(p, playerByUuid(arg));
            case "kick" -> kick(p, playerByUuid(arg));
            case "lead" -> makeLeader(p, playerByUuid(arg));
            case "leave" -> leave(p);
            case "surrender" -> {
                if (isLeader(p)) confirmingSurrender.add(p.uuid());
            }
            case "surrender-no" -> confirmingSurrender.remove(p.uuid());
            case "surrender-yes" -> {
                confirmingSurrender.remove(p.uuid());
                if (isLeader(p)) TeamDestroyTracker.surrenderTeam(p.team());
            }
            default -> {
            }
        }
        showTeamPanel(p);
        refreshTeamPanels(p);
    }

    private static void requestJoin(Player p, Team team) {
        if (team == null || team == p.team() || !TeamInfo.isPlayerTeam(team) || !team.active()) return;
        Player leader = Ui.onlineLeader(team);
        if (leader == null) return;

        Cache.teamRequests.put(p.uuid(), leader.uuid());
        p.sendMessage(local(p, "joinMenuSentRequest") + " " + leader.name);
        leader.sendMessage(p.name + " " + local(leader, "joinMenuGotRequest"));
        Call.infoToast(leader.con, local(leader, "uiToastRequest", p.name), 4f);
    }

    private static void cancelRequest(Player p, Team team) {
        Player leader = team == null ? null : Ui.onlineLeader(team);
        if (leader != null && leader.uuid().equals(Cache.teamRequests.get(p.uuid()))) {
            Cache.teamRequests.remove(p.uuid());
        }
    }

    private static void accept(Player p, Player found) {
        if (found == null || !isLeader(p) || !hasRequest(found, p)) return;
        Cache.teamRequests.remove(found.uuid());
        if (found.team() == p.team()) return;

        Team oldTeam = found.team();
        boolean wasLeader = isLeader(found);
        found.team(p.team());
        if (found.unit() != null) found.unit().kill();

        p.sendMessage(local(p, "teamRequestsAccept") + " " + found.name);
        found.sendMessage(local(found, "teamRequestsAccepted") + " " + p.name);
        Call.infoToast(found.con, local(found, "uiToastJoined", Ui.colored(p.team(), p.name)), 4f);
        // The leader of the old team left it: an online teammate takes over,
        // and if nobody from that team is online, the team surrenders
        if (wasLeader && passLeadership(oldTeam, found) == null) {
            TeamDestroyTracker.surrenderTeam(oldTeam);
        }
    }

    private static void deny(Player p, Player found) {
        if (found == null || !hasRequest(found, p)) return;
        Cache.teamRequests.remove(found.uuid());
        p.sendMessage(local(p, "teamRequestsDeny") + " " + found.name);
        found.sendMessage(local(found, "teamRequestsDenied"));
    }

    private static void kick(Player p, Player target) {
        if (target == null || target == p || !isLeader(p) || target.team() != p.team()) return;
        target.team(Team.derelict);
        if (target.unit() != null) target.unit().kill();
        p.sendMessage(local(p, "kickMenuKick") + " " + target.name);
        target.sendMessage(local(target, "kickMenuKicked"));
    }

    private static void makeLeader(Player p, Player target) {
        if (target == null || target == p || !isLeader(p) || target.team() != p.team()) return;
        TeamInfo info = Cache.teamsInfo.get(p.team());
        if (info == null) return;
        setLeader(info, target);
        p.sendMessage(local(p, "SetLeaderMenuTransfer") + " " + target.name);
        target.sendMessage(local(target, "SetLeaderMenuTransferred"));
    }

    private static void leave(Player p) {
        Team team = p.team();
        if (!TeamInfo.isPlayerTeam(team)) return;
        if (isLeader(p) && passLeadership(team, p) == null) return;

        p.team(Team.derelict);
        if (p.unit() != null) p.unit().kill();
        p.sendMessage(local(p, "uiLeftTeam"));
    }

    public static Player passLeadership(Team team, Player except) {
        TeamInfo info = Cache.teamsInfo.get(team);
        if (info == null) return null;

        Seq<Player> candidates = new Seq<>();
        Groups.player.each(p -> p != except && p.team() == team, candidates::add);
        if (candidates.isEmpty()) return null;

        Player newLeader = candidates.random();
        setLeader(info, newLeader);
        newLeader.sendMessage(local(newLeader, "teamLeaderLeftMessage"));
        return newLeader;
    }

    private static void setLeader(TeamInfo info, Player newLeader) {
        String oldLeader = info.getLeaderUuid();
        info.setLeader(newLeader);
        if (oldLeader == null) return;
        for (var entry : Cache.teamRequests) {
            if (oldLeader.equals(entry.value)) Cache.teamRequests.put(entry.key, newLeader.uuid());
        }
    }

    private static boolean isLeader(Player p) {
        TeamInfo info = Cache.teamsInfo.get(p.team());
        return info != null && info.isLeader(p);
    }

    private static boolean hasRequest(Player requester, Player leader) {
        return leader.uuid().equals(Cache.teamRequests.get(requester.uuid()));
    }

    private static Seq<Player> members(Team team) {
        Seq<Player> list = new Seq<>();
        Groups.player.each(m -> m.team() == team, list::add);
        TeamInfo info = Cache.teamsInfo.get(team);
        if (info != null) list.sort(m -> info.isLeader(m) ? 0 : 1);
        return list;
    }

    private static Seq<Player> requesters(Player leader) {
        Seq<Player> list = new Seq<>();
        Groups.player.each(r -> leader.uuid().equals(Cache.teamRequests.get(r.uuid())), list::add);
        return list;
    }

    private static Seq<Team> otherTeams(Player p) {
        Seq<Team> list = new Seq<>();
        for (var data : state.teams.getActive()) {
            if (data.team != p.team() && TeamInfo.isPlayerTeam(data.team) && Ui.onlineLeader(data.team) != null) {
                list.add(data.team);
            }
        }
        return list;
    }

    private static Team teamById(String id) {
        int i = Strings.parseInt(id, -1);
        return i >= 0 && i < Team.all.length ? Team.all[i] : null;
    }

    private static Player playerByUuid(String uuid) {
        return Groups.player.find(o -> o.uuid().equals(uuid));
    }

    public static void showLeaderBoard(Player p) {
        LeaderBoardManager.getTop(10, top -> LeaderBoardManager.getPlayerStats(p.uuid(), own -> {
            if (!p.isAdded()) return; // left while the queries were running
            leaderboardMenu.show(p, local(p, "leaderboardTitle"), table()
                    .add(pane().id("board").align("top").width(Ui.PANE_WIDTH).growY().add(leaderboardContent(p, top, own))).row()
                    .add(button(local(p, "leaderboardUpdate")).icon("refresh").clicked("refresh").width(WIDTH).height(BUTTON_HEIGHT).padTop(6f)));
        }));
    }

    private static final float PLACE_WIDTH = 50f, POINTS_WIDTH = 80f;

    private static TableBuilder leaderboardContent(Player p, List<LeaderBoardManager.Player> top, LeaderBoardManager.Player own) {
        float nameWidth = WIDTH - PLACE_WIDTH - POINTS_WIDTH;
        TableBuilder t = table();
        t.add(defaults().padTop(3f).padBottom(3f));
        t.add(label("[lightgray]#").labelAlign("left").width(PLACE_WIDTH));
        t.add(label("[lightgray]" + local(p, "uiColPlayer")).labelAlign("left").width(nameWidth));
        t.add(label("[lightgray]" + local(p, "uiColPoints")).labelAlign("right").width(POINTS_WIDTH)).row();

        if (top.isEmpty()) {
            t.add(label("[lightgray]" + local(p, "leaderboardEmptyMessage")).wrap().width(WIDTH).colspan(3)).row();
        }
        for (LeaderBoardManager.Player entry : top) {
            String place = switch (entry.position) {
                case 1 -> "[gold]";
                case 2 -> "[lightgray]";
                case 3 -> "[#cd7f32]";
                default -> "[white]";
            };
            boolean me = entry.uuid.equals(p.uuid());
            t.add(label(place + "#" + entry.position).labelAlign("left").width(PLACE_WIDTH));
            t.add(label((me ? "[accent]" : "") + entry.name).wrap().labelAlign("left").width(nameWidth));
            t.add(label("[green]" + entry.points).labelAlign("right").width(POINTS_WIDTH)).row();
        }

        t.add(label(own == null
                ? "[lightgray]" + local(p, "leaderboardPositionNotFound")
                : "[accent]" + local(p, "uiYourPlace", own.position, own.points)).wrap().width(WIDTH).colspan(3).padTop(12f)).row();
        return t;
    }


    public static void showGuide(Player p) {
        showGuidePage(p, 0);
    }

    private static void showGuidePage(Player p, int page) {
        guidePages.put(p.uuid(), page);
        guideMenu.show(p, null, guidePage(p, page));
    }

    private static TableBuilder guidePage(Player p, int page) {
        TableBuilder t = table();
        if (page == 0) {
            t.add(label(local(p, "WelcomeTitle")).wrap().labelAlign("center").width(WIDTH).padBottom(10f)).row();
            t.add(button(local(p, "ShowGuide")).icon("book").clicked("guide").width(WIDTH).height(BUTTON_HEIGHT).padTop(2f).padBottom(2f)).row();
            t.add(button(local(p, "OpenDiscordButton")).icon("discord").clicked("discord").width(WIDTH).height(BUTTON_HEIGHT).padTop(2f).padBottom(2f)).row();
            t.add(button(local(p, "uiBtnPlay")).icon("play").clicked("close").width(WIDTH).height(BUTTON_HEIGHT).padTop(2f).padBottom(2f)).row();
            return t;
        }

        String[] keys = GUIDE[page - 1];
        StringBuilder text = new StringBuilder();
        for (int i = 1; i < keys.length; i++) {
            if (i > 1) text.append('\n');
            text.append(local(p, keys[i]));
        }
        t.add(label("[accent]" + local(p, keys[0])).wrap().labelAlign("left").width(WIDTH).padBottom(6f)).row();
        t.add(label(text.toString()).wrap().labelAlign("left").width(WIDTH)).row();

        float navButton = 110f;
        TableBuilder nav = table();
        nav.add(button(local(p, "uiBtnBack")).clicked("back").width(navButton).height(BUTTON_HEIGHT));
        nav.add(label("[lightgray]" + page + " / " + GUIDE.length).labelAlign("center").width(WIDTH - 2 * navButton - 8f));
        nav.add(page < GUIDE.length
                ? button(local(p, "NextMenuButton")).clicked("next").width(navButton).height(BUTTON_HEIGHT)
                : button(local(p, "uiBtnPlay")).clicked("close").width(navButton).height(BUTTON_HEIGHT));
        t.add(nav.padTop(12f)).row();
        return t;
    }

    private static void guideAction(Player p, String action, MenuResult result) {
        int page = guidePages.get(p.uuid(), 0);
        switch (action == null ? "" : action) {
            case "guide" -> showGuidePage(p, 1);
            case "next" -> showGuidePage(p, Math.min(page + 1, GUIDE.length));
            case "back" -> showGuidePage(p, Math.max(page - 1, 0));
            case "discord" -> Call.openURI(p.con, DISCORD);
            case "close" -> {
                guideMenu.hide(p);
                guidePages.remove(p.uuid(), 0);
            }
            default -> showGuidePage(p, page);
        }
    }
}
