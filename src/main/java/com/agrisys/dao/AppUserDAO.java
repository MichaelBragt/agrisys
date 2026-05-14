package com.agrisys.dao;

import com.agrisys.DbConnect;
import com.agrisys.model.AppUserRecord;
import java.sql.*;
import java.util.Optional;

/**
 * DAO for the AppUser table.
 * Handles authentication and user management.
 */
public class AppUserDAO {

    /**
     * Retrieves an AppUser by their unique username.
     * This is typically used for authentication.
     *
     * @param username The username to search for.
     * @return An Optional containing the AppUserRecord if found.
     * @throws SQLException if a database error occurs.
     */
    public Optional<AppUserRecord> findByUsername(String username) throws SQLException {
        String sql = "SELECT user_id, username, password, user_role FROM AppUser WHERE username = ?";
        try (Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Creates a new AppUser in the database.
     *
     * @param user The AppUserRecord containing the user's details (password should be hashed).
     * @return The generated user_id for the new user.
     * @throws SQLException if a database error occurs.
     */
    public int create(AppUserRecord user) throws SQLException {
        String sql = "INSERT INTO AppUser (username, password, user_role) VALUES (?, ?, ?)";
        try (Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, user.username());
            pstmt.setString(2, user.password());
            pstmt.setString(3, user.userRole());
            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                throw new SQLException("Creating user failed, no ID obtained.");
            }
        }
    }

    /**
     * Helper method to map a ResultSet row to an AppUserRecord.
     */
    private AppUserRecord map(ResultSet rs) throws SQLException {
        return new AppUserRecord(
            rs.getInt("user_id"),
            rs.getString("username"),
            rs.getString("password"),
            rs.getString("user_role")
        );
    }
}