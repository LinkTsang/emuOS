/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.ui;

import emuos.os.CentralProcessingUnit;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * @author Link
 */
public class MainWindow extends Application {

    public final static String WINDOW_TITLE = "emuOS";
    private final CentralProcessingUnit kernel = new CentralProcessingUnit();

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        loadKernel();
        FXMLLoader terminalLoader = new FXMLLoader(getClass().getResource("Terminal.fxml"));
        Parent root = terminalLoader.load();
        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle(WINDOW_TITLE);
        primaryStage.setScene(scene);
        TerminalController terminalController = terminalLoader.getController();
        terminalController.setStage(primaryStage);
        terminalController.initShell(kernel);
        primaryStage.show();
    }

    private void loadKernel() {
        kernel.run();
    }
}
