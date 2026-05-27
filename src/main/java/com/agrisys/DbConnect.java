package com.agrisys;

import com.agrisys.Utils.UIErrorReport;
import io.github.cdimascio.dotenv.Dotenv;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @author Michael Bragt
 */
public enum DbConnect {
    /**
     * Our ENUM can only have this ONE constant
     * Therefore this makes out ENUM into singleton
     * database connection, so we are sure we always only have this one connection
     */
    UNIQUE_CONNECT;

    private Connection connection;
    private Dotenv dotenv;
    private static final Logger LOGGER = Logger.getLogger(DbConnect.class.getName());
    private static final int TIMEOUT_SECONDS = 2;

    /**
     * Connection is created when the ENUM is accessed for the first time.
     * Environment variables are loaded from the project root .env file.
     */
    DbConnect() {
        loadConfig();
    }

    /**
     * Loads database configuration from the .env file.
     * Adheres to the principle of "Information Hiding" by keeping config logic private.
     */
    private void loadConfig() {
        this.dotenv = Dotenv.configure()
                .directory("./")
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();
    }
    
    /**
     * Attempts to establish a new physical connection to the MSSQL server.
     * Uses strict validation to ensure required parameters are present.
     */
    private void connect() {
        String url = dotenv.get("DB_URL");
        String user = dotenv.get("DB_USER");
        String password = dotenv.get("DB_PASSWORD");

        if (url == null || user == null || password == null) {
            LOGGER.log(Level.SEVERE, "Critical Error: Database environment variables are missing in .env");
            return;
        }

        try {
            LOGGER.info("Attempting to establish new database connection...");
            this.connection = DriverManager.getConnection(url, user, password);
            LOGGER.info("Database connection established successfully.");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to connect to the database.", e);
            UIErrorReport.showDatabaseError(e);
        }
    }

    /**
     * Primary gateway to obtain the database connection.
     * This method implements a "Self-Healing" pattern: it verifies if the connection
     * is null, closed, or invalid (timed out) and attempts to reconnect transparently.
     * 
     * @return The active database Connection.
     */
    public synchronized Connection getConnection() {
        try {
            // Check if connection is null, closed, or fails the validation ping
            if (connection == null || connection.isClosed() || !connection.isValid(TIMEOUT_SECONDS)) {
                connect();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Connection health check failed. Attempting recovery.", e);
            connect();
        }
        return connection;
    }

    /**
     * Gracefully closes the database connection.
     * Implemented to support clean application shutdown.
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                LOGGER.info("Database connection closed gracefully.");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error while closing the database connection.", e);
        }
    }
}
