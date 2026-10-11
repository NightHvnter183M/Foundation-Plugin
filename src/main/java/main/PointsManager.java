package main;

import arc.struct.ObjectIntMap;
import arc.struct.Seq;
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

    ///Holding rating of every alive team
    public static void updateRatings() {
        for (var entry : Cache.teamsInfo) {
            if (TeamDestroyTracker.hasMainCore(entry.key)) {
                entry.value.setRating(calculateTeamRating(entry.key));
            }
        }
    }

    public static void pointCounter(Team teamDestroyer, Team teamVictim) {
        if (teamDestroyer == null || teamDestroyer == teamVictim) return;
        if (teamDestroyer == Team.derelict) return;

        float pointsDestroyer = Math.max(1f, calculateTeamRating(teamDestroyer));
        // By now the victim has already lost its main cores, so use the rating it had while alive
        float pointsVictim = calculateTeamRating(teamVictim);
        TeamInfo victimInfo = Cache.teamsInfo.get(teamVictim);
        if (victimInfo != null) {
            pointsVictim = Math.max(pointsVictim, victimInfo.getRating());
        }

        int players = Math.max(1, teamDestroyer.data().players.size);
        int finalPoints = Math.round(pointsVictim / pointsDestroyer * 100f / players);

        Seq<LeaderBoardManager.PointsEntry> entries = new Seq<>();
        Groups.player.each(p -> p.team() == teamDestroyer, p -> {
            p.sendMessage((Localisation.local(p, "teamEliminationMessage")) + " " + finalPoints);
            entries.add(new LeaderBoardManager.PointsEntry(p.uuid(), p.name(), finalPoints));
        });
        LeaderBoardManager.addPoints(entries);
    }

    private static float calculateTeamRating(Team team) {
        float points = 0f;
        //Calculating core's points
        for (var core : team.cores()) {
            if (core.block == Blocks.coreNucleus) points += 10f;
            else if (core.block == Blocks.coreFoundation) points += 5f;
            else if (core.block == Blocks.coreShard) points += 1f;
        }
        //Calculating unit's points (the team keeps its own unit list, no need to scan every unit)
        for (Unit u : team.data().units) {
            points += unitScores.get(u.type, 0);
        }
        return points;
    }
}
