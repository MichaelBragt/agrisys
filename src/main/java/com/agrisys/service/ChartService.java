package com.agrisys.service;

import com.agrisys.DbConnect;
import com.agrisys.dto.chart.ChartPoint;
import com.agrisys.dto.chart.ChartSeriesData;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eirik Pran
 * @see "PS-01"
 * @see "FR-05: Systemet skal vise vægt- og spise aktivitet for en specifik gris."
 * @see "FR-10: Systemet skal præsentere vækst- og foderdata via et “Vækst Dashboard” med grafer."
 */
public class ChartService {

    /**
     * Henter den overordnede FCR trend dag for dag for hele besætningen.
     */
    public ChartSeriesData getFcrTrendForPopulation() throws SQLException {
        return getFcrTrend(null);
    }

    /**
     * Henter FCR trend for en specifik lokation eller hele besætningen.
     * @param locationId Hvis null, hentes data for hele besætningen.
     */
    public ChartSeriesData getFcrTrend(Integer locationId) throws SQLException {
        List<ChartPoint> punkter = new ArrayList<>();

        String locationFilter = (locationId == null) ? "" : 
            " JOIN Pig_Location pl ON ra.animal_number = pl.animal_number WHERE pl.departed_at IS NULL AND pl.location_id = " + locationId + " AND ";
        
        String dateFilterPrefix = (locationId == null) ? " WHERE " : "";

        // pigs estimated daily growth (1000g pr. pig per day).
        // CONVERT (style 120) corresponds to ODBC yyy-mm-dd hh:mi:ss
        // by converting to varchar(10) we strip of the time portion
        // effectively leaving us with only the dates
        // The calculation total feed at specific day / unique number of animals * 1000
        // gives us an estimated FCR based on healthy pigs under normal conditions should
        // grow by 1000 grams per day.
        // if we want to get a calculated FCR we should use SQL Windows functions
        // we also filter out the most recent day in the result because that might be a day not finished
        String sql = """
            SELECT 
                CONVERT(VARCHAR(10), d.visit_time, 120) AS Dato,
                -- Formel: Total foder / (Antal unikke grise på dagen * 1000g tilvækst)
                CAST(SUM(d.feed_intake) / (COUNT(DISTINCT ra.animal_number) * 1000.0) AS DOUBLE PRECISION) AS BeregnetFCR
            FROM PPT_Data d
            JOIN Responder_Assignment ra ON d.assignment_id = ra.assignment_id
            """ + locationFilter + dateFilterPrefix + """
            CONVERT(VARCHAR(10), d.visit_time, 120) < (
                SELECT MAX(CONVERT(VARCHAR(10), visit_time, 120)) FROM PPT_Data
            )
            GROUP BY CONVERT(VARCHAR(10), d.visit_time, 120)
            ORDER BY Dato ASC
        """;

        Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                String dato = rs.getString("Dato");
                double fcr = rs.getDouble("BeregnetFCR");

                // round of to 2 decimals
                fcr = Math.round(fcr * 100.0) / 100.0;

                // We add the points
                punkter.add(new ChartPoint(dato, fcr));

            }
        }

        return new ChartSeriesData(locationId == null ? "Hele Bestanden" : "Lokation " + locationId, punkter);
    }

    /**
     * Henter den gennemsnitlige vægtudvikling (i kg) dag for dag for hele besætningen.
     */
    public ChartSeriesData hentGennemsnitVaegtForBestand() throws SQLException {
        return getAverageWeight(null);
    }

    /**
     * Henter gennemsnitsvægt for en specifik lokation eller hele besætningen.
     */
    public ChartSeriesData getAverageWeight(Integer locationId) throws SQLException {
        List<ChartPoint> punkter = new ArrayList<>();

        String joinClause = (locationId == null) ? "" : 
            " JOIN Responder_Assignment ra ON d.assignment_id = ra.assignment_id JOIN Pig_Location pl ON ra.animal_number = pl.animal_number ";
        
        String whereClause = (locationId == null) ? " WHERE d.pig_weight > 0 " : 
            " WHERE d.pig_weight > 0 AND pl.location_id = " + locationId + " AND pl.departed_at IS NULL ";

        String sql = """
        SELECT 
            CONVERT(VARCHAR(10), d.visit_time, 120) AS Dato,
            -- Vi tager gennemsnitsvægten for dagen og laver gram om til kg
            CAST(AVG(d.pig_weight / 1000.0) AS DOUBLE PRECISION) AS GrisVaegtKG
        FROM PPT_Data d
        """ + joinClause + whereClause + """
        GROUP BY CONVERT(VARCHAR(10), d.visit_time, 120)
        ORDER BY Dato ASC
    """;

        Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                String dato = rs.getString("Dato");
                double vaegt = rs.getDouble("GrisVaegtKG");

                // Afrund til 1 decimal (f.eks. 95.5 kg)
                vaegt = Math.round(vaegt * 10.0) / 10.0;

                punkter.add(new ChartPoint(dato, vaegt));
            }
        }

        return new ChartSeriesData("Gennemsnitsvægt (kg)", punkter);
    }
}