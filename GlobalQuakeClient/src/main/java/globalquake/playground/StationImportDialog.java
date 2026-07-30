package globalquake.playground;

import globalquake.core.GlobalQuake;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

public class StationImportDialog extends JDialog {

    private final List<CsvStationLoader.StationFolderInfo> stationFolders;
    private String selectedCsvPath;
    private Set<String> selectedNetworks;
    private int maxCount;
    private boolean noLimit = false;
    private boolean clearExisting = true;

    public StationImportDialog(Frame parent, String stationFolderPath) {
        super(parent, "Import Stations", true);
        this.stationFolders = CsvStationLoader.scanStationFolders(stationFolderPath);
        this.selectedNetworks = new HashSet<>();
        this.maxCount = 1000;

        setSize(650, 700);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        if (stationFolders.isEmpty()) {
            showEmptyDialog(parent);
            return;
        }

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Top panel: Step 1 + Details stacked vertically
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        // Step 1 - Select station folder
        JPanel step1Panel = new JPanel(new BorderLayout(5, 5));
        step1Panel.setBorder(BorderFactory.createTitledBorder("Step 1: Select Station Dataset"));
        step1Panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        step1Panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        DefaultListModel<String> fileListModel = new DefaultListModel<>();
        for (CsvStationLoader.StationFolderInfo info : stationFolders) {
            fileListModel.addElement(info.name() + " (" + info.stationCount() + " stations)");
        }

        JList<String> fileList = new JList<>(fileListModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.setFont(new Font("SansSerif", Font.PLAIN, 13));
        fileList.setSelectedIndex(0);

        JScrollPane fileScrollPane = new JScrollPane(fileList);
        fileScrollPane.setPreferredSize(new Dimension(600, 80));
        step1Panel.add(fileScrollPane, BorderLayout.CENTER);

        topPanel.add(step1Panel);
        topPanel.add(Box.createVerticalStrut(5));

        // Details panel
        JPanel detailsPanel = new JPanel(new BorderLayout(5, 5));
        detailsPanel.setBorder(BorderFactory.createTitledBorder("Dataset Details"));
        detailsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        detailsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JEditorPane detailsArea = new JEditorPane("text/html", "");
        detailsArea.setEditable(false);
        detailsArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        detailsPanel.add(new JScrollPane(detailsArea), BorderLayout.CENTER);

        topPanel.add(detailsPanel);

        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Middle: Step 2 - Select networks
        JPanel step2Panel = new JPanel(new BorderLayout(5, 5));
        step2Panel.setBorder(BorderFactory.createTitledBorder("Step 2: Select Networks/Regions"));

        JPanel checkBoxPanel = new JPanel();
        checkBoxPanel.setLayout(new BoxLayout(checkBoxPanel, BoxLayout.Y_AXIS));

        JScrollPane networkScrollPane = new JScrollPane(checkBoxPanel);
        networkScrollPane.setPreferredSize(new Dimension(600, 150));
        step2Panel.add(networkScrollPane, BorderLayout.CENTER);

        JPanel selectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton selectAllBtn = new JButton("Select All");
        JButton deselectAllBtn = new JButton("Deselect All");
        step2Panel.add(selectPanel, BorderLayout.SOUTH);
        selectPanel.add(selectAllBtn);
        selectPanel.add(deselectAllBtn);

        mainPanel.add(step2Panel, BorderLayout.CENTER);

        // Bottom: Step 3 - Station count + buttons
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));

        JPanel step3Panel = new JPanel(new BorderLayout(5, 5));
        step3Panel.setBorder(BorderFactory.createTitledBorder("Step 3: Station Count Limit"));

        JLabel countLabel = new JLabel("Max stations to import: 100");
        countLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JSlider slider = new JSlider(JSlider.HORIZONTAL, 1, 50000, 100);
        slider.setPaintTicks(false);
        slider.setPaintLabels(false);

        slider.addChangeListener(e -> {
            maxCount = slider.getValue();
            countLabel.setText("Max stations to import: " + maxCount);
        });

        step3Panel.add(countLabel, BorderLayout.NORTH);
        step3Panel.add(slider, BorderLayout.CENTER);

        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        JCheckBox noLimitCheckBox = new JCheckBox("No limit (import all selected)", false);
        JCheckBox clearExistingCheckBox = new JCheckBox("Clear existing stations before import", true);
        clearExistingCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        noLimitCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        optionsPanel.add(noLimitCheckBox);
        optionsPanel.add(clearExistingCheckBox);
        step3Panel.add(optionsPanel, BorderLayout.SOUTH);

        clearExistingCheckBox.addActionListener(e -> clearExisting = clearExistingCheckBox.isSelected());

        bottomPanel.add(step3Panel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("Import");
        JButton cancelBtn = new JButton("Cancel");
        buttonPanel.add(okBtn);
        buttonPanel.add(cancelBtn);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel);

        // Network checkbox map
        final Map<String, JCheckBox> networkCheckBoxMap = new LinkedHashMap<>();

        // Function to load details and networks when selection changes
        Runnable loadSelection = () -> {
            int fileIndex = fileList.getSelectedIndex();
            if (fileIndex < 0) return;

            CsvStationLoader.StationFolderInfo selectedFolder = stationFolders.get(fileIndex);
            selectedCsvPath = selectedFolder.csvPath();

            // Update details
            StringBuilder details = new StringBuilder();
            details.append("<html>");
            details.append("<b>Name:</b> ").append(selectedFolder.name()).append("<br>");
            details.append("<b>Author:</b> ").append(selectedFolder.author()).append("<br>");
            details.append("<b>Total Stations:</b> ").append(selectedFolder.stationCount()).append("<br>");
            details.append("<br>");
            details.append("<b>Description:</b><br>").append(selectedFolder.description());
            details.append("</html>");
            detailsArea.setText(details.toString());
            detailsArea.setCaretPosition(0);

            // Load networks
            List<String> networks = CsvStationLoader.getNetworks(selectedCsvPath);

            checkBoxPanel.removeAll();
            networkCheckBoxMap.clear();

            for (String network : networks) {
                JCheckBox cb = new JCheckBox(network, true);
                cb.setAlignmentX(Component.LEFT_ALIGNMENT);
                networkCheckBoxMap.put(network, cb);
                checkBoxPanel.add(cb);
            }

            // Update slider max based on actual station count
            int stationCount = selectedFolder.stationCount();
            slider.setMaximum(Math.max(1, stationCount));

            if (maxCount > stationCount) {
                maxCount = stationCount;
                slider.setValue(maxCount);
            }
            countLabel.setText("Max stations to import: " + maxCount);

            checkBoxPanel.revalidate();
            checkBoxPanel.repaint();
        };

        // Initial load
        loadSelection.run();

        fileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelection.run();
            }
        });

        selectAllBtn.addActionListener(e -> {
            for (JCheckBox cb : networkCheckBoxMap.values()) {
                cb.setSelected(true);
            }
        });

        deselectAllBtn.addActionListener(e -> {
            for (JCheckBox cb : networkCheckBoxMap.values()) {
                cb.setSelected(false);
            }
        });

        noLimitCheckBox.addActionListener(e -> {
            boolean selected = noLimitCheckBox.isSelected();
            noLimit = selected;
            slider.setEnabled(!selected);
            if (selected) {
                countLabel.setText("Max stations to import: ALL");
            } else {
                maxCount = slider.getValue();
                countLabel.setText("Max stations to import: " + maxCount);
            }
        });

        okBtn.addActionListener((ActionEvent e) -> {
            selectedNetworks = new HashSet<>();
            for (Map.Entry<String, JCheckBox> entry : networkCheckBoxMap.entrySet()) {
                if (entry.getValue().isSelected()) {
                    selectedNetworks.add(entry.getKey());
                }
            }

            if (selectedNetworks.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Please select at least one network!",
                        "Warning",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            dispose();
        });

        cancelBtn.addActionListener(e -> {
            selectedNetworks = null;
            dispose();
        });

        setVisible(true);
    }

    public String getSelectedCsvPath() {
        return selectedCsvPath;
    }

    public Set<String> getSelectedNetworks() {
        return selectedNetworks;
    }

    public int getMaxCount() {
        return noLimit ? -1 : maxCount;
    }

    public boolean isClearExisting() {
        return clearExisting;
    }

    public boolean isImportAllNetworks() {
        return selectedNetworks != null && selectedNetworks.isEmpty();
    }

    private void showEmptyDialog(Frame parent) {
        JOptionPane.showMessageDialog(this,
                "No station datasets found!\n\n" +
                "Please create station folders in:\n" +
                "  playground_stations/<dataset_name>/stations.csv\n" +
                "  playground_stations/<dataset_name>/info.json",
                "No Station Datasets",
                JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }
}
