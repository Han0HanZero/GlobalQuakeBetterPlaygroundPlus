package globalquake.ui.settings;

import globalquake.core.Settings;
import globalquake.core.training.EarthquakeAnalysisTraining;
import globalquake.ui.i18n.I18n;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;

public class PerformanceSettingsPanel extends SettingsPanel {
    private JSlider sliderResolution;
    private JCheckBox chkBoxParalell;
    private JCheckBox chkBoxRecalibrateOnLauch;

    public PerformanceSettingsPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        add(createSettingAccuracy());
        add(createSettingParalell());
        fill(this, 16);
    }

    @SuppressWarnings("ExtractMethodRecommender")
    private JPanel createSettingParalell() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createRaisedBevelBorder());
        panel.setLayout(new BorderLayout());
        chkBoxParalell = new JCheckBox(I18n.get("settings.performance.useAllCores"));
        chkBoxParalell.setSelected(Settings.parallelHypocenterLocations);

        JTextArea textAreaExplanation = new JTextArea(I18n.get("settings.performance.useAllCoresInfo"));
        textAreaExplanation.setBorder(new EmptyBorder(5, 5, 5, 5));
        textAreaExplanation.setEditable(false);
        textAreaExplanation.setBackground(panel.getBackground());

        chkBoxParalell.addChangeListener(changeEvent -> Settings.parallelHypocenterLocations = chkBoxParalell.isSelected());

        panel.add(chkBoxParalell, BorderLayout.CENTER);
        panel.add(textAreaExplanation, BorderLayout.SOUTH);
        return panel;
    }

    @Override
    public void save() {
        Settings.hypocenterDetectionResolution = (double) sliderResolution.getValue();
        Settings.parallelHypocenterLocations = chkBoxParalell.isSelected();
        Settings.recalibrateOnLaunch = chkBoxRecalibrateOnLauch.isSelected();
    }

    private Component createSettingAccuracy() {
        sliderResolution = HypocenterAnalysisSettingsPanel.createSettingsSlider(0, 160, 10, 5);

        JLabel label = new JLabel();
        ChangeListener changeListener = changeEvent ->
        {
            label.setText(I18n.format("settings.performance.resolution",
                    sliderResolution.getValue() / 100.0,
                    getNameForResolution(sliderResolution.getValue())));
            Settings.hypocenterDetectionResolution = (double) sliderResolution.getValue();
        };
        sliderResolution.addChangeListener(changeListener);

        sliderResolution.setValue(Settings.hypocenterDetectionResolution.intValue());
        changeListener.stateChanged(null);

        JPanel panel = HypocenterAnalysisSettingsPanel.createCoolLayout(sliderResolution, label, "%.2f".formatted(Settings.hypocenterDetectionResolutionDefault / 100.0),
                I18n.get("settings.performance.resolutionInfo"));

        JPanel panel2 = new JPanel();

        JButton btnRecalibrate = new JButton(I18n.get("settings.performance.recalibrate"));
        btnRecalibrate.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                btnRecalibrate.setEnabled(false);
                sliderResolution.setEnabled(false);
                new Thread(() -> {
                    EarthquakeAnalysisTraining.calibrateResolution(null, sliderResolution, true);
                    btnRecalibrate.setEnabled(true);
                    sliderResolution.setEnabled(true);
                }).start();
            }
        });

        panel2.add(btnRecalibrate);

        JButton testSpeed = new JButton(I18n.get("settings.performance.testSearch"));
        testSpeed.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                testSpeed.setEnabled(false);
                new Thread(() -> {
                    testSpeed.setText(I18n.format("settings.performance.testTook", EarthquakeAnalysisTraining.measureTest(System.currentTimeMillis(), 60, true)));
                    testSpeed.setEnabled(true);
                }).start();
            }
        });
        panel2.add(testSpeed);

        chkBoxRecalibrateOnLauch = new JCheckBox(I18n.get("settings.performance.recalibrateOnStartup"), Settings.recalibrateOnLaunch);
        panel2.add(chkBoxRecalibrateOnLauch);

        panel.add(panel2, BorderLayout.SOUTH);

        return panel;
    }

    public static final String[] RESOLUTION_KEYS = {"resolutionVeryLow", "resolutionLow", "resolutionDefault", "resolutionIncreased", "resolutionHigh", "resolutionVeryHigh", "resolutionExtremelyHigh", "resolutionInsane"};

    private String getNameForResolution(int value) {
        return I18n.get("settings.performance." + RESOLUTION_KEYS[(int) Math.max(0, Math.min(RESOLUTION_KEYS.length - 1, ((value / 160.0) * (RESOLUTION_KEYS.length))))]);
    }

    @Override
    public String getTitle() {
        return I18n.get("settings.performance.title");
    }
}
