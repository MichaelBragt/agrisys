package com.agrisys.dao;

import com.agrisys.DbConnect;
import com.agrisys.model.RfidTagRecord;
import java.sql.*;
import java.util.Optional;

/**
 * DAO for RFID hardware management.
 */
public class RfidTagDAO {
    
    public Optional<RfidTagRecord> findByCode(String code) throws SQLException {
        String sql = "SELECT RFID_TagID, RFIDCode FROM RFID_Tag WHERE RFIDCode = ?";
        try (Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, code);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new RfidTagRecord(
                        rs.getInt("RFID_TagID"),
                        rs.getString("RFIDCode")
                    ));
                }
            }
        }
        return Optional.empty();
    }

    public void registerTag(String code) throws SQLException {
        // SQL Insert logic...
    }
}