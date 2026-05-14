package com.agrisys.dao;

import com.agrisys.DbConnect;
import com.agrisys.model.PigRecord;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO for Pig table. Implements CRUD for biological data.
 */
public class PigDAO {
    
    public Optional<PigRecord> findById(int id) throws SQLException {
        String sql = "SELECT PigID, BirthDate, Status FROM Pig WHERE PigID = ?";
        try (Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<PigRecord> findAllActive() throws SQLException {
        List<PigRecord> pigs = new ArrayList<>();
        String sql = "SELECT * FROM Pig WHERE Status = 'Aktiv'";
        try (Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                pigs.add(map(rs));
            }
        }
        return pigs;
    }

    private PigRecord map(ResultSet rs) throws SQLException {
        Date birth = rs.getDate("BirthDate");
        return new PigRecord(
            rs.getInt("PigID"),
            birth != null ? birth.toLocalDate() : null,
            rs.getString("Status")
        );
    }
}