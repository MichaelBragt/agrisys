module com.agrisys {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.agrisys to javafx.fxml, javafx.graphics, javafx.controls, javafx.base;
    exports com.agrisys;
}