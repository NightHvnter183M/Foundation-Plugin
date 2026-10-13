package main;

import arc.Core;
import arc.Events;
import arc.struct.ObjectIntMap;
import arc.util.Time;
import arc.util.Timer;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.ui.builder.UiBuilder.TableBuilder;

import static main.Localisation.local;
import static mindustry.Vars.state;
import static mindustry.ui.builder.UiBuilder.*;

public class PayloadVote {

    public static final boolean DEFAULT_ENABLED = true;
    private static final float START_DELAY = 5f;
    private static final float DURATION = 30f;
    private static final float WIDTH = Ui.WIDTH;

    private static BuilderMenu menu;
    private static boolean voting;
    private static long endsAt;
    private static final ObjectIntMap<String> votes = new ObjectIntMap<>();
    private static Timer.Task startTask, finishTask;

    public static void init() {
        menu = new BuilderMenu(false, false, (player, action, result) -> {
            if (!voting || (!"yes".equals(action) && !"no".equals(action))) return;

            votes.put(player.uuid(), "yes".equals(action) ? 1 : 0);
            int[] count = count();
            player.sendMessage(local(player, "payloadVoteCounted", count[0], count[1]));
            if (Groups.player.count(p -> votes.containsKey(p.uuid())) >= Groups.player.size()) {
                finish();
                return;
            }
            show(player);
            Groups.player.each(p -> p != player, p -> menu.update(p, "choice", choice(p)));
        });

        Events.on(EventType.PlayerJoin.class, event -> Core.app.post(() -> {
            if (voting && event.player.isAdded()) show(event.player);
        }));
        Events.on(EventType.ResetEvent.class, event -> cancel());
    }

    public static void roundStarted() {
        cancel();
        state.rules.unitPayloadUpdate = DEFAULT_ENABLED;
        startTask = Timer.schedule(PayloadVote::start, START_DELAY);
    }

    public static void command(Player player) {
        if (voting) {
            show(player);
        } else {
            player.sendMessage(local(player, state.rules.unitPayloadUpdate ? "payloadStatusOn" : "payloadStatusOff"));
        }
    }

    public static String hudLine(Player p) {
        if (!voting) return null;
        int[] count = count();
        return local(p, "hudVotePayload", count[0], count[1], Math.max(0, (int) Math.ceil((endsAt - Time.millis()) / 1000f)));
    }

    private static void start() {
        startTask = null;
        if (!state.isGame()) return;

        voting = true;
        votes.clear();
        endsAt = Time.millis() + (long) (DURATION * 1000);
        Groups.player.each(p -> {
            p.sendMessage(local(p, "payloadVoteStarted"));
            show(p);
        });
        finishTask = Timer.schedule(PayloadVote::finish, DURATION);
    }

    private static void show(Player p) {
        menu.show(p, local(p, "payloadVoteTitle"), table()
                .add(label(local(p, "payloadVoteMessage")).wrap().labelAlign("center").width(WIDTH).padBottom(8f)).row()
                .add(choice(p).id("choice").width(WIDTH)));
    }

    private static TableBuilder choice(Player p) {
        int[] count = count();
        int mine = votes.get(p.uuid(), -1);
        TableBuilder t = table();
        t.add(defaults().width(WIDTH / 2f - 2f).height(54f));
        t.add(button(local(p, "payloadVoteYes") + "   [lightgray]" + count[0]).style("togglet").checked(mine == 1).clicked("yes"));
        t.add(button(local(p, "payloadVoteNo") + "   [lightgray]" + count[1]).style("togglet").checked(mine == 0).clicked("no").padLeft(4f));
        return t;
    }

    private static void finish() {
        if (!voting) return;
        voting = false;
        if (finishTask != null) finishTask.cancel(); // when everyone voted before the time ran out
        finishTask = null;
        menu.hideAll();

        int[] count = count();
        votes.clear();
        boolean enabled = count[0] == count[1] ? DEFAULT_ENABLED : count[0] > count[1];

        state.rules.unitPayloadUpdate = enabled;
        Call.setRules(state.rules);

        String key = enabled ? "payloadVoteResultOn" : "payloadVoteResultOff";
        Groups.player.each(p -> p.sendMessage(local(p, key, count[0], count[1])));
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
        if (voting) menu.hideAll();
        voting = false;
        votes.clear();
    }
}
