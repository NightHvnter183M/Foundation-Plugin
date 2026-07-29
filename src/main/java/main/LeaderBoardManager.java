package main;

import arc.Core;
import arc.util.Log;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
//I'm done with this shit. I hate SQL
public class LeaderBoardManager {

    private static Connection con;
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


    public static void init(){
        try {
            File dir = Core.settings.getDataDirectory().child("mods/Foundation").file();
            if (!dir.exists()) dir.mkdirs();

            String url = "jdbc:sqlite:" + new File(dir, "leaderboard.db").getAbsolutePath();
            con = DriverManager.getConnection(url);

            try (Statement stmt = con.createStatement()) {
                // Creating DB
                stmt.execute("CREATE TABLE IF NOT EXISTS players (" +
                        "uuid TEXT PRIMARY KEY, " +
                        "name TEXT, " +
                        "points INTEGER DEFAULT 0)");
            }
            Log.info("[Foundation] Leaderboard db is successfully initialized.");
        } catch (SQLException e) {
            Log.err("[Foundation] Error initializing db", e);
        }
    }

    public static void addPoints(String uuid, String name, int points){
        String sql = "INSERT INTO players(uuid, name, points) VALUES(?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET " +
                "name = excluded.name, " +
                "points = points + excluded.points";
        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            pstmt.setString(2, name);
            pstmt.setInt(3, points);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            Log.err("[Foundation] Error adding points to: " + name, e);
        }
    }

    public static List<Player> getTop(int limit) {
        List<Player> list = new ArrayList<>();
        String sql = "SELECT uuid, name, points FROM players ORDER BY points DESC LIMIT ?";

        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();

            int position = 1;
            while (rs.next()) {
                list.add(new Player(rs.getString("uuid"), rs.getString("name"), rs.getInt("points"), position++));
            }
        } catch (SQLException e) {
            Log.err("[Foundation] Error getting a leaderboard", e);
        }
        return list;
    }

    public static Player getPlayerStats(String uuid) {
        String sql = "SELECT points, name, " +
                "(SELECT COUNT(*) + 1 FROM players WHERE points > p.points) AS position " +
                "FROM players p WHERE uuid = ?";

        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new Player(
                        uuid,
                        rs.getString("name"),
                        rs.getInt("points"),
                        rs.getInt("position")
                );
            }
        } catch (SQLException e) {
            Log.err("[Foundation] error getting player position: " + uuid, e);
        }

        return null;
    }
}
