package globalquake.ui.settings;

import globalquake.core.Settings;
import globalquake.ui.i18n.I18n;

import javax.swing.*;

/**
 * PLUM 法（简易版，Playground）设置面板。
 * 阈值/分辨率/ハイブリッド融合/レベル法/每网格最少测站。
 */
public class PlumSettingsPanel extends SettingsPanel {

    private JCheckBox chkBoxEnabled;
    private JComboBox<Integer> comboBoxResolution;
    private IntensityScaleSelector thresholdSelector;
    private JCheckBox chkBoxHybrid;
    private JCheckBox chkBoxLevelMethod;
    private JTextField textFieldMinStations;

    public PlumSettingsPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        chkBoxEnabled = new JCheckBox(I18n.get("settings.plum.enabled"), Settings.plumEnabled);
        add(chkBoxEnabled);

        JPanel resPanel = new JPanel();
        resPanel.add(new JLabel(I18n.get("settings.plum.resolution")));
        comboBoxResolution = new JComboBox<>(new Integer[]{4, 5, 6, 7, 8});
        comboBoxResolution.setSelectedItem(Settings.plumResolution);
        resPanel.add(comboBoxResolution);
        add(resPanel);

        add(thresholdSelector = new IntensityScaleSelector(I18n.get("settings.plum.threshold"),
                Settings.plumThresholdScale, Settings.plumThresholdLevel));

        chkBoxHybrid = new JCheckBox(I18n.get("settings.plum.hybrid"), Settings.plumHybridEnabled);
        add(chkBoxHybrid);

        chkBoxLevelMethod = new JCheckBox(I18n.get("settings.plum.levelMethod"), Settings.plumLevelMethodEnabled);
        add(chkBoxLevelMethod);

        JPanel minStationsPanel = new JPanel();
        minStationsPanel.add(new JLabel(I18n.get("settings.plum.minStations")));
        textFieldMinStations = new JTextField(String.valueOf(Settings.plumCellMinStations), 5);
        minStationsPanel.add(textFieldMinStations);
        add(minStationsPanel);

        fill(this, 20);
    }

    @Override
    public void save() throws NumberFormatException {
        Settings.plumEnabled = chkBoxEnabled.isSelected();
        Settings.plumResolution = (Integer) comboBoxResolution.getSelectedItem();
        Settings.plumThresholdScale = thresholdSelector.getShakingScaleComboBox().getSelectedIndex();
        Settings.plumThresholdLevel = thresholdSelector.getLevelComboBox().getSelectedIndex();
        Settings.plumHybridEnabled = chkBoxHybrid.isSelected();
        Settings.plumLevelMethodEnabled = chkBoxLevelMethod.isSelected();
        Settings.plumCellMinStations = parseInt(textFieldMinStations.getText(),
                I18n.get("settings.plum.minStations"), 1, 100);
    }

    @Override
    public String getTitle() {
        return I18n.get("settings.plum.title");
    }
}
