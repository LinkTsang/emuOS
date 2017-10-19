package emuos.ui;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import emuos.compiler.Instruction;
import emuos.diskmanager.FilePath;
import emuos.diskmanager.FileSystem;
import emuos.os.*;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.io.FileNotFoundException;
import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;


/**
 * FXML Controller class
 *
 * @author Link
 */
public class MonitorController implements Initializable {

    private static final int MEMORY_USAGE_VIEW_COL_COUNT = 16;
    private static final int MEMORY_USAGE_VIEW_ROW_COUNT = MEMORY_USAGE_VIEW_COL_COUNT / 2;
    private static final int MEMORY_USAGE_VIEW_TOTAL_COUNT = MEMORY_USAGE_VIEW_COL_COUNT * MEMORY_USAGE_VIEW_ROW_COUNT;
    private final OverviewItem kernelTime = new OverviewItem("Kernel Time", "0");
    private final OverviewItem timeSlice = new OverviewItem("Time Slice", "");
    private final OverviewItem runningProcessImage = new OverviewItem("Running Process Image", "");
    private final OverviewItem runningPID = new OverviewItem("Running PID", "");
    private final OverviewItem intermediateResult = new OverviewItem("Intermediate Result", "");
    private final OverviewItem runningInstruction = new OverviewItem("Running Instruction", "");
    private final OverviewItem lastExitProcessImage = new OverviewItem("Last Exit Process Image", "");
    private final OverviewItem lastExitPID = new OverviewItem("Last Exit PID", "");
    private final OverviewItem lastExitCode = new OverviewItem("Last Exit Code", "");
    private final ObservableList<OverviewItem> overviewList =
            FXCollections.observableArrayList(
                    kernelTime, timeSlice,
                    runningPID, runningProcessImage,
                    intermediateResult, runningInstruction,
                    lastExitPID, lastExitProcessImage, lastExitCode);
    private final ObservableList<ProcessManager.Snapshot> processList =
            FXCollections.observableArrayList();
    private final ObservableList<DeviceManager.Snapshot> deviceList =
            FXCollections.observableArrayList();
    private final XYChart.Series<Number, Number> kernelUsageSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> memoryUsageSeries = new XYChart.Series<>();
    private final Image folderIcon = new Image(getClass().getResourceAsStream("folder.png"));
    private final Image fileIcon = new Image(getClass().getResourceAsStream("file.png"));
    private final FileTreeItem rootDir = new FileTreeItem(new FilePath("/"), new ImageView(folderIcon));
    public TabPane tabPane;
    public Tab overviewTab;
    private final Kernel.Listener intEndListener = c -> {
        ProcessControlBlock pcb = c.getProcessManager().getRunningProcess();
        Platform.runLater(() -> {
            if (overviewTab.isSelected()) {
                lastExitProcessImage.setValue(pcb.getImageFile().getPath());
                lastExitPID.setValue(String.valueOf(pcb.getPID()));
                lastExitCode.setValue(String.valueOf(pcb.getContext().getAX()));
            }
        });
    };
    public Tab processesTab;
    public Tab devicesTab;
    public Tab diskTab;
    public TableView<OverviewItem> overviewTable;
    public TableColumn<OverviewItem, String> overviewItemCol;
    public TableColumn<OverviewItem, String> overviewValueCol;
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
    public Canvas memoryCanvas;
    public TreeTableView<FilePath> fileTreeTableView;
    public TreeTableColumn<FilePath, String> fileNameCol;
    public TreeTableColumn<FilePath, String> fileSizeCol;
    public Label memoryLabel;
    public AreaChart<Number, Number> memoryUsageChart;
    public AreaChart<Number, Number> kernelUsageChart;
    public VBox memoryStatusVBox;
    public ScrollPane statusScrollPane;
    public VBox diskStatusVBox;
    private Kernel kernel;
    private Timeline overviewTimeline;
    private Timeline processesTimeline;
    private Timeline devicesTimeline;
    private Timeline diskTimeline;
    private long lastKernelTime = 0;
    private long lastKernelExecutionTime = 0;
    private double lastKernelUsageRate = 0.0;
    private int kernelUsageUpdateCounter = 0;

    public MonitorController() {
        initTimeLine();
    }

    private static void setUpChart(AreaChart<Number, Number> chart, XYChart.Series<Number, Number> series) {
        for (int i = 0; i < 30; ++i) {
            series.getData().add(new XYChart.Data<>(i, Math.random()));
        }
        NumberAxis xAxis = (NumberAxis) chart.getXAxis();
        chart.getXAxis().setTickLabelsVisible(false);
        chart.getXAxis().setAutoRanging(false);
        xAxis.setLowerBound(0);
        xAxis.setUpperBound(29);
        chart.setAnimated(false);
        chart.getData().add(series);
    }

    private static void updateSeries(XYChart.Series<Number, Number> series, Number value) {
        ObservableList<XYChart.Data<Number, Number>> list = series.getData();
        Iterator<XYChart.Data<Number, Number>> itr = list.iterator();
        if (itr.hasNext()) {
            XYChart.Data<Number, Number> prev = itr.next();
            XYChart.Data<Number, Number> current = prev;
            while (itr.hasNext()) {
                current = itr.next();
                prev.setYValue(current.getYValue());
                prev = current;
            }
            current.setYValue(value);
        }
    }

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initTableView();

        memoryCanvas.widthProperty().bind(memoryStatusVBox.widthProperty().subtract(5));
        memoryCanvas.heightProperty().bind(memoryStatusVBox.widthProperty().multiply(0.5));
        diskCanvas.widthProperty().bind(diskStatusVBox.widthProperty().subtract(5));
        diskCanvas.heightProperty().bind(diskStatusVBox.widthProperty().multiply(0.5));

        setUpChart(kernelUsageChart, kernelUsageSeries);
        setUpChart(memoryUsageChart, memoryUsageSeries);
    }

    void init(Kernel kernel) {
        this.kernel = kernel;
        memoryLabel.setText(String.format("%.0f Bytes/Block",
                kernel.getMemoryManager().getMaxUserSpaceSize() * 1.0 / MEMORY_USAGE_VIEW_TOTAL_COUNT));
    }

    private void initTimeLine() {
        overviewTimeline = new Timeline(new KeyFrame(Duration.millis(Kernel.CPU_PERIOD_MS), ae -> {
            kernelTime.setValue(String.valueOf(kernel.getTime()));
            timeSlice.setValue(String.valueOf(kernel.getTimeSlice()));
            ProcessControlBlock pcb = kernel.getProcessManager().getRunningProcess();
            runningProcessImage.setValue(pcb == null ? "IDLE" : String.valueOf(pcb.getImageFile().getPath()));
            runningPID.setValue(pcb == null ? "0" : String.valueOf(pcb.getPID()));
            Kernel.Context context = kernel.snapContext();
            intermediateResult.setValue(String.valueOf(context.getAX()));
            runningInstruction.setValue(Instruction.getName(context.getIR()));

            renderMemoryMap();

            if (++kernelUsageUpdateCounter > Kernel.INIT_TIME_SLICE) {
                long currentTime = kernel.getTime();
                long currentExecutionTime = kernel.getExecutionTime();
                lastKernelUsageRate = (lastKernelExecutionTime - currentExecutionTime) * 100.0
                        / (lastKernelTime - currentTime);
                kernelUsageUpdateCounter = 0;
                lastKernelTime = currentTime;
                lastKernelExecutionTime = currentExecutionTime;
            }

            updateSeries(kernelUsageSeries, lastKernelUsageRate);
            updateSeries(memoryUsageSeries, kernel.getMemoryManager().getAllocatedSize());
        }));
        overviewTimeline.setCycleCount(Animation.INDEFINITE);


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
            rootDir.refresh();
        }));
        diskTimeline.setCycleCount(Animation.INDEFINITE);
    }

    void handleHidden() {
        kernel.removeIntExitListener(intEndListener);
        overviewTimeline.stop();
        processesTimeline.stop();
        devicesTimeline.stop();
        diskTimeline.stop();
        rootDir.getChildren().clear();
    }

    void handleShown() {
        kernel.addIntExitListener(intEndListener);
        SingleSelectionModel<Tab> selectionModel = tabPane.getSelectionModel();
        selectionModel.selectLast();
        selectionModel.selectFirst();
        rootDir.refresh();
        rootDir.setExpanded(true);
    }

    public void handleOverviewTabSelectionChanged(Event event) {
        if (overviewTab.isSelected()) {
            overviewTimeline.play();
        } else {
            overviewTimeline.stop();
        }
    }

    public void handleProcessesTabSelectionChanged(Event event) {
        if (processesTab.isSelected()) {
            processesTimeline.play();
        } else {
            processesTimeline.stop();
        }
    }

    public void handleDevicesTabSelectionChanged(Event event) {
        if (devicesTab.isSelected()) {
            devicesTimeline.play();
        } else {
            devicesTimeline.stop();
        }
    }

    public void handleDiskTabSelectionChanged(Event event) {
        if (diskTab.isSelected()) {
            fileTreeTableView.refresh();
            renderDiskMap();
            diskTimeline.play();
        } else {
            diskTimeline.stop();
        }
    }

    public void handleFileTreeKeyPressed(KeyEvent keyEvent) {
        switch (keyEvent.getCode()) {
            case F5: {
                rootDir.refresh();
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

    private void initTableView() {
        overviewTable.setItems(overviewList);
        overviewItemCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        overviewValueCol.setCellValueFactory(new PropertyValueFactory<>("value"));

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
    }

    private void renderMemoryMap() {
        GraphicsContext context = memoryCanvas.getGraphicsContext2D();
        context.clearRect(0, 0, memoryCanvas.getWidth(), memoryCanvas.getHeight());
        double size = memoryCanvas.getWidth() / MEMORY_USAGE_VIEW_COL_COUNT;
        double space = size * 0.1, blockSize = size * 0.9;

        MemoryManager memoryManager = kernel.getMemoryManager();
        double blockLogicSize = (double) memoryManager.getMaxUserSpaceSize() / MEMORY_USAGE_VIEW_COL_COUNT / MEMORY_USAGE_VIEW_ROW_COUNT;

        Consumer<MemoryManager.Space> drawBlock = e -> {
            double logicStartAddress = e.startAddress / blockLogicSize;
            double logicEndAddress = (e.startAddress + e.size) / blockLogicSize;

            for (double logicAddress = logicStartAddress; logicAddress < logicEndAddress; ++logicAddress) {
                int row = (int) (logicAddress / MEMORY_USAGE_VIEW_COL_COUNT);
                int column = (int) (logicAddress % MEMORY_USAGE_VIEW_COL_COUNT);
                double x = column * size;
                double y = row * size;
                context.fillRect(x, y, blockSize, blockSize);
            }
        };

        context.setFill(Color.LIGHTGREEN);
        List<MemoryManager.Space> freeSpaces = memoryManager.getFreeSpaces();
        freeSpaces.forEach(drawBlock);

        context.setFill(Color.GREEN);
        List<MemoryManager.Space> allocatedSpaces = memoryManager.getAllocatedSpaces();
        allocatedSpaces.forEach(drawBlock);
    }

    private void renderDiskMap() {
        GraphicsContext context = diskCanvas.getGraphicsContext2D();
        context.clearRect(0, 0, diskCanvas.getWidth(), diskCanvas.getHeight());
        FileSystem fs = FileSystem.getFileSystem();
        final int col = 16;
        final double size = diskCanvas.getWidth() / col;
        double x = 0, y = 0, length = size * 0.9;
        final double space = size * 0.1;
        for (int i = 0; i < 64 * 2; ++i) {
            context.setFill(fs.read(i) == 0 ? Color.LIGHTGREEN : Color.GREEN);
            context.fillRect(x, y, length, length);
            x += length + space;
            if (i % col == col - 1) {
                x = 0;
                y += length + space;
            }
        }
    }

    public static class OverviewItem {
        private final StringProperty name = new SimpleStringProperty(this, "name");
        private final StringProperty value = new SimpleStringProperty(this, "value");

        OverviewItem(String name, String value) {
            setName(name);
            setValue(value);
        }

        public String getName() {
            return nameProperty().get();
        }

        public void setName(String value) {
            nameProperty().set(value);
        }

        public StringProperty nameProperty() {
            return name;
        }

        public String getValue() {
            return valueProperty().get();
        }

        public void setValue(String value) {
            valueProperty().set(value);
        }

        public StringProperty valueProperty() {
            return value;
        }
    }

    private class FileTreeItem extends TreeItem<FilePath> {
        private boolean childrenLoaded = false;

        FileTreeItem(FilePath path, ImageView folderIcon) {
            super(path, folderIcon);
        }

        void loadChildren() {
            FilePath filePath = getValue();
            ObservableList<TreeItem<FilePath>> children = super.getChildren();
            for (FilePath f : filePath.list()) {
                children.add(createFileTreeItem(f));
            }
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
            fileTreeTableView.refresh();
            if (childrenLoaded) {
                return super.getChildren();
            }
            childrenLoaded = true;
            loadChildren();
            return super.getChildren();
        }

        void refresh() {
            ObservableList<TreeItem<FilePath>> children = getChildren();
            Iterator<TreeItem<FilePath>> iterator = children.iterator();
            while (iterator.hasNext()) {
                FileTreeItem current = (FileTreeItem) iterator.next();
                FilePath file = current.getValue();
                try {
                    if (file.isDir()) {
                        current.refresh();
                    }
                } catch (FileNotFoundException ignored) {
                    iterator.remove();
                }
            }
            FilePath parent = getValue();
            FilePath[] updatedFiles = parent.list();
            Arrays.stream(updatedFiles)
                    .filter(file -> children.stream()
                            .noneMatch(filePathTreeItem -> filePathTreeItem.getValue().equals(file)))
                    .forEach(file -> children.add(createFileTreeItem(file)));
        }

        private FileTreeItem createFileTreeItem(FilePath file) {
            Image image = null;
            try {
                image = file.isDir() ? folderIcon : fileIcon;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            FileTreeItem fileTreeItem = new FileTreeItem(file, new ImageView(image));
            FilePath[] filePaths = file.list();
            fileTreeItem.setExpanded(filePaths != null && filePaths.length == 0);
            return fileTreeItem;
        }
    }
}
