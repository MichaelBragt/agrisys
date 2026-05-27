package com.agrisys.Utils;

import javafx.scene.control.Alert;

import java.sql.SQLException;

/**
 * @author Michael Bragt
 */
public class UIErrorReport {

    /**
     *
     * @param t
     */
    public static void showDatabaseError(Throwable t) {

        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }

        if(cause instanceof SQLException sqlEx) {
            handleSqlError(sqlEx);
        }
        else {
            showAlert(
                    "Program error",
                    "There has been an unexpected program error",
                    cause.getMessage()
            );
        }
    }

    /**
     * methos to show various dql error inthe UI
     * @param sqlEx
     */
    private static void handleSqlError(SQLException sqlEx) {

        String sqlState = sqlEx.getSQLState();
        int errorCode = sqlEx.getErrorCode();

        // DB connection errors always starts with 08
        if(sqlState != null && sqlState.startsWith("08")) {
            showAlert(
                    "Database connection error",
                    "Cannot connect to database",
                    "The database server is not running or unreachable."
            );
        }

        // login errors has errorcode 18456
        else if (errorCode == 18456) {
            showAlert(
                    "Login failed",
                    "Authentication error",
                    "Invalid database username or password."
            );
        }
        // Other errors have an sqlState with 23000
        else if ("23000".equals(sqlState)) {
            showAlert(
                    "Invalid data",
                    "Constraint violation",
                    "Data eksisterer allerede eller kan ikke opdateres\nPga. data constraint violations"
            );
        }
        // or else just tell we have an sql error (SQLException)
        else {
            showAlert(
                    "Database error",
                    "Unexpected SQL error",
                    sqlEx.getMessage()
            );
        }
    }

    /**
     * helper methos to show errors with info in the UI
     * @param title
     * @param header
     * @param content
     */
    public static void showAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();

        // should app close on error? for now NO
//        Platform.exit(); // clean JavaFX shutdown
    }

}
