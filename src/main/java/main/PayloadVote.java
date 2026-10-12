package main;

import arc.Events;
import arc.struct.ObjectIntMap;
import arc.util.Timer;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.ui.Menus;

import static mindustry.Vars.state;

public class PayloadVote {

    public static final boolean DEFAULT_ENABLED = true;
    private static final float START_DELAY = 5f;
    private static final float DURATION = 30f;

    private static int menuId;
    private static boolean voting;
    private static final ObjectIntMap<String> votes = new ObjectIntMap<>();
    private static Timer.Task startTask, finishTask;

    public static void init() {
        menuId = Menus.registerMenu((player, selection) -> {
            if (!voting || (selection != 0 && selection != 1)) return;

            votes.put(player.uuid(), selection == 0 ? 1 : 0);
            int[] count = count();
            player.sendMessage(Localisation.local(player, "payloadVoteCounted", count[0], count[1]));
            if (Groups.player.count(p -> votes.containsKey(p.uuid())) >= Groups.player.size()) {
                finish();
            }
        });

        Events.on(EventType.PlayerJoin.class, event -> {
            if (voting) showMenu(event.player);
        });
        Events.on(EventType.ResetEvent.class, event -> cancel());
    }

    public static void roundStarted() {
        cancel();
        state.rules.unitPayloadUpdate = DEFAULT_ENABLED;
        startTask = Timer.schedule(PayloadVote::start, START_DELAY);
    }

    public static void command(Player player) {
        if (voting) {
            showMenu(player);
        } else {
            player.sendMessage(Localisation.local(player, state.rules.unitPayloadUpdate ? "payloadStatusOn" : "payloadStatusOff"));
        }
    }

    private static void start() {
        startTask = null;
        if (!state.isGame()) return;

        voting = true;
        votes.clear();
        Groups.player.each(p -> {
            p.sendMessage(Localisation.local(p, "payloadVoteStarted"));
            showMenu(p);
        });
        finishTask = Timer.schedule(PayloadVote::finish, DURATION);
    }

    private static void showMenu(Player p) {
        Call.menu(p.con, menuId, Localisation.local(p, "payloadVoteTitle"), Localisation.local(p, "payloadVoteMessage"),
                new String[][]{{Localisation.local(p, "payloadVoteYes"), Localisation.local(p, "payloadVoteNo")}});
    }

    private static void finish() {
        if (!voting) return;
        voting = false;
        if (finishTask != null) finishTask.cancel(); // when everyone voted before the time ran out
        finishTask = null;

        int[] count = count();
        votes.clear();
        boolean enabled = count[0] == count[1] ? DEFAULT_ENABLED : count[0] > count[1];

        state.rules.unitPayloadUpdate = enabled;
        Call.setRules(state.rules);

        String key = enabled ? "payloadVoteResultOn" : "payloadVoteResultOff";
        Groups.player.each(p -> p.sendMessage(Localisation.local(p, key, count[0], count[1])));
    }

    private static int[] count() {
        int yes = 0, no = 0;
        for (ObjectIntMap.Entry<String> vote : votes) {
            if (vote.value == 1) yes++;
            else no++;
        }
        return new int[]{yes, no};
    }

    private static void cancel() {
        if (startTask != null) startTask.cancel();
        if (finishTask != null) finishTask.cancel();
        startTask = null;
        finishTask = null;
        voting = false;
        votes.clear();
    }
}
