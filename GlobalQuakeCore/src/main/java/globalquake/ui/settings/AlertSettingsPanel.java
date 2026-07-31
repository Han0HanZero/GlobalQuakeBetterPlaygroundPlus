package globalquake.ui.settings;

import globalquake.core.Settings;
import globalquake.core.earthquake.data.Cluster;
import globalquake.ui.i18n.I18n;

import javax.swing.*;
import java.awt.*;
import java.util.stream.IntStream;

public class AlertSettingsPanel extends SettingsPanel {

    private JCheckBox chkBoxLocal;
    private JTextField textFieldLocalDist;
    private JCheckBox chkBoxRegion;
    private JTextField textFieldRegionMag;
    private JTextField textFieldRegionDist;
    private JCheckBox checkBoxGlobal;
    private JTextField textFieldGlobalMag;
    private JLabel label1;
    private JCheckBox chkBoxFocus;
    private JCheckBox chkBoxJumpToAlert;
    private IntensityScaleSelector shakingThreshold;
    private IntensityScaleSelector strongShakingThreshold;
    private JCheckBox chkBoxPossibleShaking;
    private JTextField textFieldPossibleShakingDistance;
    private JCheckBox chkBoxEarthquakeSounds;
    private JTextField textFieldQuakeMinMag;
    private JTextField textFieldQuakeMaxDist;
    private JLabel label2;
    private IntensityScaleSelector eewThreshold;
    private JComboBox<Integer> comboBoxEEWClusterLevel;

    public AlertSettingsPanel() {
        setLayout(new BorderLayout());

        JTabbedPane tabbedPane = new JTabbedPane();

        tabbedPane.addTab(I18n.get("settings.alerts.tabWarnings"), createWarningsTab());
        tabbedPane.addTab(I18n.get("settings.alerts.tabPings"), createPingsTab());

        add(tabbedPane, BorderLayout.CENTER);

        refreshUI();
    }

    private Component createPingsTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        createPossibleShakingPanel(panel);
        createEarthquakeSoundsPanel(panel);

        JPanel eewThresholdPanel = new JPanel(new GridLayout(3,1));
        eewThresholdPanel.setBorder(BorderFactory.createTitledBorder("EEW"));
        eewThresholdPanel.add(new JLabel(I18n.get("settings.alerts.eewThreshold")));

        eewThresholdPanel.add(eewThreshold = new IntensityScaleSelector("",
                Settings.eewScale, Settings.eewLevelIndex));


        JPanel maxClusterLevelPanel = new JPanel();
        maxClusterLevelPanel.add(new JLabel(I18n.get("settings.alerts.eewClusterLevel")));

        comboBoxEEWClusterLevel = new JComboBox<>();
        for(int i : IntStream.rangeClosed(0, Cluster.MAX_LEVEL).toArray()){
            comboBoxEEWClusterLevel.addItem(i);
        }

        comboBoxEEWClusterLevel.setSelectedIndex(Settings.eewClusterLevel);

        maxClusterLevelPanel.add(comboBoxEEWClusterLevel);

        eewThresholdPanel.add(maxClusterLevelPanel);

        panel.add(eewThresholdPanel);

        fill(panel, 20);

        return panel;
    }

    private void createEarthquakeSoundsPanel(JPanel panel) {
        chkBoxEarthquakeSounds = new JCheckBox(I18n.get("settings.alerts.earthquakeSounds"), Settings.enableEarthquakeSounds);
        textFieldQuakeMinMag = new JTextField(String.valueOf(Settings.earthquakeSoundsMinMagnitude) ,12);
        textFieldQuakeMinMag.setEnabled(chkBoxEarthquakeSounds.isSelected());
        textFieldQuakeMaxDist =  new JTextField("1",12);
        textFieldQuakeMaxDist.setEnabled(chkBoxEarthquakeSounds.isSelected());

        chkBoxEarthquakeSounds.addChangeListener(changeEvent -> {
            textFieldQuakeMinMag.setEnabled(chkBoxEarthquakeSounds.isSelected());
            textFieldQuakeMaxDist.setEnabled(chkBoxEarthquakeSounds.isSelected());
        });

        JPanel earthquakePanel = new JPanel(new GridLayout(2,1));
        earthquakePanel.setBorder(BorderFactory.createTitledBorder(I18n.get("settings.alerts.earthquakeAlerts")));

        JPanel quakeMagpanel = new JPanel();
        quakeMagpanel.setLayout(new BoxLayout(quakeMagpanel, BoxLayout.X_AXIS));
        quakeMagpanel.add(chkBoxEarthquakeSounds);
        quakeMagpanel.add(textFieldQuakeMinMag);

        earthquakePanel.add(quakeMagpanel);

        JPanel quakeDistPanel = new JPanel();
        quakeDistPanel.setLayout(new BoxLayout(quakeDistPanel, BoxLayout.X_AXIS));
        quakeDistPanel.add(label2 = new JLabel(""));
        quakeDistPanel.add(textFieldQuakeMaxDist);

        earthquakePanel.add(quakeDistPanel);

        panel.add(earthquakePanel);
    }

    private void createPossibleShakingPanel(JPanel panel) {
        chkBoxPossibleShaking = new JCheckBox(I18n.format("settings.alerts.possibleShaking", Settings.getSelectedDistanceUnit().getShortName()), Settings.alertPossibleShaking);
        textFieldPossibleShakingDistance = new JTextField(String.valueOf(Settings.alertPossibleShakingDistance) ,12);
        textFieldPossibleShakingDistance.setEnabled(chkBoxPossibleShaking.isSelected());


        chkBoxPossibleShaking.addChangeListener(changeEvent -> textFieldPossibleShakingDistance.setEnabled(chkBoxPossibleShaking.isSelected()));

        JPanel possibleShakingPanel = new JPanel(new GridLayout(1,1));
        possibleShakingPanel.setBorder(BorderFactory.createTitledBorder(I18n.get("settings.alerts.possibleShakingTitle")));

        JPanel regionMagPanel = new JPanel();
        regionMagPanel.setLayout(new BoxLayout(regionMagPanel, BoxLayout.X_AXIS));
        regionMagPanel.add(chkBoxPossibleShaking);
        regionMagPanel.add(textFieldPossibleShakingDistance);

        possibleShakingPanel.add(regionMagPanel);

        panel.add(possibleShakingPanel);
    }

    private Component createWarningsTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        panel.add(createAlertDialogSettings());
        panel.add(createAlertLevels());

        fill(panel, 10);

        return panel;
    }

    private Component createAlertLevels() {
        JPanel panel = new JPanel();

        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(I18n.get("settings.alerts.alertLevels")));

        panel.add(shakingThreshold = new IntensityScaleSelector(I18n.get("settings.alerts.shakingThreshold"),
                Settings.shakingLevelScale, Settings.shakingLevelIndex));
        panel.add(strongShakingThreshold = new IntensityScaleSelector(I18n.get("settings.alerts.strongShakingThreshold"),
                Settings.strongShakingLevelScale, Settings.strongShakingLevelIndex));

        return panel;
    }

    private Component createAlertDialogSettings() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(I18n.get("settings.alerts.alertSettings")));

        chkBoxLocal = new JCheckBox("", Settings.alertLocal);
        textFieldLocalDist = new JTextField("1", 12);
        textFieldLocalDist.setEnabled(chkBoxLocal.isSelected());
        chkBoxLocal.addChangeListener(changeEvent -> textFieldLocalDist.setEnabled(chkBoxLocal.isSelected()));

        JPanel localPanel = new JPanel(new GridLayout(1,1));
        localPanel.setBorder(BorderFactory.createTitledBorder(I18n.get("settings.alerts.localArea")));

        JPanel nearbyPanel = new JPanel();
        nearbyPanel.setLayout(new BoxLayout(nearbyPanel, BoxLayout.X_AXIS));
        nearbyPanel.add(chkBoxLocal);
        nearbyPanel.add(textFieldLocalDist);

        localPanel.add(nearbyPanel);
        panel.add(localPanel);

        chkBoxRegion = new JCheckBox(I18n.get("settings.alerts.alertRegionMag"), Settings.alertRegion);
        textFieldRegionMag = new JTextField(String.valueOf(Settings.alertRegionMag) ,12);
        textFieldRegionMag.setEnabled(chkBoxRegion.isSelected());
        textFieldRegionDist =  new JTextField("1",12);
        textFieldRegionDist.setEnabled(chkBoxRegion.isSelected());

        chkBoxRegion.addChangeListener(changeEvent -> {
            textFieldRegionMag.setEnabled(chkBoxRegion.isSelected());
            textFieldRegionDist.setEnabled(chkBoxRegion.isSelected());
        });

        JPanel regionPanel = new JPanel(new GridLayout(2,1));
        regionPanel.setBorder(BorderFactory.createTitledBorder(I18n.get("settings.alerts.regionalArea")));

        JPanel regionMagPanel = new JPanel();
        regionMagPanel.setLayout(new BoxLayout(regionMagPanel, BoxLayout.X_AXIS));
        regionMagPanel.add(chkBoxRegion);
        regionMagPanel.add(textFieldRegionMag);

        regionPanel.add(regionMagPanel);


        JPanel regionDistPanel = new JPanel();
        regionDistPanel.setLayout(new BoxLayout(regionDistPanel, BoxLayout.X_AXIS));
        regionDistPanel.add(label1 = new JLabel(""));
        regionDistPanel.add(textFieldRegionDist);

        regionPanel.add(regionDistPanel);

        panel.add(regionPanel);

        JPanel globalPanel = new JPanel(new GridLayout(1,1));
        globalPanel.setBorder(BorderFactory.createTitledBorder(I18n.get("settings.alerts.global")));

        checkBoxGlobal = new JCheckBox(I18n.get("settings.alerts.alertGlobalMag"), Settings.alertGlobal);
        textFieldGlobalMag = new JTextField(String.valueOf(Settings.alertGlobalMag), 12);
        textFieldGlobalMag.setEnabled(checkBoxGlobal.isSelected());
        checkBoxGlobal.addChangeListener(changeEvent -> textFieldGlobalMag.setEnabled(checkBoxGlobal.isSelected()));

        JPanel globalMagPanel = new JPanel();
        globalMagPanel.setLayout(new BoxLayout(globalMagPanel, BoxLayout.X_AXIS));

        globalMagPanel.add(checkBoxGlobal);
        globalMagPanel.add(textFieldGlobalMag);

        globalPanel.add(globalMagPanel);

        panel.add(globalPanel);

        JPanel panel2 = new JPanel(new GridLayout(2,1));

        panel2.add( chkBoxFocus = new JCheckBox(I18n.get("settings.alerts.focusWindow"), Settings.focusOnEvent));
        panel2.add( chkBoxJumpToAlert = new JCheckBox(I18n.get("settings.alerts.jumpToAlert"), Settings.jumpToAlert));

        panel.add(panel2);

        return panel;
    }

    @Override
    public void refreshUI() {
        chkBoxLocal.setText(I18n.format("settings.alerts.alertLocalDist", Settings.getSelectedDistanceUnit().getShortName()));
        label1.setText(I18n.format("settings.alerts.closerFromHome", Settings.getSelectedDistanceUnit().getShortName()));
        label2.setText(I18n.format("settings.alerts.orCloserFromHome", Settings.getSelectedDistanceUnit().getShortName()));
        chkBoxPossibleShaking.setText(I18n.format("settings.alerts.possibleShaking", Settings.getSelectedDistanceUnit().getShortName()));

        textFieldLocalDist.setText(String.format("%.1f", Settings.alertLocalDist * Settings.getSelectedDistanceUnit().getKmRatio()));
        textFieldRegionDist.setText(String.format("%.1f", Settings.alertRegionDist * Settings.getSelectedDistanceUnit().getKmRatio()));
        textFieldPossibleShakingDistance.setText(String.format("%.1f", Settings.alertPossibleShakingDistance * Settings.getSelectedDistanceUnit().getKmRatio()));
        textFieldQuakeMaxDist.setText(String.format("%.1f", Settings.earthquakeSoundsMaxDist * Settings.getSelectedDistanceUnit().getKmRatio()));

        revalidate();
        repaint();
    }

    @Override
    public void save() throws NumberFormatException {
        Settings.alertLocal = chkBoxLocal.isSelected();
        Settings.alertLocalDist = parseDouble(textFieldLocalDist.getText(), I18n.get("settings.alerts.nameLocalDist"), 0, 30000) / Settings.getSelectedDistanceUnit().getKmRatio();
        Settings.alertRegion = chkBoxRegion.isSelected();
        Settings.alertRegionMag = parseDouble(textFieldRegionMag.getText(), I18n.get("settings.alerts.nameRegionMag"), 0, 10);
        Settings.alertRegionDist = parseDouble(textFieldRegionDist.getText(), I18n.get("settings.alerts.nameRegionDist"), 0, 30000) / Settings.getSelectedDistanceUnit().getKmRatio();

        Settings.alertGlobal = checkBoxGlobal.isSelected();
        Settings.alertGlobalMag = parseDouble(textFieldGlobalMag.getText(), I18n.get("settings.alerts.nameGlobalMag"), 0, 10);
        Settings.focusOnEvent = chkBoxFocus.isSelected();
        Settings.jumpToAlert = chkBoxJumpToAlert.isSelected();

        Settings.shakingLevelScale = shakingThreshold.getShakingScaleComboBox().getSelectedIndex();
        Settings.shakingLevelIndex = shakingThreshold.getLevelComboBox().getSelectedIndex();

        Settings.strongShakingLevelScale = strongShakingThreshold.getShakingScaleComboBox().getSelectedIndex();
        Settings.strongShakingLevelIndex = strongShakingThreshold.getLevelComboBox().getSelectedIndex();

        Settings.alertPossibleShaking = chkBoxPossibleShaking.isSelected();
        Settings.alertPossibleShakingDistance = parseDouble(textFieldPossibleShakingDistance.getText(), I18n.get("settings.alerts.namePossibleShakingDist"), 0, 30000) / Settings.getSelectedDistanceUnit().getKmRatio();
        Settings.enableEarthquakeSounds = chkBoxEarthquakeSounds.isSelected();
        Settings.earthquakeSoundsMinMagnitude = parseDouble(textFieldQuakeMinMag.getText(), I18n.get("settings.alerts.nameQuakeMinMag"), 0, 10);
        Settings.earthquakeSoundsMaxDist = parseDouble(textFieldQuakeMaxDist.getText(), I18n.get("settings.alerts.nameQuakeMaxDist"), 0, 30000) / Settings.getSelectedDistanceUnit().getKmRatio();

        Settings.eewScale = eewThreshold.getShakingScaleComboBox().getSelectedIndex();
        Settings.eewLevelIndex = eewThreshold.getLevelComboBox().getSelectedIndex();
        Settings.eewClusterLevel = (Integer) comboBoxEEWClusterLevel.getSelectedItem();
    }

    @Override
    public String getTitle() {
        return I18n.get("settings.alerts.title");
    }
}
