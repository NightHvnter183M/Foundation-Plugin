package main;

import arc.math.geom.Vec2;
import arc.util.Interval;
import mindustry.Vars;
import mindustry.ai.Pathfinder;
import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.entities.Units;
import mindustry.entities.units.AIController;
import mindustry.entities.units.UnitController;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.world.Tile;

public class DynamicCruxAi {

    public static class dynamicCruxAI extends AIController {

        // The centre of the enemy main cores is the same for every crux unit,
        // so it is computed once per stratUpdateTime for all of them.
        private static final Vec2 strategicCenter = new Vec2();
        private static double centerUpdatedAt = -1;
        private static int centerVersion;

        private Teamc currentTarget;
        private Tile spawnPoint;
        private final Vec2 laneDestination = new Vec2();
        private int laneVersion = -1;
        // not called "timer": that would hide AIController.timer, which the base class uses for retargeting
        private final Interval aiTimer = new Interval(1);
        private static final float targetUpdateTime = 30f;
        private static final float idleSearchTime = 15f;
        private static final float stratUpdateTime = 120f;
        private static final float aggRad = 500f;
        private static final float switchMargin = 150f;
        private static final float directApproach = 80f;
        private static final float mLaneAngle = 65f;
        private static final float lBonus = 500f;
        private static final float wLane = 350f;
        private static final float ncBonus = 800f;
        private static final float fBonus = 700f;
        private static final float tBonus = 500f;
        private static final float uBonus = 100f;
        @Override
        public void init() {
            super.init();
            spawnPoint = getClosestSpawner();
            refreshLane();
        }

        // Lanes and straight-line approach don't suit naval units, the vanilla AI handles them better
        @Override
        public boolean useFallback() {
            return unit != null && unit.type.naval;
        }

        @Override
        public AIController fallback() {
            UnitController controller = unit.type.aiController.get();
            return controller instanceof AIController ai ? ai : null;
        }

        @Override
        public void updateMovement() {

            if (unit == null || unit.dead) {
                return;
            }
            if (spawnPoint == null) {
                spawnPoint = getClosestSpawner();
            }
            refreshLane();

            if (!isValidTarget(currentTarget)) {
                currentTarget = null;
            }
            // Searched on a timer even when there is no target: findBestTarget() can scan the whole map
            if (aiTimer.get(0, currentTarget == null ? idleSearchTime : targetUpdateTime)) {
                Teamc candidate = findBestTarget();
                if (currentTarget == null) {
                    currentTarget = candidate;
                } else if (candidate != null && candidate != currentTarget
                        && scoreTarget(candidate) > scoreTarget(currentTarget) + switchMargin) {
                    // an additive margin: "score * 1.3" switched to worse targets when scores were negative
                    currentTarget = candidate;
                }
            }

            if (currentTarget == null) {
                advanceAlongLane();
                return;
            }

            // Weapons are aimed by AIController.updateWeapons() (see findMainTarget below)
            if (unit.within(currentTarget, unit.type.range)) {
                target = currentTarget;
            }

            float attackRange = getAttackRange();
            if (unit.isFlying()) {
                updateFlyingMovement(currentTarget, attackRange);
            } else {
                updateGroundMovement(currentTarget, attackRange);
            }
        }

        // The base AI picks a new main target every 40-90 ticks; keep it on ours while it is in range
        @Override
        public Teamc findMainTarget(float x, float y, float range, boolean air, boolean ground) {
            if (isValidTarget(currentTarget) && currentTarget.within(x, y, range)) {
                return currentTarget;
            }
            return super.findMainTarget(x, y, range, air, ground);
        }

        private static void updateStrategicCenter() {
            double now = Vars.state.tick;
            if (centerUpdatedAt >= 0 && now >= centerUpdatedAt && now - centerUpdatedAt < stratUpdateTime) {
                return;
            }
            centerUpdatedAt = now;
            centerVersion++;

            float totalX = 0f;
            float totalY = 0f;
            int count = 0;
            for (var data : Vars.state.teams.getActive()) {
                if (!TeamInfo.isPlayerTeam(data.team)) {
                    continue;
                }
                for (var core : data.cores) {
                    if (core == null || core.dead()) {
                        continue;
                    }
                    if (core.block == Blocks.coreShard) {
                        continue;
                    }
                    totalX += core.x;
                    totalY += core.y;
                    count++;
                }
            }
            if (count > 0) {

                strategicCenter.set(totalX / count, totalY / count);
                return;
            }
            strategicCenter.set(Vars.world.width() * Vars.tilesize / 2f, Vars.world.height() * Vars.tilesize / 2f);
        }

        private void refreshLane() {
            updateStrategicCenter();
            if (spawnPoint != null && laneVersion != centerVersion) {
                updateLaneDestination();
                laneVersion = centerVersion;
            }
        }

        private void updateLaneDestination() {

            if (spawnPoint == null) {
                return;
            }
            float spawnX = spawnPoint.worldx();
            float spawnY = spawnPoint.worldy();
            float dx = strategicCenter.x - spawnX;
            float dy = strategicCenter.y - spawnY;

            float length = (float)Math.sqrt(dx * dx + dy * dy);

            if (length < 1f) {
                laneDestination.set(strategicCenter);
                return;
            }
            dx /= length;
            dy /= length;
            float advanceDistance = length * 0.55f;
            laneDestination.set(spawnX + dx * advanceDistance, spawnY + dy * advanceDistance);
        }

        private void advanceAlongLane() {

            if (spawnPoint == null) {
                if (!unit.isFlying()) {
                    pathfind(Pathfinder.fieldCore);
                }
                return;
            }
            faceMovement();
            moveTo(laneDestination, 20f);
        }

        private void updateGroundMovement(Teamc goal, float attackRange) {

            faceTarget();
            // moveTo() goes in a straight line and gets stuck on walls, so a distant target is
            // approached through the flow field, which leads around obstacles to the nearest enemy core
            if (unit.within(goal, attackRange + directApproach)) {
                moveTo(goal, attackRange);
            } else {
                pathfind(Pathfinder.fieldCore);
            }
        }

        private void updateFlyingMovement(Teamc goal, float attackRange) {

            if (unit.type == UnitTypes.flare || unit.type == UnitTypes.horizon || unit.type == UnitTypes.quad) {
                moveTo(goal, 0f);
                faceTarget();
                return;
            }

            if (unit.type == UnitTypes.zenith) {
                moveTo(goal, attackRange * 0.7f);
                faceTarget();
                return;
            }

            if (unit.type.circleTarget) {
                float radiusOffset = (unit.id % 5) * 12f;
                float orbitRadius = Math.max(attackRange * 0.75f, 35f) + radiusOffset;
                circle(goal, orbitRadius);
                faceTarget();
            } else {
                moveTo(goal, Math.max(attackRange * 0.85f, 25f));
                faceTarget();
            }
        }

        private float getAttackRange() {

            return Math.max(
                    unit.type.range * 0.82f,
                    20f
            );
        }
        private boolean isEnemyTeam(Team team) {
            return TeamInfo.isPlayerTeam(team) && team != unit.team;
        }
        private boolean isValidTarget(Teamc target) {

            if (target == null) {
                return false;
            }
            if (target == unit) {
                return false;
            }
            if (!isEnemyTeam(target.team())) {
                return false;
            }
            return !Units.invalidateTarget(target, unit.team, unit.x, unit.y);
        }
        private Teamc findBestTarget() {

            Teamc bestTarget = null;
            float bestScore = Float.NEGATIVE_INFINITY;
            Building building = Units.findEnemyTile(unit.team, unit.x, unit.y, aggRad, tile -> isEnemyTeam(tile.team()));
            if (building != null) {
                float score = scoreTarget(building);
                if (score > bestScore) {
                    bestScore = score;
                    bestTarget = building;
                }
            }
            Unit enemyUnit = Units.closest(null, unit.x, unit.y, aggRad, u -> isValidTarget(u) && !u.dead);

            if (enemyUnit != null) {
                float score = scoreTarget(enemyUnit);
                if (score > bestScore) {
                    bestScore = score;
                    bestTarget = enemyUnit;
                }
            }
            for (var data : Vars.state.teams.getActive()) {
                if (!isEnemyTeam(data.team)) {
                    continue;
                }
                for (var core : data.cores) {
                    if (core == null || core.dead()) {
                        continue;
                    }
                    if (core.block == Blocks.coreShard) {
                        continue;
                    }
                    float score = scoreTarget(core);
                    if (score > bestScore) {
                        bestScore = score;
                        bestTarget = core;
                    }
                }
            }
            if (bestTarget == null) {
                bestTarget = Units.findEnemyTile(unit.team, unit.x, unit.y, Float.MAX_VALUE, tile -> isEnemyTeam(tile.team()));
            }
            if (bestTarget == null) {
                bestTarget = Units.closest(null, unit.x, unit.y, Float.MAX_VALUE, u -> isValidTarget(u) && !u.dead);
            }
            return bestTarget;
        }

        private float scoreTarget(Teamc target) {

            if (target == null) {
                return Float.NEGATIVE_INFINITY;
            }
            float distance = unit.dst(target);
            float score = 1000f - distance;
            float laneAlignment = getLaneAlignment(target);
            score += laneAlignment * lBonus;
            if (laneAlignment < 0.25f) {
                score -= wLane;
            }

            if (target instanceof Building building) {

                if (building.block == Blocks.coreNucleus) score += ncBonus;

                else if (building.block == Blocks.coreFoundation) score += fBonus;



                else if (building.block == Blocks.coreShard) score += 25f;


                else if (
                        building.block instanceof mindustry.world.blocks.defense.turrets.Turret
                ) {

                    score += tBonus;
                }

                else {

                    score += 75f;
                }
            }
            else if (target instanceof Unit enemy) {

                score += uBonus;

                score += enemy.hitSize * 3f;

                if (enemy.health > 0f) {

                    float healthRatio = enemy.health / Math.max(enemy.maxHealth, 1f);

                    if (healthRatio < 0.35f) {
                        score += 150f;
                    }
                }

                if (enemy.isFlying()) {
                    score += 50f;
                }
            }

            return score;
        }

        private float getLaneAlignment(Teamc target) {

            if (spawnPoint == null || target == null) {
                return 0.5f;
            }

            float spawnX = spawnPoint.worldx();
            float spawnY = spawnPoint.worldy();

            float laneX = spawnX - strategicCenter.x;
            float laneY = spawnY - strategicCenter.y;

            float targetX = target.x() - strategicCenter.x;
            float targetY = target.y() - strategicCenter.y;

            float laneLength = (float)Math.sqrt(laneX * laneX + laneY * laneY);

            float targetLength = (float)Math.sqrt(targetX * targetX + targetY * targetY);

            if (laneLength < 1f || targetLength < 1f) {
                return 1f;
            }

            laneX /= laneLength;
            laneY /= laneLength;

            targetX /= targetLength;
            targetY /= targetLength;

            float dot = laneX * targetX + laneY * targetY;

            dot = Math.max(-1f, Math.min(1f, dot));

            float angle = (float)Math.toDegrees(Math.acos(dot));

            float alignment = 1f - Math.min(angle / mLaneAngle, 1f);

            return alignment;
        }
    }
}