package main;

import arc.util.Align;
import mindustry.content.Blocks;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Iconc;
import mindustry.gen.Player;
import mindustry.world.blocks.storage.CoreBlock;

import static main.Localisation.local;
import static mindustry.Vars.state;
///A special class for hud
public class Hud {

    private static final String PANEL_ID = "foundation-panel";
    private static final int PANEL_ALIGN = Align.topRight, PANEL_TOP = 160, PANEL_RIGHT = 4;
    private static final float PANEL_DURATION = 2.5f;

    public static void update() {
        if (!state.isGame()) return;
        Groups.player.each(p -> Call.infoPopup(p.con, panel(p), PANEL_ID, PANEL_DURATION, PANEL_ALIGN, PANEL_TOP, 0, 0, PANEL_RIGHT));
    }

    private static String panel(Player p) {
        StringBuilder sb = new StringBuilder();
        sb.append("[#5F9EA0]Foundation PvP[]\n");
        sb.append("[lightgray]").append(local(p, "hudRoundEnds")).append(" [white]").append(Ui.time(Resources.maxTime));
        sb.append("\n[lightgray]").append(local(p, "hudPayload")).append(' ')
                .append(state.rules.unitPayloadUpdate ? "[green]" + local(p, "hudOn") : "[scarlet]" + local(p, "hudOff"));

        String vote = voteLine(p);
        if (vote != null) sb.append("\n[accent]").append(vote);

        Team team = p.team();
        if (TeamInfo.isPlayerTeam(team) && team.active()) {
            sb.append("\n[lightgray]").append(local(p, "hudMaxUnits")).append(" [white]").append(Units.getCap(team));
            sb.append('\n').append(cores(team));
        }
        return sb.toString();
    }

    private static String voteLine(Player p) {
        String line = MapVote.hudLine(p);
        if (line == null) line = PayloadVote.hudLine(p);
        if (line == null) line = Restart.hudLine(p);
        return line;
    }

    private static String cores(Team team) {
        int nucleus = 0, foundation = 0, shard = 0;
        for (CoreBlock.CoreBuild core : team.cores()) {
            if (core.block == Blocks.coreNucleus) nucleus++;
            else if (core.block == Blocks.coreFoundation) foundation++;
            else if (core.block == Blocks.coreShard) shard++;
        }
        return "[white]" + Iconc.blockCoreNucleus + " " + nucleus + "   " + Iconc.blockCoreFoundation + " " + foundation
                + "   " + Iconc.blockCoreShard + " " + shard;
    }
}
