package globalquake.ui.settings;


import globalquake.core.GQFonts;
import globalquake.core.Settings;
import globalquake.core.geo.DistanceUnit;
import globalquake.core.intensity.IntensityScale;
import globalquake.core.intensity.IntensityScales;
import globalquake.core.intensity.LevelPalette;
import globalquake.ui.i18n.I18n;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Objects;

public class GeneralSettingsPanel extends SettingsPanel {
	private JComboBox<IntensityScale> comboBoxScale;
	private JCheckBox chkBoxHomeLoc;

	private JTextField textFieldLat;
	private JTextField textFieldLon;
	private JComboBox<DistanceUnit> distanceUnitJComboBox;
	private JComboBox<ZoneId> timezoneCombobox;

	private JSlider sliderStoreTime;

	public GeneralSettingsPanel(SettingsFrame settingsFrame) {
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

		createHomeLocationSettings();
		//createAlertsDialogSettings();
		add(createIntensitySettingsPanel());
		createOtherSettings(settingsFrame);
		add(createSettingStoreTime());
	}

	private void createOtherSettings(SettingsFrame settingsFrame) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder(I18n.get("settings.other")));

		JPanel row0 = new JPanel();

		row0.add(new JLabel(I18n.get("settings.language")));

		JComboBox<String> languageComboBox = new JComboBox<>(new String[]{"简体中文", "English"});
		languageComboBox.setSelectedIndex(Settings.languageIndex == null ? 0 : Settings.languageIndex);
		languageComboBox.addActionListener(new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				Settings.languageIndex = languageComboBox.getSelectedIndex();
				I18n.applyLanguage(Settings.languageIndex);
				settingsFrame.rebuildUI();
			}
		});

		row0.add(languageComboBox);

		JPanel row1 = new JPanel();

		row1.add(new JLabel(I18n.get("settings.distanceUnits")));

		distanceUnitJComboBox = new JComboBox<>(DistanceUnit.values());
		distanceUnitJComboBox.setSelectedIndex(Math.max(0, Math.min(distanceUnitJComboBox.getItemCount() - 1, Settings.distanceUnitsIndex)));

		distanceUnitJComboBox.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
														  boolean isSelected, boolean cellHasFocus) {
				JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof DistanceUnit unit) {
					label.setText(I18n.get("unit." + unit.name().toLowerCase()));
				}
				return label;
			}
		});

		distanceUnitJComboBox.addItemListener(itemEvent -> {
			Settings.distanceUnitsIndex = distanceUnitJComboBox.getSelectedIndex();
			settingsFrame.refreshUI();
        });

		row1.add(distanceUnitJComboBox);

		JPanel row2 = new JPanel();

		row2.add(new JLabel(I18n.get("settings.timezone")));
		Comparator<ZoneId> zoneIdComparator = Comparator.comparingInt(zone -> zone.getRules().getOffset(Instant.now()).getTotalSeconds());

		// Use a DefaultComboBoxModel for better control and management
		DefaultComboBoxModel<ZoneId> timezoneModel = new DefaultComboBoxModel<>();

		// Populate the model with available timezones and sort them using the custom Comparator
		ZoneId.getAvailableZoneIds().stream()
				.map(ZoneId::of)
				.sorted(zoneIdComparator)
				.forEach(timezoneModel::addElement);

		// Create the JComboBox with the populated and sorted model
		timezoneCombobox = new JComboBox<>(timezoneModel);

		// this assures that default timezone will always be selected
		timezoneCombobox.setSelectedItem(ZoneId.systemDefault());

		// if theres valid timezone in the settings then it will be selected
		timezoneCombobox.setSelectedItem(ZoneId.of(Settings.timezoneStr));

		// Add the JComboBox to your UI
		row2.add(timezoneCombobox);

		timezoneCombobox.setRenderer(new DefaultListCellRenderer(){
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
														  boolean isSelected, boolean cellHasFocus) {
				JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

				if (value instanceof ZoneId zoneId) {
                    String offset = zoneId.getRules().getOffset(Instant.now()).toString();
					label.setText(String.format("%s (%s)", zoneId, offset));
				}

				return label;
			}
		});

		timezoneCombobox.addActionListener(new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				Settings.timezoneStr = ((ZoneId) Objects.requireNonNull(timezoneCombobox.getSelectedItem())).getId();
				Settings.initTimezoneSettings();
			}
		});

		row2.add(timezoneCombobox);

		panel.add(row0);
		panel.add(row1);
		panel.add(row2);

		add(panel);
	}

	private void createHomeLocationSettings() {
		JPanel outsidePanel = new JPanel(new BorderLayout());
		outsidePanel.setBorder(BorderFactory.createTitledBorder(I18n.get("settings.homeLocation")));

		JPanel homeLocationPanel = new JPanel();
		homeLocationPanel.setLayout(new GridLayout(2,1));

		JLabel lblLat = new JLabel(I18n.get("settings.homeLat"));
		JLabel lblLon = new JLabel(I18n.get("settings.homeLon"));

		textFieldLat = new JTextField(20);
		textFieldLat.setText(String.format("%s", Settings.homeLat));
		textFieldLat.setColumns(10);

		textFieldLon = new JTextField(20);
		textFieldLon.setText(String.format("%s", Settings.homeLon));
		textFieldLon.setColumns(10);

		JPanel latPanel = new JPanel();
		//latPanel.setLayout(new BoxLayout(latPanel, BoxLayout.X_AXIS));

		latPanel.add(lblLat);
		latPanel.add(textFieldLat);

		JPanel lonPanel = new JPanel();
		//lonPanel.setLayout(new BoxLayout(lonPanel, BoxLayout.X_AXIS));

		lonPanel.add(lblLon);
		lonPanel.add(textFieldLon);

		homeLocationPanel.add(latPanel);
		homeLocationPanel.add(lonPanel);

		JTextArea infoLocation = new JTextArea(I18n.get("settings.homeLocationInfo"));
		infoLocation.setBorder(new EmptyBorder(5,5,5,5));
		infoLocation.setLineWrap(true);
		infoLocation.setEditable(false);
		infoLocation.setBackground(homeLocationPanel.getBackground());

		chkBoxHomeLoc = new JCheckBox(I18n.get("settings.displayHomeLocation"));
		chkBoxHomeLoc.setSelected(Settings.displayHomeLocation);
		outsidePanel.add(chkBoxHomeLoc);

		outsidePanel.add(homeLocationPanel, BorderLayout.NORTH);
		outsidePanel.add(infoLocation, BorderLayout.CENTER);
		outsidePanel.add(chkBoxHomeLoc, BorderLayout.SOUTH);

		add(outsidePanel);
	}

	private Component createSettingStoreTime() {
		sliderStoreTime = HypocenterAnalysisSettingsPanel.createSettingsSlider(2, 20, 2, 1);

		JLabel label = new JLabel();
		ChangeListener changeListener = changeEvent -> label.setText(I18n.format("settings.waveformStorage", sliderStoreTime.getValue()));

		sliderStoreTime.addChangeListener(changeListener);

		sliderStoreTime.setValue(Settings.logsStoreTimeMinutes);
		changeListener.stateChanged(null);

		return HypocenterAnalysisSettingsPanel.createCoolLayout(sliderStoreTime, label, "5",
				I18n.get("settings.waveformStorageInfo"));
	}

	private JPanel createIntensitySettingsPanel() {
		JPanel panel = new JPanel(new GridLayout(3,1));
		panel.setBorder(BorderFactory.createTitledBorder(I18n.get("settings.intensityScale")));

		comboBoxScale = new JComboBox<>(IntensityScales.INTENSITY_SCALES);
		comboBoxScale.setSelectedIndex(Settings.intensityScaleIndex);

		comboBoxScale.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
														  boolean isSelected, boolean cellHasFocus) {
				JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof IntensityScale scale) {
					label.setText(I18n.get("intensity." + scale.getNameShort().toLowerCase()));
				}
				return label;
			}
		});

		JPanel div = new JPanel();
		div.add(comboBoxScale);
		panel.add(div, BorderLayout.CENTER);

		// 全局烈度配色方案
		JPanel divPalette = new JPanel();
		JComboBox<String> paletteComboBox = new JComboBox<>(new String[]{
				I18n.get("settings.palette.default"),
				I18n.get("settings.palette.kanameishi"),
				I18n.get("settings.palette.srev")
		});
		paletteComboBox.setSelectedIndex(Settings.intensityPalette == null ? 0 : Settings.intensityPalette);
		paletteComboBox.addActionListener(new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				Settings.intensityPalette = paletteComboBox.getSelectedIndex();
				LevelPalette.setCurrent(Settings.intensityPalette);
			}
		});
		divPalette.add(new JLabel(I18n.get("settings.intensityPalette")));
		divPalette.add(paletteComboBox);
		panel.add(divPalette);

		JLabel lbl = new JLabel();
		lbl.setFont(GQFonts.font(Font.PLAIN, 13));
		lbl.setText(I18n.get("settings.intensityEstimatedInfo"));


		panel.add(lbl, BorderLayout.SOUTH);

		return panel;
	}

	@Override
	public void save() {
		Settings.homeLat = parseDouble(textFieldLat.getText(), I18n.get("settings.homeLat"), -90, 90);
		Settings.homeLon = parseDouble(textFieldLon.getText(), I18n.get("settings.homeLon"), -180, 180);
		Settings.intensityScaleIndex = comboBoxScale.getSelectedIndex();
		Settings.displayHomeLocation = chkBoxHomeLoc.isSelected();
		Settings.distanceUnitsIndex = distanceUnitJComboBox.getSelectedIndex();
		Settings.timezoneStr = ((ZoneId) Objects.requireNonNull(timezoneCombobox.getSelectedItem())).getId();
		Settings.logsStoreTimeMinutes = sliderStoreTime.getValue();
	}

	@Override
	public String getTitle() {
		return I18n.get("settings.general.title");
	}
}
