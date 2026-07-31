package globalquake.ui.settings;

import globalquake.core.Settings;
import globalquake.ui.i18n.I18n;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class HypocenterAnalysisSettingsPanel extends SettingsPanel {

    private JSlider sliderPWaveInaccuracy;
    private JSlider sliderCorrectness;
    private JSlider sliderMinStations;
    private JSlider sliderMaxStations;

    public HypocenterAnalysisSettingsPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        add(createMinStationsSetting());
        add(createMaxStationsSetting());
        add(createSettingPWave());
        add(createSettingCorrectness());
    }

    private Component createMaxStationsSetting() {
        sliderMaxStations = createSettingsSlider(20, 300, 20, 5);

        JLabel label = new JLabel();

        ChangeListener upd = changeEvent -> label.setText(I18n.format("settings.maxStations", sliderMaxStations.getValue()));

        sliderMaxStations.addChangeListener(upd);
        sliderMaxStations.setValue(Settings.maxEvents);

        upd.stateChanged(null);

        return createCoolLayout(sliderMaxStations, label, "%s".formatted(Settings.maxEventsDefault),
                I18n.get("settings.maxStationsInfo"));
    }

    private Component createMinStationsSetting() {
        sliderMinStations = createSettingsSlider(4, 16, 1, 1);

        JLabel label = new JLabel();

        ChangeListener upd = changeEvent -> label.setText(I18n.format("settings.minStations", sliderMinStations.getValue()));

        sliderMinStations.addChangeListener(upd);
        sliderMinStations.setValue(Settings.minimumStationsForEEW);

        upd.stateChanged(null);

        return createCoolLayout(sliderMinStations, label, "%s".formatted(Settings.minimumStationsForEEWDefault),
                I18n.get("settings.minStationsInfo"));
    }

    public static JPanel createCoolLayout(JSlider slider, JLabel label, String defaultValue, String explanation){
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createRaisedBevelBorder());

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(new EmptyBorder(5,5,5,5));

        topPanel.add(label, BorderLayout.NORTH);
        topPanel.add(slider, BorderLayout.CENTER);

        if(defaultValue != null) {
            JLabel labelDefault = new JLabel(I18n.format("settings.defaultValue", defaultValue));
            labelDefault.setBorder(new EmptyBorder(8, 2, 0, 0));
            topPanel.add(labelDefault, BorderLayout.SOUTH);
        }

        if(explanation != null) {
            JTextArea textAreaExplanation = new JTextArea(explanation);
            textAreaExplanation.setBorder(new EmptyBorder(5, 5, 5, 5));
            textAreaExplanation.setEditable(false);
            textAreaExplanation.setBackground(panel.getBackground());
            panel.add(textAreaExplanation, BorderLayout.CENTER);
        }

        panel.add(topPanel, BorderLayout.NORTH);

        return panel;
    }

    public static JSlider createSettingsSlider(int min, int max, int major, int minor){
        JSlider slider = new JSlider();
        slider.setMinimum(min);
        slider.setMaximum(max);
        slider.setMajorTickSpacing(major);
        slider.setMinorTickSpacing(minor);

        slider.setPaintLabels(true);
        slider.setPaintTicks(true);
        return slider;
    }

    private Component createSettingCorrectness() {
        sliderCorrectness = createSettingsSlider(20, 90, 10, 2);

        JLabel label = new JLabel();

        ChangeListener upd = changeEvent -> label.setText(I18n.format("settings.correctnessThreshold", sliderCorrectness.getValue()));

        sliderCorrectness.addChangeListener(upd);
        sliderCorrectness.setValue(Settings.hypocenterCorrectThreshold.intValue());

        upd.stateChanged(null);

        return createCoolLayout(sliderCorrectness, label, "%s %%".formatted(Settings.hypocenterCorrectThresholdDefault),
                I18n.get("settings.correctnessThresholdInfo"));
    }

    private Component createSettingPWave() {
        sliderPWaveInaccuracy = createSettingsSlider(400, 5200, 400, 200);

        JLabel label = new JLabel();
        ChangeListener changeListener = changeEvent -> label.setText(I18n.format("settings.pWaveInaccuracy", sliderPWaveInaccuracy.getValue()));
        sliderPWaveInaccuracy.addChangeListener(changeListener);

        sliderPWaveInaccuracy.setValue(Settings.pWaveInaccuracyThreshold.intValue());
        changeListener.stateChanged(null);

        return createCoolLayout(sliderPWaveInaccuracy, label, "%s ms".formatted(Settings.pWaveInaccuracyThresholdDefault),
                I18n.get("settings.pWaveInaccuracyInfo"));
    }

    @Override
    public void save() {
        Settings.pWaveInaccuracyThreshold = (double) sliderPWaveInaccuracy.getValue();
        Settings.hypocenterCorrectThreshold = (double) sliderCorrectness.getValue();
        Settings.minimumStationsForEEW = sliderMinStations.getValue();
        Settings.maxEvents = sliderMaxStations.getValue();
    }

    @Override
    public String getTitle() {
        return I18n.get("settings.hypocenter.title");
    }
}
