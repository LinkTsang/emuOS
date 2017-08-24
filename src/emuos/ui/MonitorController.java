package emuos.ui;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import emuos.diskmanager.FilePath;
import emuos.diskmanager.FileSystem;
import emuos.os.DeviceManager;
import emuos.os.Kernel;
import emuos.os.ProcessManager;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.io.FileNotFoundException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * FXML Controller class
 *
 * @author Link
 */
public class MonitorController implements Initializable {

    private final ObservableList<ProcessManager.Snapshot> processList =
            FXCollections.observableArrayList();
    private final ObservableList<DeviceManager.Snapshot> deviceList =
            FXCollections.observableArrayList();

    private final Image folderIcon = new Image(getClass().getResourceAsStream("folder.png"));
    private final Image fileIcon = new Image(getClass().getResourceAsStream("file.png"));
    public TabPane tabPane;
    public Tab processesTab;
    public Tab devicesTab;
    public Tab diskTab;
    public TableView<ProcessManager.Snapshot> processesTable;
    public TableColumn<ProcessManager.Snapshot, Integer> processPIDCol;
    public TableColumn<ProcessManager.Snapshot, String> processStatusCol;
    public TableColumn<ProcessManager.Snapshot, Integer> processMemoryCol;
    public TableColumn<ProcessManager.Snapshot, String> processPathCol;
    public TableColumn<ProcessManager.Snapshot, Integer> processPCCol;
    public TableView<DeviceManager.Snapshot> devicesTable;
    public TableColumn<DeviceManager.Snapshot, Integer> deviceIDCol;
    public TableColumn<DeviceManager.Snapshot, String> deviceStatusCol;
    public TableColumn<DeviceManager.Snapshot, Integer> devicePIDCol;
    public Canvas diskCanvas;
    public TreeTableView<FilePath> fileTreeTableView;
    public TreeTableColumn<FilePath, String> fileNameCol;
    public TreeTableColumn<FilePath, String> fileSizeCol;
    private Kernel kernel;
    private Timeline processesTimeline;
    private Timeline devicesTimeline;
    private Timeline diskTimeline;
    private FileTreeItem rootDir = new FileTreeItem(new FilePath("/"), new ImageView(folderIcon));

    public MonitorController() {
        initTimeLine();
    }

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initTableView();
    }

    public void init(MainWindow mainWindow) {
        kernel = mainWindow.getKernel();
    }

    private void initTableView() {
        processesTable.setItems(processList);
        processPIDCol.setCellValueFactory(new PropertyValueFactory<>("PID"));
        processPathCol.setCellValueFactory(new PropertyValueFactory<>("path"));
        processStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        processMemoryCol.setCellValueFactory(new PropertyValueFactory<>("memorySize"));
        processPCCol.setCellValueFactory(new PropertyValueFactory<>("PC"));

        deviceIDCol.setCellValueFactory(new PropertyValueFactory<>("ID"));
        deviceStatusCol.setCellValueFactory(new PropertyValueFactory<>("Status"));
        devicePIDCol.setCellValueFactory(new PropertyValueFactory<>("PID"));
        devicesTable.setItems(deviceList);

        fileNameCol.setCellValueFactory(param -> {
            TreeItem<FilePath> item = param.getValue();
            return item == null
                    ? new ReadOnlyStringWrapper()
                    : new ReadOnlyStringWrapper(item.getValue().getName());
        });
        fileSizeCol.setCellValueFactory(param -> {
            TreeItem<FilePath> item = param.getValue();
            if (item == null) return new ReadOnlyStringWrapper();
            FilePath file = item.getValue();
            try {
                if (file.isFile()) {
                    int size = FileSystem.getFileSystem().getSize(file);
                    return new ReadOnlyStringWrapper(String.format("%d B", size));
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return new ReadOnlyStringWrapper();
        });
        fileTreeTableView.setRoot(rootDir);
        rootDir.setExpanded(true);
        fileTreeTableView.getColumns().setAll(fileNameCol, fileSizeCol);
        fileTreeTableView.refresh();
    }

    private void initTimeLine() {
        processesTimeline = new Timeline(new KeyFrame(Duration.millis(200), ae -> {
            TableView.TableViewSelectionModel<ProcessManager.Snapshot> model = processesTable.getSelectionModel();
            int index = model.getSelectedIndex();
            processList.setAll(kernel.getProcessManager().snap());
            model.select(index);
        }));
        processesTimeline.setCycleCount(Animation.INDEFINITE);

        devicesTimeline = new Timeline(new KeyFrame(Duration.millis(200), ae -> {
            TableView.TableViewSelectionModel<DeviceManager.Snapshot> model = devicesTable.getSelectionModel();
            int index = model.getSelectedIndex();
            deviceList.setAll(kernel.getDeviceManager().snap());
            model.select(index);
        }));
        devicesTimeline.setCycleCount(Animation.INDEFINITE);

        diskTimeline = new Timeline(new KeyFrame(Duration.millis(1000), ae -> {
            renderDiskMap();
        }));
        diskTimeline.setCycleCount(Animation.INDEFINITE);
    }

    public void handleProcessesTabSelectionChanged(Event event) {
        if (processesTab.isSelected()) {
            processesTimeline.play();
        } else {
            processesTimeline.pause();
        }
    }

    public void handleDevicesTabSelectionChanged(Event event) {
        if (devicesTab.isSelected()) {
            devicesTimeline.play();
        } else {
            devicesTimeline.pause();
        }
    }

    public void handleDiskTabSelectionChanged(Event event) {
        if (diskTab.isSelected()) {
            renderDiskMap();
            fileTreeTableView.refresh();
            diskTimeline.play();
        } else {
            diskTimeline.pause();
        }
    }

    private void renderDiskMap() {
        GraphicsContext context = diskCanvas.getGraphicsContext2D();
        context.clearRect(0, 0, diskCanvas.getWidth(), diskCanvas.getHeight());
        FileSystem fs = FileSystem.getFileSystem();
        final int col = 16;
        final double size = diskCanvas.getWidth() / col;
        double x = 0, y = 0, w = size * 0.9, h = w;
        final double space = size * 0.1;
        for (int i = 0; i < 64 * 2; ++i) {
            context.setFill(fs.read(i) == 0 ? Color.LIGHTGREEN : Color.GREEN);
            context.fillRect(x, y, w, h);
            x += w + space;
            if (i % col == col - 1) {
                x = 0;
                y += h + space;
            }
        }
    }

    public void handleFileTreeClicked(MouseEvent mouseEvent) {
    }

    public void handleFileTreeKeyPressed(KeyEvent keyEvent) {
        switch (keyEvent.getCode()) {
            case F5: {
                refreshDiskTab();
            }
            break;
            case SPACE:
            case ENTER: {
                FileTreeItem item = (FileTreeItem) fileTreeTableView.getSelectionModel().getSelectedItem();
                if (item == null) return;
                item.setExpanded(!item.isExpanded());
            }
            break;
        }
    }

    private void refreshDiskTab() {
        renderDiskMap();
        FileTreeItem item = (FileTreeItem) fileTreeTableView.getSelectionModel().getSelectedItem();
        if (item == null) return;
        FilePath file = item.getValue();
        try {
            if (file.isDir() && file.list() != null) {
                item.invalidate();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        item.loadChildren();
    }

    private class FileTreeItem extends TreeItem<FilePath> {
        private boolean childrenLoaded = false;

        FileTreeItem(FilePath path, ImageView folderIcon) {
            super(path, folderIcon);
        }

        public void invalidate() {
            childrenLoaded = false;
        }

        public void loadChildren() {
            FilePath filePath = getValue();
            List<TreeItem<FilePath>> children = new ArrayList<>();
            for (FilePath f : filePath.list()) {
                Image image = null;
                try {
                    image = f.isDir() ? folderIcon : fileIcon;
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                children.add(new FileTreeItem(f, new ImageView(image)));
            }
            super.getChildren().setAll(children);
            fileTreeTableView.refresh();
        }

        @Override
        public boolean isLeaf() {
            if (childrenLoaded) {
                return getChildren().isEmpty();
            }
            try {
                return !getValue().isDir();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return true;
        }

        @Override
        public ObservableList<TreeItem<FilePath>> getChildren() {
            if (childrenLoaded) {
                fileTreeTableView.refresh();
                return super.getChildren();
            }
            childrenLoaded = true;
            loadChildren();
            return super.getChildren();
        }
    }
}
