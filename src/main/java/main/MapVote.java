package main;

import arc.Core;
import arc.Events;
import arc.struct.ObjectIntMap;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Iconc;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.ui.builder.UiBuilder.TableBuilder;

import static main.Localisation.local;
import static mindustry.ui.builder.UiBuilder.*;

public class MapVote {
    private static final float DURATION = 20f;
    private static final float WIDTH = Ui.WIDTH;

    private static final Seq<Map> MapOptions = new Seq<>();
    private static final ObjectIntMap<String> votes = new ObjectIntMap<>();
    private static boolean IsVoting = false;
    private static long endsAt;
    private static Timer.Task finishTask, endTask;
    private static BuilderMenu menu;

    public static void init(){
        menu = new BuilderMenu(false, false, (player, action, result) -> {
            if (!IsVoting || action == null || !action.startsWith("map:")) return;
            int choice = Strings.parseInt(action.substring("map:".length()), -1);
            if (choice < 0 || choice >= MapOptions.size) return;

            votes.put(player.uuid(), choice);
            player.sendMessage(local(player, "voteRestartMessage") + " " + MapOptions.get(choice).name());
            show(player);
            Groups.player.each(p -> p != player, p -> menu.update(p, "maps", mapButtons(p)));
        });
        Events.on(EventType.PlayerJoin.class, e -> Core.app.post(() -> {
            if (IsVoting && e.player.isAdded()) show(e.player);
        }));
    }

    public static void start(){
        if(IsVoting) return;
        IsVoting = true;
        votes.clear();
        MapOptions.clear();
        Seq<Map> allMaps = Vars.maps.customMaps();
        if (allMaps.isEmpty()) allMaps = Vars.maps.all();
        allMaps = allMaps.copy().shuffle();
        for (int i = 0; i < Math.min(6, allMaps.size); i++){
            MapOptions.add(allMaps.get(i));
        }
        endsAt = Time.millis() + (long) (DURATION * 1000);
        Groups.player.each(MapVote::show);
        finishTask = Timer.schedule(MapVote::finish, DURATION);
    }

    private static void show(Player p) {
        menu.show(p, local(p, "roundOverTitle"), table()
                .add(label(local(p, "roundOverMessage")).wrap().labelAlign("center").width(WIDTH).padBottom(8f)).row()
                .add(mapButtons(p).id("maps").width(WIDTH)));
    }

    private static TableBuilder mapButtons(Player p) {
        TableBuilder t = table();
        t.add(defaults().width(WIDTH).height(50f).padTop(2f).padBottom(2f));
        int[] counts = counts();
        int mine = votes.get(p.uuid(), -1);
        for (int i = 0; i < MapOptions.size; i++) {
            t.add(button(MapOptions.get(i).name() + "   [lightgray]" + Iconc.players + " " + counts[i])
                    .style("togglet").checked(i == mine).clicked("map:" + i)).row();
        }
        return t;
    }

    private static int[] counts() {
        int[] counts = new int[MapOptions.size];
        for (ObjectIntMap.Entry<String> vote : votes) {
            if (vote.value >= 0 && vote.value < counts.length) counts[vote.value]++;
        }
        return counts;
    }

    public static String hudLine(Player p) {
        if (!IsVoting) return null;
        return local(p, "hudVoteMap", Math.max(0, (int) Math.ceil((endsAt - Time.millis()) / 1000f)));
    }

    private static void finish(){
        if (!IsVoting) return;
        IsVoting = false;
        menu.hideAll();

        Map winner = null;
        if (!MapOptions.isEmpty()) {
            int[] counts = counts();
            int winnerIndex = 0;
            for (int i = 1; i < counts.length; i++) {
                if (counts[i] > counts[winnerIndex]) winnerIndex = i;
            }
            winner = MapOptions.get(winnerIndex);
        }

        Map nextMap = winner;
        if (nextMap != null) Groups.player.each(p -> p.sendMessage(local(p, "uiNextMap", nextMap.name())));
        endTask = Timer.schedule(() -> {
            // no maps at all: let the server pick the next one itself
            if (nextMap != null) Vars.maps.setNextMapOverride(nextMap);
            Events.fire(new EventType.GameOverEvent(Team.derelict));
            Groups.player.each(p -> p.team(Team.derelict));
        }, 3);
    }

    public static void reset(){
        if (finishTask != null) finishTask.cancel();
        if (endTask != null) endTask.cancel();
        finishTask = null;
        endTask = null;
        if (IsVoting) menu.hideAll();
        IsVoting = false;
        votes.clear();
        MapOptions.clear();
    }
}
