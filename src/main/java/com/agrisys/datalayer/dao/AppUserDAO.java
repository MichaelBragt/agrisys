package com.agrisys.datalayer.dao;

// PREPARED STATEMENTS
// "I vores datalag har vi konsekvent valgt at anvende PreparedStatement frem for almindelige direkte Statement-objekter." +
// "Dette valg er truffet ud fra to primære software engineering-principper: Sikkerhed og Performance.
// For det første sikrer det os mod SQL Injection, som er en af de mest kritiske sårbarheder i database-applikationer.
// Ved at anvende placeholders med spørgsmålstegn tvinger vi databasen til at adskille SQL-kommandoens struktur fra selve datainputtet.
// Databasen laver en pre-kompilering af forespørgslen og låser en eksekveringsplan fast, inden parametrene bindes.
// Hvis en bruger forsøger at injicere SQL-kommandoer i vores JavaFX-tekstfelter, vil JDBC-driveren og MSSQL-serveren behandle inputtet udelukkende som en rå,
// neutraliseret dataliteral og aldrig som eksekverbar kode.
// Samtidig opnår vi en performance-fordel ved, at databasen cacher eksekveringsplanen, hvilket minimerer overhead ved gentagne databasekald,
// når der f.eks. oprettes grise eller batches af sensordata."

/**
 * Data Access Object (DAO) for AppUser-tabellen.
 * Varetager database-integrationen for brugerautentificering og rettighedsstyring.
 * * @author Michael Bragt
 * @see "PS-02: Adgangsstyring - Arkitektonisk rollestyring og adgangsniveauer"
 * @see "FR-08: Brugere (landmand/rådgiver) skal kunne logge ind med unikt login"
 * @see "FR-17: CRUD-håndtering af brugere (Admin) direkte i databasen/systemet"
 */

import com.agrisys.DbConnect;
import com.agrisys.Utils.SecurityUtils;
import com.agrisys.datalayer.entity.AppUserRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement; // Tilføjet så RETURN_GENERATED_KEYS ikke lyser rødt
import java.util.Optional;

// Primær forfatter: Michael Bragt (Udvidet med login-integration)

/**
 * DAO for the AppUser table.
 * Handles authentication and user management.
 */
public class AppUserDAO {

    // NY: Bruges til at sende et rent resultat tilbage til jeres login-skærm
    public record UserResult(String username, String role) {}

    /**
     * Validerer en bruger ved at hashe input-passwordet og sammenligne det med databasen.
     */
    public Optional<UserResult> validateLogin(String username, String rawPassword) throws SQLException {
        // 1. Slå brugeren op via Michaels eksisterende metode
        Optional<AppUserRecord> userOpt = findByUsername(username);

        if (userOpt.isPresent()) {
            AppUserRecord user = userOpt.get();

            // 2. Hash det password, som brugeren lige har indtastet i jeres login-felt
            String hashedInput = SecurityUtils.hashPassword(rawPassword);

            // 3. Sammenlign de to hashes (Input-hash mod databasens gemte hash)
            if (user.password().equals(hashedInput)) {
                // Succes! Returner de data, som jeres UserSession skal bruge
                // HER SKULLE VI HAVE RETURNERET EN AppUserRecord istedet
                // Vi har den allerede i denne line AppUserRecord user = userOpt.get();
                // Så vi skulle bare have gjort return Optional.of(user);
                return Optional.of(new UserResult(user.username(), user.userRole()));
            }
        }

        // Enten fandtes brugeren ikke, eller også var passwordet forkert
        return Optional.empty();
    }

    /**
     * Retrieves an AppUser by their unique username.
     */
    // Here the Connection coon should have been outside the try with resource
    // so the connection didn't get closed, but our single enum Connection class
    // is luckily self-healing so it won't crash
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