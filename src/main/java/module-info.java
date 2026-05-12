module com.agrisys {
    requires javafx.controls;
    requires javafx.fxml;

    opens com.agrisys to javafx.fxml, javafx.graphics;
    opens com.agrisys.controller to javafx.fxml;
    
    exports com.agrisys;
    exports com.agrisys.controller;
}