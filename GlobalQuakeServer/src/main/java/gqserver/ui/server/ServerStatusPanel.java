package gqserver.ui.server;

import globalquake.core.Settings;
import globalquake.core.exception.RuntimeApplicationException;
import globalquake.ui.i18n.I18n;
import gqserver.events.GlobalQuakeServerEventListener;
import gqserver.events.specific.ServerStatusChangedEvent;
import gqserver.main.Main;
import gqserver.server.GlobalQuakeServer;
import gqserver.server.SocketStatus;
import gqserver.ui.server.tabs.*;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class ServerStatusPanel extends JPanel {
    private JButton controlButton;
    private JLabel statusLabel;
    private JTextField addressField;
    private JTextField portField;

    public ServerStatusPanel() {
        setLayout(new BorderLayout());

        add(createTopPanel(), BorderLayout.NORTH);
        add(createMiddlePanel(), BorderLayout.CENTER);
    }

    private Component createMiddlePanel() {
        JTabbedPane tabbedPane = new JTabbedPane();

        tabbedPane.addTab(I18n.get("server.tab.status"), new StatusTab());
        tabbedPane.addTab(I18n.get("server.tab.seedlinks"), new SeedlinksTab());
        tabbedPane.addTab(I18n.get("server.tab.clients"), new ClientsTab());
        tabbedPane.addTab(I18n.get("server.tab.earthquakes"), new EarthquakesTab());
        tabbedPane.addTab(I18n.get("server.tab.clusters"), new ClustersTab());

        return tabbedPane;
    }

    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));

        JPanel addressPanel = new JPanel(new GridLayout(2,1));
        addressPanel.setBorder(BorderFactory.createTitledBorder(I18n.get("server.addressTitle")));

        JPanel ipPanel = new JPanel();
        ipPanel.setLayout(new BoxLayout(ipPanel, BoxLayout.X_AXIS));
        ipPanel.add(new JLabel(I18n.get("server.ip")));
        ipPanel.add(addressField = new JTextField(Settings.lastServerIP,20));

        addressPanel.add(ipPanel);

        JPanel portPanel = new JPanel();
        portPanel.setLayout(new BoxLayout(portPanel, BoxLayout.X_AXIS));
        portPanel.add(new JLabel(I18n.get("server.port")));
        portPanel.add(portField = new JTextField(String.valueOf(Settings.lastServerPORT),20));

        addressPanel.add(portPanel);

        topPanel.add(addressPanel);

        JPanel controlPanel = new JPanel(new GridLayout(2,1));
        controlPanel.setBorder(BorderFactory.createTitledBorder(I18n.get("server.controlPanel")));

        controlPanel.add(statusLabel = new JLabel(I18n.get("server.statusIdle")));
        controlPanel.add(controlButton = new JButton(I18n.get("server.startServer")));

        GlobalQuakeServer.instance.getServerEventHandler().registerEventListener(new GlobalQuakeServerEventListener(){
            @Override
            public void onServerStatusChanged(ServerStatusChangedEvent event) {
                switch (event.status()){
                    case IDLE -> {
                        addressField.setEnabled(true);
                        portField.setEnabled(true);
                        controlButton.setEnabled(true);
                        controlButton.setText(I18n.get("server.startServer"));
                    }
                    case OPENING -> {
                        addressField.setEnabled(false);
                        portField.setEnabled(false);
                        controlButton.setEnabled(false);
                        controlButton.setText(I18n.get("server.startServer"));
                    }
                    case RUNNING -> {
                        addressField.setEnabled(false);
                        portField.setEnabled(false);
                        controlButton.setEnabled(true);
                        controlButton.setText(I18n.get("server.stopServer"));
                    }
                }
                statusLabel.setText(I18n.format("server.status", event.status()));
            }
        });

        controlButton.addActionListener(actionEvent -> {
            SocketStatus status = GlobalQuakeServer.instance.getServerSocket().getStatus();
            if(status == SocketStatus.IDLE){
                try {
                    String ip = addressField.getText();
                    int port = Integer.parseInt(portField.getText());

                    Settings.lastServerIP = ip;
                    Settings.lastServerPORT = port;
                    Settings.save();

                    GlobalQuakeServer.instance.initStations();
                    GlobalQuakeServer.instance.getServerSocket().run(ip, port);
                    GlobalQuakeServer.instance.startRuntime();
                } catch(Exception e){
                    Main.getErrorHandler().handleException(new RuntimeApplicationException(I18n.get("server.startFailed"), e));
                }
            } else if(status == SocketStatus.RUNNING) {
                if(confirm(I18n.get("server.closeConfirm"))) {
                    try {
                        GlobalQuakeServer.instance.getServerSocket().stop();
                        GlobalQuakeServer.instance.stopRuntime();
                        GlobalQuakeServer.instance.reset();
                    } catch (IOException e) {
                        Main.getErrorHandler().handleException(new RuntimeApplicationException(I18n.get("server.stopFailed"), e));
                    }
                }
            }
        });

        topPanel.add(controlPanel);
        return topPanel;
    }

    @SuppressWarnings("SameParameterValue")
    private boolean confirm(String s) {
        return JOptionPane.showConfirmDialog(this, s, I18n.get("server.confirmation"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

}
