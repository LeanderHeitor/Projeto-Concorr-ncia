package br.com.concorrencia.tradutor.controller.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class TradutorConcorrencia extends Application {

    private ControladorTela controller;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/br/com/concorrencia/tradutor/tela.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1280, 768);

        controller = fxmlLoader.getController();

        stage.setTitle("Tradutor - Concorrência");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.stop();
        }
        System.exit(0);
    }

    public static void main(String[] args) {
        launch();
    }
}