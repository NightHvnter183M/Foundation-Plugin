package main;

import arc.util.Time;
import arc.util.Timer;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.ui.builder.UiBuilder;

import static main.Localisation.local;
import static mindustry.ui.builder.UiBuilder.*;

public class Restart {

    private static final int VOTE_TIME = 300;
    private static boolean IsRestartVoting = false;
    private static long endsAt;
    private static Timer.Task voteTask;
    private static BuilderMenu menu;

    public static void init() {
        menu = new BuilderMenu(true, false, (player, action, result) -> {
            if ("yes".equals(action)) AddVotes(player);
        });
    }

    public static void StartRestartVote(Player player){
        if(IsRestartVoting) return;
        IsRestartVoting = true;
        Cache.restartVotes.clear();
        Cache.restartVotes.add(player.uuid());
        endsAt = Time.millis() + VOTE_TIME * 1000L;
        Groups.player.each(p -> {
            p.sendMessage(local(p, "uiRestartStarted", player.name));
            if (p != player) show(p, player);
        });
        voteTask = Timer.schedule(() -> {
            if (IsRestartVoting) {
                IsRestartVoting = false;
                menu.hideAll();
                Cache.restartVotes.clear();
                Groups.player.each(p -> p.sendMessage(local(p, "uiRestartFailed")));
            }
        }, VOTE_TIME);
    }

    private static void show(Player p, Player initiator) {
        menu.show(p, local(p, "uiRestartTitle"), table()
                .add(label(local(p, "uiRestartMessage", initiator.name)).wrap().labelAlign("center").width(Ui.WIDTH).padBottom(6f)).row()
                .add(table().id("count").add(countLabel(p)).padBottom(8f)).row()
                .add(button(local(p, "uiBtnVoteYes")).icon("ok").clicked("yes").width(Ui.WIDTH).height(48f)));
    }

    private static LabelBuilder countLabel(Player p) {
        return label("[lightgray]" + local(p, "uiRestartCount", Cache.restartVotes.size, votesNeeded())).labelAlign("center").width(Ui.WIDTH);
    }

    private static int votesNeeded() {
        return (int) (Groups.player.size() * 0.6) + 1;
    }

    public static void AddVotes(Player player) {
        if (!IsRestartVoting){
            StartRestartVote(player);
            return;
        }
        if(Cache.restartVotes.contains(player.uuid())){
            player.sendMessage(local(player, "restartVoteAgain"));
            return;
        }
        Cache.restartVotes.add(player.uuid());
        int votesNeeded = votesNeeded();
        int currentVotes = Cache.restartVotes.size;
        Groups.player.each(p -> p.sendMessage(local(p, "uiRestartVoted", player.name, currentVotes, votesNeeded)));
        // voted with /restart while the window was open
        menu.hide(player);
        Groups.player.each(p -> menu.update(p, "count", table().add(countLabel(p))));
        if(currentVotes >= votesNeeded) DoingRestart();
    }

    public static String hudLine(Player p) {
        if (!IsRestartVoting) return null;
        int seconds = Math.max(0, (int) Math.ceil((endsAt - Time.millis()) / 1000f));
        return local(p, "hudVoteRestart", Cache.restartVotes.size, votesNeeded(), seconds / 60, seconds % 60);
    }

    public static void DoingRestart(){
        IsRestartVoting = false;
        if (voteTask != null) voteTask.cancel();
        menu.hideAll();
        Cache.playerTeams.clear();
        MapVote.start();
        Groups.player.each(p -> p.team(Team.all[0]));
    }

    public static void reset(){
        if (IsRestartVoting) menu.hideAll();
        IsRestartVoting = false;
        if (voteTask != null) voteTask.cancel();
        voteTask = null;
        Cache.restartVotes.clear();
    }

}
