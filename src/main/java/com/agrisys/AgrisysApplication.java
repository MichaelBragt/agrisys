package com.agrisys;

import com.agrisys.config.AppConfig;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class AgrisysApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(AgrisysApplication.class.getResource(AppConfig.MAIN_VIEW));
        
        Scene scene = new Scene(fxmlLoader.load(), AppConfig.MIN_WIDTH, AppConfig.MIN_HEIGHT);

        stage.setWidth(AppConfig.MIN_WIDTH);
        stage.setHeight(AppConfig.MIN_HEIGHT);
        stage.setMinWidth(AppConfig.MIN_WIDTH);
        stage.setMinHeight(AppConfig.MIN_HEIGHT);
        
        stage.setTitle(AppConfig.APP_TITLE);
        stage.setScene(scene);
        stage.show();
    }
}
