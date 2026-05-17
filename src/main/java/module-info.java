module com.agrisys {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires io.github.cdimascio.dotenv.java;
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;
    requires javafx.graphics;

    opens com.agrisys to javafx.fxml, javafx.graphics;
    opens com.agrisys.controller to javafx.fxml;
    
    exports com.agrisys;
    exports com.agrisys.controller;
}