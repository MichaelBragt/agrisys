package com.agrisys.service;

import com.agrisys.DbConnect;
import com.agrisys.dto.ChartPoint;
import com.agrisys.dto.ChartSeriesData;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ChartService {

    /**
     * Henter den overordnede FCR trend dag for dag for hele besætningen.
     */
    public ChartSeriesData hentFcrTrendForBestand() throws SQLException {
        List<ChartPoint> punkter = new ArrayList<>();

        // SQL der tager dagens samlede foder (i gram) og holder det op mod
        // grisenes forventede daglige tilvækst (1000g pr. gris om dagen).
        String sql = """
            SELECT 
                CONVERT(VARCHAR(10), d.visit_time, 120) AS Dato,
                -- Formel: Total foder / (Antal unikke grise på dagen * 1000g tilvækst)
                CAST(SUM(d.feed_intake) / (COUNT(DISTINCT ra.animal_number) * 1000.0) AS DOUBLE PRECISION) AS BeregnetFCR
            FROM PPT_Data d
            JOIN Responder_Assignment ra ON d.assignment_id = ra.assignment_id
            GROUP BY CONVERT(VARCHAR(10), d.visit_time, 120)
            ORDER BY Dato ASC
        """;

        try (Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                String dato = rs.getString("Dato");
                double fcr = rs.getDouble("BeregnetFCR");

                // Afrund til 2 decimaler (f.eks. 2.94)
                fcr = Math.round(fcr * 100.0) / 100.0;

                // Vi filtrerer den sidste dag fra, hvis den kun indeholder halve data (f.eks. pga. eksport-tidspunkt)
                if (fcr > 1.0 && fcr < 6.0) {
                    punkter.add(new ChartPoint(dato, fcr));
                }
            }
        }

        return new ChartSeriesData("Hele Bestanden", punkter);
    }

    /**
     * Henter den gennemsnitlige vægtudvikling (i kg) dag for dag for hele besætningen.
     */
    public ChartSeriesData hentGennemsnitVaegtForBestand() throws SQLException {
        List<ChartPoint> punkter = new ArrayList<>();

        String sql = """
        SELECT 
            CONVERT(VARCHAR(10), visit_time, 120) AS Dato,
            -- Vi tager gennemsnitsvægten for dagen og laver gram om til kg
            CAST(AVG(pig_weight / 1000.0) AS DOUBLE PRECISION) AS GnsVaegtKG
        FROM PPT_Data
        WHERE pig_weight > 0
        GROUP BY CONVERT(VARCHAR(10), visit_time, 120)
        ORDER BY Dato ASC
    """;

        try (Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                String dato = rs.getString("Dato");
                double vaegt = rs.getDouble("GnsVaegtKG");

                // Afrund til 1 decimal (f.eks. 95.5 kg)
                vaegt = Math.round(vaegt * 10.0) / 10.0;

                punkter.add(new ChartPoint(dato, vaegt));
            }
        }

        return new ChartSeriesData("Gennemsnitsvægt (kg)", punkter);
    }
}