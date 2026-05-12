package main;

import arc.Events;
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
    private static Seq<Map> mapOptions = new Seq<>();
    private static boolean isvoting = false;
    public static void init(){
        mapVoteMenuId = Menus.registerMenu((player, selection) -> {
            if (selection == -1 || !isvoting) return;
            votes.put(selection, votes.get(selection, 0) + 1);
            player.sendMessage("Voted for " + mapOptions.get(selection).name());
        });
    }
    public static void start(){
        if(isvoting) return;
        isvoting = true;
        votes.clear();
        mapOptions.clear();
        Seq<Map> allMaps = Vars.maps.customMaps().shuffle();
        for (int i = 0; i < Math.min(3, allMaps.size); i++){
            mapOptions.add(allMaps.get(i));
        }
        String[][] buttons = new String[mapOptions.size][1];
        for (int i = 0; i < mapOptions.size; i++){
            buttons[i][0] = mapOptions.get(i).name();
        }
        Call.menu(mapVoteMenuId, "Round is over",  "Choose next map: ", buttons);
        Timer.schedule(() -> finish(), 20);
    }

    private static void finish(){
        isvoting = false;
        int winnerindex = 0;
        int maxvotes = -1;
        for (int i = 0; i < mapOptions.size; i++){
            int count = votes.get(i, 0);
            if (count > maxvotes){
                maxvotes = count;
                winnerindex = i;
            }
        }

        Map winner = mapOptions.get(winnerindex);
        Timer.schedule(() -> {
            Vars.maps.setNextMapOverride(winner);
            Events.fire(new EventType.GameOverEvent(Team.all[0]));
            Groups.player.each(p -> p.team(Team.all[0]));
        }, 3);
    }
}
