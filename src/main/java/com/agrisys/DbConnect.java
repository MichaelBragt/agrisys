package com.agrisys;

import com.agrisys.Utils.UIErrorReport;
import io.github.cdimascio.dotenv.Dotenv;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public enum DbConnect {

    /**
     * Our ENUM can only have this ONE constant
     * Therefore this makes out ENUM into singleton
     * database connection, so we are sure we always only have this one connection
     */
    UNIQUE_CONNECT;

    private Connection connection;

    /**
     * Connection is created when the ENUM is accessed for the first time.
     * Environment variables are loaded from the project root .env file.
     */
    DbConnect() {
        // Initialize Dotenv to load variables from the root directory
        Dotenv dotenv = Dotenv.configure()
                .directory("./") 
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();

        String url = dotenv.get("DB_URL");
        String user = dotenv.get("DB_USER");
        String password = dotenv.get("DB_PASSWORD");

        if (url == null || user == null || password == null) {
            System.err.println("Critical Error: Database environment variables are missing in .env");
            return;
        }

        try {
            connection = DriverManager.getConnection(url, user, password);
        }
        catch (SQLException e) {
            UIErrorReport.showDatabaseError(e);
        }
    }

    /**
     * method to get connection
     * @return
     */
    public Connection getConnection() {
        return connection;
    }

    // close database connection method
    // just in case we need it :-)
    public void close() {
        try {
            if(connection != null && !connection.isClosed()) {
                connection.close();
//                System.out.println("Database connection is closed");
            }
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
