package globalquake.playground;

import com.fasterxml.jackson.databind.ObjectMapper;
import globalquake.core.GlobalQuake;
import globalquake.core.earthquake.EarthquakeAnalysis;
import globalquake.core.earthquake.data.Earthquake;
import globalquake.core.events.specific.QuakeRemoveEvent;
import globalquake.core.station.AbstractStation;
import globalquake.main.Main;
import globalquake.plum.PlumService;
import globalquake.ui.globalquake.EarthquakeListPanel;
import globalquake.ui.globalquake.GlobalQuakeFrame;
import globalquake.ui.globalquake.GlobalQuakePanel;
import globalquake.ui.i18n.I18n;
import org.tinylog.Logger;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
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

import org.w3c.dom.Document;

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
        JMenu fileMenu = new JMenu(I18n.get("playground.menu.file"));

        JMenuItem exportStationsItem = new JMenuItem(I18n.get("playground.menu.exportStations"));
        exportStationsItem.addActionListener(e -> showExportDialog());
        fileMenu.add(exportStationsItem);

        menuBar.add(fileMenu);

        // Playground menu
        JMenu playgroundMenu = new JMenu(I18n.get("playground.menu.playground"));

        JMenuItem clearQuakesItem = new JMenuItem(I18n.get("playground.menu.clearQuakes"));
        clearQuakesItem.addActionListener(e -> clearEarthquakesAndResetWaveforms());
        playgroundMenu.add(clearQuakesItem);

        JMenuItem deleteStationsItem = new JMenuItem(I18n.get("playground.menu.deleteStations"));
        deleteStationsItem.addActionListener(e -> deleteAllStations());
        playgroundMenu.add(deleteStationsItem);

        playgroundMenu.addSeparator();

        JMenuItem customQuakeItem = new JMenuItem(I18n.get("playground.menu.customQuake"));
        customQuakeItem.addActionListener(e -> showCustomEarthquakeDialog());
        playgroundMenu.add(customQuakeItem);

        menuBar.add(playgroundMenu);

        // Help menu
        JMenu helpMenu = new JMenu(I18n.get("playground.menu.help"));

        JMenuItem keybindingsItem = new JMenuItem(I18n.get("playground.menu.keybindings"));
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
                    I18n.get("playground.export.noStations"),
                    I18n.get("playground.export.title"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Create dialog
        JDialog exportDialog = new JDialog(this, I18n.get("playground.export.title"), true);
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
        formPanel.add(new JLabel(I18n.get("playground.export.datasetName")), gbc);

        JTextField nameField = new JTextField(25);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(nameField, gbc);

        // Author field
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel(I18n.get("playground.export.author")), gbc);

        JTextField authorField = new JTextField(25);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(authorField, gbc);

        // Description field
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        gbc.gridy = GridBagConstraints.NORTH;
        formPanel.add(new JLabel(I18n.get("playground.export.description")), gbc);

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
        JLabel infoLabel = new JLabel(I18n.format("playground.export.stationCount", stationCount));
        infoLabel.setForeground(Color.GRAY);
        formPanel.add(infoLabel, gbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton exportBtn = new JButton(I18n.get("playground.export.export"));
        JButton cancelBtn = new JButton(I18n.get("common.cancel"));
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
                        I18n.get("playground.export.needName"),
                        I18n.get("common.warning"),
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                exportStations(name, author, description, stationManager);
                JOptionPane.showMessageDialog(exportDialog,
                        I18n.format("playground.export.success", stationCount),
                        I18n.get("playground.export.complete"),
                        JOptionPane.INFORMATION_MESSAGE);
                exportDialog.dispose();
            } catch (Exception ex) {
                Logger.error("Export failed", ex);
                JOptionPane.showMessageDialog(exportDialog,
                        I18n.format("playground.export.failed", ex.getMessage()),
                        I18n.get("common.error"),
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
        String message = "<html><b>" + I18n.get("playground.keybindings.title") + "</b><br><br>" +
                "<b>" + I18n.get("playground.keybindings.stationOps") + "</b><br>" +
                "[R] " + I18n.get("playground.keybindings.r") + "<br>" +
                "[T] " + I18n.get("playground.keybindings.t") + "<br>" +
                "[I] " + I18n.get("playground.keybindings.i") + "<br><br>" +
                "<b>" + I18n.get("playground.keybindings.brushTools") + "</b><br>" +
                "[B] " + I18n.get("playground.keybindings.b") + "<br>" +
                "[D] " + I18n.get("playground.keybindings.d") + "<br>" +
                "[U] " + I18n.get("playground.keybindings.u") + "<br>" +
                "[J] " + I18n.get("playground.keybindings.j") + "<br><br>" +
                "<b>" + I18n.get("playground.keybindings.quakeOps") + "</b><br>" +
                "[E] " + I18n.get("playground.keybindings.e") + "<br>" +
                "[F] " + I18n.get("playground.keybindings.f") + "<br><br>" +
                "<b>" + I18n.get("playground.keybindings.timeView") + "</b><br>" +
                "[" + I18n.get("playground.keybindings.arrows") + "] " + I18n.get("playground.keybindings.arrowsDesc") + "<br>" +
                "[Space] " + I18n.get("playground.keybindings.space") + "<br>" +
                "[ESC] " + I18n.get("playground.keybindings.esc") + "<br><br>" +
                "<b>" + I18n.get("playground.keybindings.sidebar") + "</b><br>" +
                I18n.get("playground.keybindings.sidebarDesc") + "</html>";

        JOptionPane.showMessageDialog(this, message, I18n.get("playground.keybindings.title"),
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
                I18n.format("playground.clearQuakes.confirm", quakeCount, stationCount),
                I18n.get("playground.menu.clearQuakes"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        // 0. 重置 PLUM 网格与警报状态
        PlumService.resetIfPresent();

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
                    I18n.get("playground.deleteStations.none"),
                    I18n.get("playground.menu.deleteStations"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                I18n.format("playground.deleteStations.confirm", stationCount),
                I18n.get("playground.menu.deleteStations"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        // 清空 PLUM 状态（stationFirstExceed 等持有站引用，不清理会导致被删站及其
        // 数百 KB 波形缓冲无法回收，几轮模拟后堆持续增长 → GC 压力 → 帧率下降/卡顿）
        PlumService.resetIfPresent();

        for (AbstractStation station : stationManager.getStations()) {
            station.clear();
        }
        stationManager.getStations().clear();
        GlobalStationManagerPlayground.createListOfClosestStations(stationManager.getStations());

        Logger.info("Deleted all " + stationCount + " stations.");
    }

    // 记住上次「自定义地震」菜单的配置（震级/经纬度/深度/延迟），下次打开默认沿用；经纬度首次用视图中心
    private static double lastCustomMag = 4.0;
    private static Double lastCustomLat = null;
    private static Double lastCustomLon = null;
    private static double lastCustomDepth = 10.0;
    private static int lastCustomDelay = 0;

    /**
     * 自定义模拟地震参数对话框：输入震级、震中经纬度、震源深度、发震延迟（秒）。
     */
    private void showCustomEarthquakeDialog() {
        GlobalQuakePanel gqPanel = (GlobalQuakePanel) panel;
        // 记住上次自定义地震配置（震级/经纬度/深度/延迟），下次打开对话框默认沿用；经纬度首次用视图中心
        double defaultLat = lastCustomLat != null ? lastCustomLat : gqPanel.getRenderer().getRenderProperties().centerLat;
        double defaultLon = lastCustomLon != null ? lastCustomLon : gqPanel.getRenderer().getRenderProperties().centerLon;

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField magField = new JTextField(String.valueOf(lastCustomMag), 10);
        JTextField latField = new JTextField(String.format("%.4f", defaultLat), 10);
        JTextField lonField = new JTextField(String.format("%.4f", defaultLon), 10);
        JTextField depthField = new JTextField(String.valueOf(lastCustomDepth), 10);
        JTextField delayField = new JTextField(String.valueOf(lastCustomDelay), 10);

        String[][] fields = {
                {I18n.get("playground.custom.magnitude"), String.valueOf(lastCustomMag)},
                {I18n.get("playground.custom.latitude"), String.format("%.4f", defaultLat)},
                {I18n.get("playground.custom.longitude"), String.format("%.4f", defaultLon)},
                {I18n.get("playground.custom.depth"), String.valueOf(lastCustomDepth)},
                {I18n.get("playground.custom.delay"), String.valueOf(lastCustomDelay)}
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

        // 从 QuakeML 文件导入参数（自动填充上方字段）
        gbc.gridx = 0; gbc.gridy = fields.length; gbc.gridwidth = 2; gbc.weightx = 0.0;
        JButton importBtn = new JButton(I18n.get("playground.custom.importQuakeML"));
        importBtn.addActionListener(e -> importFromQuakeML(magField, latField, lonField, depthField));
        formPanel.add(importBtn, gbc);

        int result = JOptionPane.showConfirmDialog(this, formPanel,
                I18n.get("playground.custom.title"),
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
                    I18n.format("playground.custom.invalidNumber", e.getMessage()),
                    I18n.get("common.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (mag < 0 || mag > 10) {
            JOptionPane.showMessageDialog(this, I18n.get("playground.custom.magRange"), I18n.get("common.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (lat < -90 || lat > 90) {
            JOptionPane.showMessageDialog(this, I18n.get("playground.custom.latRange"), I18n.get("common.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (lon < -180 || lon > 180) {
            JOptionPane.showMessageDialog(this, I18n.get("playground.custom.lonRange"), I18n.get("common.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (depth < 0 || depth > 700) {
            JOptionPane.showMessageDialog(this, I18n.get("playground.custom.depthRange"), I18n.get("common.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (delaySec < 0) {
            JOptionPane.showMessageDialog(this, I18n.get("playground.custom.delayRange"), I18n.get("common.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }

        final double fMag = mag, fLat = lat, fLon = lon, fDepth = depth;
        lastCustomMag = mag;
        lastCustomLat = lat;
        lastCustomLon = lon;
        lastCustomDepth = depth;
        lastCustomDelay = delaySec;

        if (delaySec == 0) {
            ((GlobalQuakePanelPlayground) panel)._createDebugEarthquake(fMag, fDepth, fLat, fLon);
        } else {
            JOptionPane.showMessageDialog(this,
                    I18n.format("playground.custom.scheduled", delaySec, fMag, fLat, fLon, fDepth),
                    I18n.get("playground.custom.scheduledTitle"),
                    JOptionPane.INFORMATION_MESSAGE);
            javax.swing.Timer timer = new javax.swing.Timer(delaySec * 1000, ev -> {
                ((GlobalQuakePanelPlayground) panel)._createDebugEarthquake(fMag, fDepth, fLat, fLon);
            });
            timer.setRepeats(false);
            timer.start();
        }
    }

    private void importFromQuakeML(JTextField magField, JTextField latField, JTextField lonField, JTextField depthField) {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("QuakeML (*.xml)", "xml"));
        // 默认打开运行目录下的 quakeml 文件夹（不存在则自动创建）
        File quakemlDir = new File("quakeml");
        if (!quakemlDir.exists() && !quakemlDir.mkdirs()) {
            Logger.warn("Unable to create quakeml folder: " + quakemlDir.getAbsolutePath());
        } else {
            fc.setCurrentDirectory(quakemlDir);
        }
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            double[] vals = parseQuakeML(fc.getSelectedFile());
            magField.setText(String.valueOf(vals[0]));
            latField.setText(String.format("%.4f", vals[1]));
            lonField.setText(String.format("%.4f", vals[2]));
            depthField.setText(String.valueOf(vals[3]));
        } catch (Exception ex) {
            Logger.error(ex);
            JOptionPane.showMessageDialog(this,
                    I18n.format("playground.custom.importFailed", ex.getMessage()),
                    I18n.get("common.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 解析 QuakeML（fdsn 1.2）文件，提取震级、震中经纬度、震源深度（km）。
     * 优先使用 preferredMagnitudeID / preferredOriginID 指向的块；缺失时退回第一个匹配块。
     */
    private static double[] parseQuakeML(File file) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        // XXE 防护：禁止 DTD/外部实体
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setExpandEntityReferences(false);
        Document doc = dbf.newDocumentBuilder().parse(file);
        XPath xpath = XPathFactory.newInstance().newXPath();

        String prefOrigin = text(xpath, doc, "//*[local-name()='event']/*[local-name()='preferredOriginID']");
        String prefMag = text(xpath, doc, "//*[local-name()='event']/*[local-name()='preferredMagnitudeID']");

        String magExpr = prefMag != null
                ? "//*[local-name()='magnitude' and @publicID='" + prefMag + "']//*[local-name()='mag']/*[local-name()='value']"
                : "(//*[local-name()='magnitude']//*[local-name()='mag']/*[local-name()='value'])[1]";
        String originExpr = prefOrigin != null
                ? "//*[local-name()='origin' and @publicID='" + prefOrigin + "']"
                : "(//*[local-name()='origin'])[1]";

        String magStr = text(xpath, doc, magExpr);
        String latStr = text(xpath, doc, originExpr + "//*[local-name()='latitude']/*[local-name()='value']");
        String lonStr = text(xpath, doc, originExpr + "//*[local-name()='longitude']/*[local-name()='value']");
        String depthStr = text(xpath, doc, originExpr + "//*[local-name()='depth']/*[local-name()='value']");

        if (magStr == null || latStr == null || lonStr == null) {
            throw new IllegalArgumentException("QuakeML missing magnitude/latitude/longitude");
        }
        double mag = Double.parseDouble(magStr);
        double lat = Double.parseDouble(latStr);
        double lon = Double.parseDouble(lonStr);
        double depth = depthStr == null ? 10.0 : Double.parseDouble(depthStr) / 1000.0; // QuakeML depth 单位米 → km
        return new double[]{mag, lat, lon, depth};
    }

    private static String text(XPath xpath, Document doc, String expr) throws XPathExpressionException {
        String v = xpath.evaluate(expr, doc);
        return v == null || v.trim().isEmpty() ? null : v.trim();
    }

}