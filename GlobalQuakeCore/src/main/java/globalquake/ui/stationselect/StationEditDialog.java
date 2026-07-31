package globalquake.ui.stationselect;

import globalquake.core.GQFonts;

import globalquake.core.database.Channel;
import globalquake.core.database.Station;
import globalquake.ui.i18n.I18n;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;

public class StationEditDialog extends JDialog {
    public StationEditDialog(StationSelectFrame stationSelectFrame, Station selectedStation) {
        super(stationSelectFrame);
        setTitle(selectedStation.toString());
        setFont(GQFonts.font(Font.BOLD, 14));

        setResizable(false);
        setModal(true);
        setLayout(new BorderLayout());

        JTextArea textAreaInfo = createInfoTextArea(selectedStation);

        add(new JScrollPane(textAreaInfo), BorderLayout.NORTH);

        JPanel channelSelectPanel = new JPanel();

        JComboBox<Channel> channelJComboBox = new JComboBox<>();
        channelJComboBox.addItem(null);
        selectedStation.getChannels().forEach(channelJComboBox::addItem);
        channelJComboBox.setSelectedItem(selectedStation.getSelectedChannel());

        channelJComboBox.addItemListener(itemEvent -> selectedStation.setSelectedChannel((Channel) channelJComboBox.getSelectedItem()));

        channelSelectPanel.add(new JLabel(I18n.get("stationedit.selectedChannel")));
        channelSelectPanel.add(channelJComboBox);
        add(channelSelectPanel, BorderLayout.CENTER);

        JButton doneButton = new JButton(I18n.get("stationedit.done"));
        doneButton.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                StationEditDialog.this.dispose();
            }
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBorder(new EmptyBorder(5,5,5,5));
        buttonPanel.add(doneButton);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(stationSelectFrame);
        setVisible(true);
    }

    private JTextArea createInfoTextArea(Station selectedStation) {
        JTextArea textAreaInfo = new JTextArea();
        textAreaInfo.setEditable(false);
        textAreaInfo.append(I18n.get("stationedit.networkCode").formatted(selectedStation.getNetwork().getNetworkCode()));
        textAreaInfo.append(I18n.get("stationedit.networkDescription").formatted(selectedStation.getNetwork().getDescription().trim()));
        textAreaInfo.append(I18n.get("stationedit.stationCode").formatted(selectedStation.getStationCode()));
        textAreaInfo.append(I18n.get("stationedit.stationSite").formatted(selectedStation.getStationSite().trim()));
        textAreaInfo.append(I18n.get("stationedit.elevation").formatted(selectedStation.getAlt()));
        textAreaInfo.append(I18n.get("stationedit.latitude").formatted(selectedStation.getLatitude()));
        textAreaInfo.append(I18n.get("stationedit.longitude").formatted(selectedStation.getLongitude()));
        textAreaInfo.setFont(getFont());
        textAreaInfo.setColumns(22);
        return textAreaInfo;
    }
}
