package main;

import arc.Events;
import arc.struct.ObjectIntMap;
import arc.struct.Seq;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.maps.Map;
import mindustry.ui.Menus;
import static main.Cache.votes;
import static main.Cache.mapVoteMenuId;

public class MapVote {
    private static final Seq<Map> MapOptions = new Seq<>();
    private static boolean IsVoting = false;
    private static Timer.Task finishTask, endTask;

    public static void init(){
        mapVoteMenuId = Menus.registerMenu((player, selection) -> {
            if (!IsVoting || selection < 0 || selection >= MapOptions.size) return;
            // one vote per player: a client can send this menu's choice any number of times
            votes.put(player.uuid(), selection);
            player.sendMessage(Localisation.local(player, "voteRestartMessage") + " " + MapOptions.get(selection).name());
        });
    }
    public static void start(){
        if(IsVoting) return;
        IsVoting = true;
        votes.clear();
        MapOptions.clear();
        Seq<Map> allMaps = Vars.maps.customMaps();
        if (allMaps.isEmpty()) allMaps = Vars.maps.all().copy();
        allMaps.shuffle();
        for (int i = 0; i < Math.min(6, allMaps.size); i++){
            MapOptions.add(allMaps.get(i));
        }
        String[][] buttons = new String[MapOptions.size][1];
        for (int i = 0; i < MapOptions.size; i++){
            buttons[i][0] = MapOptions.get(i).name();
        }
        Groups.player.each(player -> {
            Call.menu(player.con, mapVoteMenuId, Localisation.local(player, "roundOverTitle"), Localisation.local(player, "roundOverMessage"), buttons);
        });
        finishTask = Timer.schedule(MapVote::finish, 20);
    }

    private static void finish(){
        IsVoting = false;
        Map winner = null;
        if (!MapOptions.isEmpty()) {
            int[] counts = new int[MapOptions.size];
            for (ObjectIntMap.Entry<String> vote : votes) {
                if (vote.value >= 0 && vote.value < counts.length) counts[vote.value]++;
            }
            int winnerIndex = 0;
            for (int i = 1; i < counts.length; i++) {
                if (counts[i] > counts[winnerIndex]) winnerIndex = i;
            }
            winner = MapOptions.get(winnerIndex);
        }

        Map nextMap = winner;
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
        IsVoting = false;
        votes.clear();
        MapOptions.clear();
    }
}