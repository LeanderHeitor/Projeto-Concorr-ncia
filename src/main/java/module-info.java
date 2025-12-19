module Tradutor {
    requires javafx.base;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;

    opens br.com.concorrencia.tradutor.controller.gui to javafx.fxml;

    exports br.com.concorrencia.tradutor.controller.gui;
}