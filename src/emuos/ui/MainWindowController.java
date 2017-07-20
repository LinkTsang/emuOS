/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.ui;

import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FXML Controller class
 *
 * @author Link
 */
public class MainWindowController implements Initializable {

    private final Stage monitorStage = new Stage();
    private Scene monitorScene;

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        try {
            Parent monitorRoot = FXMLLoader.load(getClass().getResource("Monitor.fxml"));
            monitorScene = new Scene(monitorRoot, 300, 250);
            monitorStage.setScene(monitorScene);
            monitorStage.setTitle("Monitor");
            monitorStage.setScene(monitorScene);
        } catch (IOException ex) {
            Logger.getLogger(MainWindowController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
