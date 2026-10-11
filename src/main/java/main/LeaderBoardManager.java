package main;

import arc.Core;
import arc.func.Cons;
import arc.struct.Seq;
import arc.util.Log;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LeaderBoardManager {

    private static Connection con;

    private static final ExecutorService db = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Foundation-DB");
        thread.setDaemon(true);
        return thread;
    });

    public static class Player {
        public String uuid;
        public String name;
        public int points;
        public int position;

        public Player(String uuid, String name, int points, int position) {
            this.uuid = uuid;
            this.name = name;
            this.points = points;
            this.position = position;
        }
    }

    public record PointsEntry(String uuid, String name, int points) {}

    private static Connection getConnection() throws SQLException {
        if (con == null || con.isClosed()) {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                throw new SQLException("Couldn't fetch SQLite ", e);
            }

            File dir = Core.settings.getDataDirectory().child("mods/Foundation").file();
            if (!dir.exists()) dir.mkdirs();

            String url = "jdbc:sqlite:" + new File(dir, "leaderboard.db").getAbsolutePath();
            con = DriverManager.getConnection(url);
            try (Statement stmt = con.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
            }
        }
        return con;
    }

    public static void init() {
        db.execute(() -> {
            try {
                Connection connection = getConnection();
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("CREATE TABLE IF NOT EXISTS players (" +
                            "uuid TEXT PRIMARY KEY, " +
                            "name TEXT, " +
                            "points INTEGER DEFAULT 0)");
                    // /rank counts players with more points than you
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_players_points ON players(points)");
                }
                Log.info("[Foundation] Leaderboard db is successfully initialized.");
            } catch (SQLException e) {
                Log.err("[Foundation] Error initializing db", e);
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(LeaderBoardManager::shutdown, "Foundation-DB-shutdown"));
    }

    public static void addPoints(Seq<PointsEntry> entries) {
        if (entries.isEmpty()) return;
        PointsEntry[] batch = entries.toArray(PointsEntry.class);
        String sql = "INSERT INTO players(uuid, name, points) VALUES(?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET " +
                "name = excluded.name, " +
                "points = points + excluded.points";

        db.execute(() -> {
            try {
                Connection connection = getConnection();
                connection.setAutoCommit(false);
                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    for (PointsEntry entry : batch) {
                        pstmt.setString(1, entry.uuid());
                        pstmt.setString(2, entry.name());
                        pstmt.setInt(3, entry.points());
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                Log.err("[Foundation] Error adding points", e);
            }
        });
    }

    /** Loads the top on the DB thread and passes it to {@code callback} on the game thread. */
    public static void getTop(int limit, Cons<List<Player>> callback) {
        db.execute(() -> {
            List<Player> list = queryTop(limit);
            Core.app.post(() -> callback.get(list));
        });
    }

    /** Loads a player's stats on the DB thread and passes them (or null) to {@code callback} on the game thread. */
    public static void getPlayerStats(String uuid, Cons<Player> callback) {
        db.execute(() -> {
            Player stats = queryPlayerStats(uuid);
            Core.app.post(() -> callback.get(stats));
        });
    }

    private static List<Player> queryTop(int limit) {
        List<Player> list = new ArrayList<>();
        String sql = "SELECT uuid, name, points FROM players ORDER BY points DESC LIMIT ?";

        try {
            Connection connection = getConnection();
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, limit);
                try (ResultSet rs = pstmt.executeQuery()) {
                    int position = 1;
                    while (rs.next()) {
                        list.add(new Player(rs.getString("uuid"), rs.getString("name"), rs.getInt("points"), position++));
                    }
                }
            }
        } catch (SQLException e) {
            Log.err("[Foundation] Error getting a leaderboard", e);
        }
        return list;
    }

    private static Player queryPlayerStats(String uuid) {
        String sql = "SELECT points, name, " +
                "(SELECT COUNT(*) + 1 FROM players WHERE points > p.points) AS position " +
                "FROM players p WHERE uuid = ?";

        try {
            Connection connection = getConnection();
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return new Player(
                                uuid,
                                rs.getString("name"),
                                rs.getInt("points"),
                                rs.getInt("position")
                        );
                    }
                }
            }
        } catch (SQLException e) {
            Log.err("[Foundation] error getting player position: " + uuid, e);
        }

        return null;
    }

    private static void shutdown() {
        db.shutdown();
        try {
            if (!db.awaitTermination(5, TimeUnit.SECONDS)) {
                Log.warn("[Foundation] Leaderboard writes did not finish before shutdown.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (con != null) con.close();
        } catch (SQLException ignored) {
        }
    }
}
