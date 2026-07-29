package main;

import arc.struct.ObjectIntMap;
import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

public class PointsManager {

    private static final ObjectIntMap<UnitType> unitScores = new ObjectIntMap<>();
    static {
        // t1 - 1 point
        for (var u : new UnitType[]{UnitTypes.dagger, UnitTypes.nova, UnitTypes.crawler, UnitTypes.stell, UnitTypes.merui, UnitTypes.elude, UnitTypes.flare, UnitTypes.risso, UnitTypes.retusa}) {
            unitScores.put(u, 1);
        }
        // t2 - 2 points
        for (var u : new UnitType[]{UnitTypes.mace, UnitTypes.pulsar, UnitTypes.atrax, UnitTypes.locus, UnitTypes.cleroi, UnitTypes.horizon, UnitTypes.avert, UnitTypes.minke, UnitTypes.oxynoe}) {
            unitScores.put(u, 2);
        }
        // t3 - 4 points
        for (var u : new UnitType[]{UnitTypes.fortress, UnitTypes.quasar, UnitTypes.spiroct, UnitTypes.precept, UnitTypes.anthicus, UnitTypes.zenith, UnitTypes.mega, UnitTypes.obviate, UnitTypes.bryde, UnitTypes.cyerce}) {
            unitScores.put(u, 4);
        }
        // t4 - 8 points
        for (var u : new UnitType[]{UnitTypes.scepter, UnitTypes.vela, UnitTypes.arkyid, UnitTypes.vanquish, UnitTypes.tecta, UnitTypes.antumbra, UnitTypes.quad, UnitTypes.quell, UnitTypes.sei, UnitTypes.aegires}) {
            unitScores.put(u, 8);
        }
        // t5 - 16 points
        for (var u : new UnitType[]{UnitTypes.reign, UnitTypes.corvus, UnitTypes.toxopid, UnitTypes.conquer, UnitTypes.collaris, UnitTypes.eclipse, UnitTypes.oct, UnitTypes.disrupt, UnitTypes.omura, UnitTypes.navanax}) {
            unitScores.put(u, 16);
        }
    }
    public void pointCounter(Team teamDestroyer, Team teamVictim) {
        if (teamDestroyer == teamVictim) return;
        if (teamDestroyer == Team.derelict) return;
        float pointsDestroyer = calculateTeamRating(teamDestroyer);
        float pointsVictim = calculateTeamRating(teamVictim);
        float ratio = pointsVictim / pointsDestroyer;
        int finalPoints = Math.abs(Math.round(ratio * 100f / teamDestroyer.data().players.size));
        Groups.player.each(p -> p.team() == teamDestroyer, p -> {
            p.sendMessage((Localisation.local(p, "teamEliminationMessage")) + " " + finalPoints);
            LeaderBoardManager.addPoints(p.uuid(), p.name(), finalPoints);
        });
    }

    private float calculateTeamRating(Team team) {
        float points = 0f;
        //Calculating core's points
        int valueAtoms = team.cores().count(c -> c.block == Blocks.coreNucleus);
        int valueFoundation = team.cores().count(c -> c.block == Blocks.coreFoundation);
        int valueShard = team.cores().count(c -> c.block == Blocks.coreShard);
        points += valueAtoms * 10f;
        points += valueFoundation * 5f;
        points += valueShard * 1f;
        //Calculating unit's points
        for (Unit u : Groups.unit) {
            if (u.team == team) {
                points += unitScores.get(u.type, 0);
            }
        }
        return points;
    }
}
