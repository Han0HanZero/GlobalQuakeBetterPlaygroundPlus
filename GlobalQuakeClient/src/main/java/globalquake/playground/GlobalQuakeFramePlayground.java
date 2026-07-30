package globalquake.playground;

import com.fasterxml.jackson.databind.ObjectMapper;
import globalquake.core.GlobalQuake;
import globalquake.core.earthquake.EarthquakeAnalysis;
import globalquake.core.earthquake.data.Earthquake;
import globalquake.core.events.specific.QuakeRemoveEvent;
import globalquake.core.station.AbstractStation;
import globalquake.main.Main;
import globalquake.ui.globalquake.EarthquakeListPanel;
import globalquake.ui.globalquake.GlobalQuakeFrame;
import globalquake.ui.globalquake.GlobalQuakePanel;
import org.tinylog.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GlobalQuakeFramePlayground extends GlobalQuakeFrame {

    private boolean hideList = false;
    private boolean _containsListToggle;
    private EarthquakeListPanel list;

    public GlobalQuakeFramePlayground() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        panel = createGQPanel();

        panel.setPreferredSize(new Dimension(800, 760));

        list = new EarthquakeListPanel(this, GlobalQuake.instance.getArchive().getArchivedQuakes());
        list.setPreferredSize(new Dimension(300, 760));

        mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());
        mainPanel.add(panel, BorderLayout.CENTER);
        mainPanel.add(list, BorderLayout.EAST);

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                _containsListToggle = false;
            }
        });

        panel.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int x = e.getX();
                int y = e.getY();
                _containsListToggle = x >= panel.getWidth() - 30 && x <= panel.getWidth() && y >= 0 && y <= 30;
            }
        });

        setContentPane(mainPanel);

        setJMenuBar(createJMenuBar());

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> mainPanel.repaint(), 1, 1000 / 60, TimeUnit.MILLISECONDS);

        pack();
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(320, 300));
        setResizable(true);
        setTitle(Main.fullName);
    }

    protected GlobalQuakePanel createGQPanel() {
        return new GlobalQuakePanelPlayground(this);
    }

    protected void toggleList() {
        hideList = !hideList;
        list.setVisible(!hideList);
        _containsListToggle = false;
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    public boolean containsListToggle() {
        return _containsListToggle;
    }

    public boolean isListHidden() {
        return hideList;
    }

    @Override
    protected JMenuBar createJMenuBar() {
        JMenuBar menuBar = super.createJMenuBar();

        // File menu
        JMenu fileMenu = new JMenu("File");

        JMenuItem exportStationsItem = new JMenuItem("Export Stations...");
        exportStationsItem.addActionListener(e -> showExportDialog());
        fileMenu.add(exportStationsItem);

        menuBar.add(fileMenu);

        // Playground menu
        JMenu playgroundMenu = new JMenu("Playground");

        JMenuItem clearQuakesItem = new JMenuItem("Clear Earthquakes & Reset Waveforms");
        clearQuakesItem.addActionListener(e -> clearEarthquakesAndResetWaveforms());
        playgroundMenu.add(clearQuakesItem);

        JMenuItem deleteStationsItem = new JMenuItem("Delete All Stations");
        deleteStationsItem.addActionListener(e -> deleteAllStations());
        playgroundMenu.add(deleteStationsItem);

        playgroundMenu.addSeparator();

        JMenuItem customQuakeItem = new JMenuItem("Custom Earthquake...");
        customQuakeItem.addActionListener(e -> showCustomEarthquakeDialog());
        playgroundMenu.add(customQuakeItem);

        menuBar.add(playgroundMenu);

        // Help menu
        JMenu helpMenu = new JMenu("Help");

        JMenuItem keybindingsItem = new JMenuItem("Keybindings");
        keybindingsItem.addActionListener(e -> showKeybindingsDialog());
        helpMenu.add(keybindingsItem);

        menuBar.add(helpMenu);

        return menuBar;
    }

    private void showExportDialog() {
        GlobalQuakePlayground gq = (GlobalQuakePlayground) GlobalQuake.instance;
        GlobalStationManagerPlayground stationManager = (GlobalStationManagerPlayground) gq.getStationManager();

        int stationCount = stationManager.getStations().size();
        if (stationCount == 0) {
            JOptionPane.showMessageDialog(this,
                    "No stations to export!\n\nPlease add stations first using import or brush tools.",
                    "Export Stations",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Create dialog
        JDialog exportDialog = new JDialog(this, "Export Stations", true);
        exportDialog.setSize(450, 380);
        exportDialog.setLocationRelativeTo(this);
        exportDialog.setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Name field
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel("Dataset Name:"), gbc);

        JTextField nameField = new JTextField(25);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(nameField, gbc);

        // Author field
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel("Author:"), gbc);

        JTextField authorField = new JTextField(25);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(authorField, gbc);

        // Description field
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        gbc.gridy = GridBagConstraints.NORTH;
        formPanel.add(new JLabel("Description:"), gbc);

        JTextArea descArea = new JTextArea(5, 25);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        JScrollPane descScroll = new JScrollPane(descArea);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        formPanel.add(descScroll, gbc);

        // Info label
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel infoLabel = new JLabel("Stations to export: " + stationCount);
        infoLabel.setForeground(Color.GRAY);
        formPanel.add(infoLabel, gbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton exportBtn = new JButton("Export");
        JButton cancelBtn = new JButton("Cancel");
        buttonPanel.add(exportBtn);
        buttonPanel.add(cancelBtn);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        exportDialog.add(mainPanel);

        exportBtn.addActionListener(ev -> {
            String name = nameField.getText().trim();
            String author = authorField.getText().trim();
            String description = descArea.getText().trim();

            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(exportDialog,
                        "Please enter a dataset name!",
                        "Warning",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                exportStations(name, author, description, stationManager);
                JOptionPane.showMessageDialog(exportDialog,
                        "Successfully exported " + stationCount + " stations!",
                        "Export Complete",
                        JOptionPane.INFORMATION_MESSAGE);
                exportDialog.dispose();
            } catch (Exception ex) {
                Logger.error("Export failed", ex);
                JOptionPane.showMessageDialog(exportDialog,
                        "Export failed: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelBtn.addActionListener(ev -> exportDialog.dispose());

        exportDialog.setVisible(true);
    }

    private void exportStations(String name, String author, String description,
                                GlobalStationManagerPlayground stationManager) throws Exception {
        GlobalQuakePlayground gq = (GlobalQuakePlayground) GlobalQuake.instance;
        String stationsFolderPath = gq.getStationsFolderPath();

        // Create safe folder name from dataset name
        String safeName = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (safeName.isEmpty()) safeName = "exported_stations";
        safeName = safeName.toLowerCase();

        File exportFolder = new File(stationsFolderPath, safeName);
        exportFolder.mkdirs();

        // Write stations.csv
        File csvFile = new File(exportFolder, "stations.csv");
        Collection<AbstractStation> stations = stationManager.getStations();

        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(csvFile, StandardCharsets.UTF_8))) {
            // Write header
            writer.write("Network,Station,Name,Latitude,Longitude,Elevation,Sensitivity,Site,Vs30");
            writer.newLine();

            // Write station data
            for (AbstractStation station : stations) {
                writer.write(String.format("%s,%s,%s,%.6f,%.6f,%.1f,%.1f,%s,%s",
                        station.getNetworkCode(),
                        station.getStationCode(),
                        station.getIdentifier(),
                        station.getLatitude(),
                        station.getLongitude(),
                        station.getAlt(),
                        station.getSensitivity(),
                        "",
                        ""));
                writer.newLine();
            }
        }

        // Write info.json
        Map<String, Object> infoMap = new LinkedHashMap<>();
        infoMap.put("name", name);
        infoMap.put("author", author.isEmpty() ? "Unknown" : author);
        infoMap.put("description", description);

        ObjectMapper mapper = new ObjectMapper();
        File infoFile = new File(exportFolder, "info.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(infoFile, infoMap);

        Logger.info("Exported " + stations.size() + " stations to: " + exportFolder.getAbsolutePath());
    }

    private void showKeybindingsDialog() {
        String message = "<html><b>Playground Mode Keybindings</b><br><br>" +
                "<b>Station Operations:</b><br>" +
                "[R] Random Stations (generates instantly)<br>" +
                "[T] Import Stations (Dialog - select region & count)<br>" +
                "[I] Import Stations (File Picker - load CSV directly)<br><br>" +
                "<b>Brush Tools:</b><br>" +
                "[B] Brush Add Mode - click/drag to add stations<br>" +
                "[D] Brush Delete Mode - click/drag to delete stations<br>" +
                "[U] Increase Brush Radius<br>" +
                "[J] Decrease Brush Radius<br><br>" +
                "<b>Earthquake Operations:</b><br>" +
                "[E] Create Earthquake (press then Space to execute)<br>" +
                "[F] Toggle Earthquake Display<br><br>" +
                "<b>Time & View:</b><br>" +
                "[Left/Right Arrow] Adjust Time (5 sec)<br>" +
                "[Space] Execute Pending Action<br>" +
                "[ESC] Clear All<br><br>" +
                "<b>Sidebar:</b><br>" +
                "Click the <b>></b> / <b><</b> button at the top-right corner<br>" +
                "to show/hide the earthquake list sidebar.</html>";

        JOptionPane.showMessageDialog(this, message, "Playground Keybindings",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 清除所有模拟地震并重置所有测站波形（保留测站）。
     * 测站颜色恢复蓝色，波形缓冲清空，分析状态重置。
     */
    private void clearEarthquakesAndResetWaveforms() {
        GlobalQuakePlayground gq = (GlobalQuakePlayground) GlobalQuake.instance;
        GlobalStationManagerPlayground stationManager = (GlobalStationManagerPlayground) gq.getStationManager();

        int quakeCount = gq.getPlaygroundEarthquakes().size();
        int stationCount = stationManager.getStations().size();

        int confirm = JOptionPane.showConfirmDialog(this,
                "Clear all simulated earthquakes and reset station waveforms?\n" +
                        "Earthquakes: " + quakeCount + "\n" +
                        "Stations: " + stationCount + " (kept, waveforms reset)\n\n" +
                        "Station colors will return to blue.",
                "Clear Earthquakes & Reset Waveforms",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        // 1. 清除 Playground 地震集合（波形生成器遍历这个集合产生信号）
        gq.getPlaygroundEarthquakes().clear();

        // 2. 触发 QuakeRemoveEvent 让 ShakemapService 等监听器清理烈度图/音效状态
        for (Earthquake eq : gq.getEarthquakeAnalysis().getEarthquakes()) {
            GlobalQuake.instance.getEventHandler().fireEvent(new QuakeRemoveEvent(eq));
        }

        // 3. 清 clusterAnalysis.clusters + earthquakeAnalysis.earthquakes
        GlobalQuake.instance.clear();

        // 4. 重置每个测站：颜色回蓝 + 地震距离缓存清掉 + 分析状态重置
        //    （resetPlaygroundStation() 内部包含 generator.reset() + station.clear()）
        for (AbstractStation station : stationManager.getStations()) {
            ((PlaygroundStation) station).resetPlaygroundStation();
        }

        // 5. 重建 nearbyStations：station.clear() 会清掉这个缓存，
        //    ClusterAnalysis 依赖它来把测站分配到 cluster，缺了就检测不到地震
        GlobalStationManagerPlayground.createListOfClosestStations(stationManager.getStations());

        Logger.info("Cleared " + quakeCount + " earthquakes, reset " + stationCount + " stations.");
    }

    /**
     * 立即删除所有现存测站。
     */
    private void deleteAllStations() {
        GlobalQuakePlayground gq = (GlobalQuakePlayground) GlobalQuake.instance;
        GlobalStationManagerPlayground stationManager = (GlobalStationManagerPlayground) gq.getStationManager();

        int stationCount = stationManager.getStations().size();
        if (stationCount == 0) {
            JOptionPane.showMessageDialog(this,
                    "No stations to delete.",
                    "Delete All Stations",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete all " + stationCount + " stations?\n" +
                        "This cannot be undone.",
                "Delete All Stations",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        for (AbstractStation station : stationManager.getStations()) {
            station.clear();
        }
        stationManager.getStations().clear();
        GlobalStationManagerPlayground.createListOfClosestStations(stationManager.getStations());

        Logger.info("Deleted all " + stationCount + " stations.");
    }

    /**
     * 自定义模拟地震参数对话框：输入震级、震中经纬度、震源深度、发震延迟（秒）。
     */
    private void showCustomEarthquakeDialog() {
        GlobalQuakePanel gqPanel = (GlobalQuakePanel) panel;
        double defaultLat = gqPanel.getRenderer().getRenderProperties().centerLat;
        double defaultLon = gqPanel.getRenderer().getRenderProperties().centerLon;

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField magField = new JTextField("4.0", 10);
        JTextField latField = new JTextField(String.format("%.4f", defaultLat), 10);
        JTextField lonField = new JTextField(String.format("%.4f", defaultLon), 10);
        JTextField depthField = new JTextField("10.0", 10);
        JTextField delayField = new JTextField("0", 10);

        String[][] fields = {
                {"Magnitude (0-10):", "4.0"},
                {"Latitude (-90 ~ 90):", String.format("%.4f", defaultLat)},
                {"Longitude (-180 ~ 180):", String.format("%.4f", defaultLon)},
                {"Depth (km, 0-700):", "10.0"},
                {"Delay (sec, 0=now):", "0"}
        };

        for (int i = 0; i < fields.length; i++) {
            gbc.gridx = 0; gbc.gridy = i; gbc.weightx = 0.0;
            formPanel.add(new JLabel(fields[i][0]), gbc);
            gbc.gridx = 1; gbc.weightx = 1.0;
            JTextField field = switch (i) {
                case 0 -> magField;
                case 1 -> latField;
                case 2 -> lonField;
                case 3 -> depthField;
                default -> delayField;
            };
            field.setText(fields[i][1]);
            formPanel.add(field, gbc);
        }

        int result = JOptionPane.showConfirmDialog(this, formPanel,
                "Custom Earthquake Parameters",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return;

        double mag, lat, lon, depth;
        int delaySec;
        try {
            mag = Double.parseDouble(magField.getText().trim());
            lat = Double.parseDouble(latField.getText().trim());
            lon = Double.parseDouble(lonField.getText().trim());
            depth = Double.parseDouble(depthField.getText().trim());
            delaySec = Integer.parseInt(delayField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                    "Invalid number format!\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (mag < 0 || mag > 10) {
            JOptionPane.showMessageDialog(this, "Magnitude must be 0-10!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (lat < -90 || lat > 90) {
            JOptionPane.showMessageDialog(this, "Latitude must be -90 ~ 90!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (lon < -180 || lon > 180) {
            JOptionPane.showMessageDialog(this, "Longitude must be -180 ~ 180!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (depth < 0 || depth > 700) {
            JOptionPane.showMessageDialog(this, "Depth must be 0-700 km!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (delaySec < 0) {
            JOptionPane.showMessageDialog(this, "Delay must be >= 0!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final double fMag = mag, fLat = lat, fLon = lon, fDepth = depth;

        if (delaySec == 0) {
            ((GlobalQuakePanelPlayground) panel)._createDebugEarthquake(fMag, fDepth, fLat, fLon);
        } else {
            JOptionPane.showMessageDialog(this,
                    "Earthquake will be created in " + delaySec + " seconds.\n" +
                            "Mag " + fMag + " at (" + fLat + ", " + fLon + "), depth " + fDepth + " km",
                    "Earthquake Scheduled",
                    JOptionPane.INFORMATION_MESSAGE);
            javax.swing.Timer timer = new javax.swing.Timer(delaySec * 1000, ev -> {
                ((GlobalQuakePanelPlayground) panel)._createDebugEarthquake(fMag, fDepth, fLat, fLon);
            });
            timer.setRepeats(false);
            timer.start();
        }
    }

}