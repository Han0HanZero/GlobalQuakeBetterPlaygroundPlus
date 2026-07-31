package globalquake.ui.archived;

import globalquake.core.GQFonts;

import globalquake.core.Settings;
import globalquake.core.archive.ArchivedQuake;
import globalquake.ui.i18n.I18n;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.time.Instant;

public class ArchivedQuakeUI extends JDialog {

    public ArchivedQuakeUI(Frame parent, ArchivedQuake quake) {
        super(parent);
        setLayout(new BorderLayout());

        JLabel latLabel = new JLabel(I18n.format("archived.latitude", quake.getLat()));
        JLabel lonLabel = new JLabel(I18n.format("archived.longitude", quake.getLon()));
        JLabel depthLabel = new JLabel(I18n.format("archived.depth", Settings.getSelectedDistanceUnit().format(quake.getDepth(), 1)));
        JLabel originLabel = new JLabel(I18n.format("archived.originTime", Settings.formatDateTime(Instant.ofEpochMilli(quake.getOrigin()))));
        JLabel magLabel = new JLabel(I18n.format("archived.magnitude", quake.getMag()));
        JLabel maxRatioLabel = new JLabel(I18n.format("archived.maxRatio", quake.getMaxRatio()));
        JLabel regionLabel = new JLabel(I18n.format("archived.region", quake.getRegion()));

        // Create a panel to hold the labels
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(latLabel);
        panel.add(lonLabel);
        panel.add(depthLabel);
        panel.add(originLabel);
        panel.add(magLabel);
        panel.add(maxRatioLabel);
        panel.add(regionLabel);


        JButton animButton = new JButton(I18n.get("archived.animation"));

        animButton.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                new ArchivedQuakeAnimation(parent, quake).setVisible(true);
            }
        });

        getContentPane().add(panel, BorderLayout.CENTER);

        JPanel panel2 = new JPanel();
        panel2.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
        panel2.add(animButton);

        getContentPane().add(panel2, BorderLayout.SOUTH);

        for(Component component: panel.getComponents()){
            component.setFont(GQFonts.font(Font.PLAIN, 18));
        }

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_ESCAPE){
                    dispose();
                }
            }
        });

        setTitle(I18n.format("archived.title", quake.getMag(), quake.getRegion()));
        pack();
        setLocationRelativeTo(parent);
        setResizable(false);
    }
}
