package globalquake.ui.settings;

import globalquake.core.Settings;
import globalquake.core.report.EarthquakeReporter;
import globalquake.ui.i18n.I18n;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class DebugSettingsPanel extends SettingsPanel {

    private final JCheckBox chkBoxReports;
    private final JCheckBox chkBoxCoreWaves;
    private final JCheckBox chkBoxConfidencePolygons;
    private final JCheckBox chkBoxRevisions;

    public DebugSettingsPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(5,5,5,5));

        add(chkBoxReports = new JCheckBox(I18n.get("settings.debug.enableReports"), Settings.reportsEnabled));
        add(new JLabel(I18n.format("settings.debug.reportsLocation", EarthquakeReporter.ANALYSIS_FOLDER.getPath())));
        add(chkBoxCoreWaves = new JCheckBox(I18n.get("settings.debug.displayCoreWaves"), Settings.displayCoreWaves));
        add(chkBoxConfidencePolygons = new JCheckBox(I18n.get("settings.debug.confidencePolygons"), Settings.confidencePolygons));
        add(chkBoxRevisions = new JCheckBox(I18n.get("settings.debug.reduceRevisions"), Settings.reduceRevisions));
    }

    @Override
    public void save() {
        Settings.reportsEnabled = chkBoxReports.isSelected();
        Settings.displayCoreWaves = chkBoxCoreWaves.isSelected();
        Settings.confidencePolygons = chkBoxConfidencePolygons.isSelected();
        Settings.reduceRevisions = chkBoxRevisions.isSelected();
    }

    @Override
    public String getTitle() {
        return I18n.get("settings.debug.title");
    }
}
