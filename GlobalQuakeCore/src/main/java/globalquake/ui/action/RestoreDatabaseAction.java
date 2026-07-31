package globalquake.ui.action;

import globalquake.core.database.StationDatabaseManager;
import globalquake.ui.i18n.I18n;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Objects;

public class RestoreDatabaseAction extends AbstractAction {

    private final StationDatabaseManager databaseManager;
    private final Window parent;

    public RestoreDatabaseAction(Window parent, StationDatabaseManager databaseManager){
        super(I18n.get("action.restoreDefaults"));
        this.databaseManager = databaseManager;
        this.parent = parent;

        putValue(SHORT_DESCRIPTION, I18n.get("action.restoreDefaultsTip"));

        ImageIcon restoreIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/image_icons/restore.png")));
        Image image = restoreIcon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
        ImageIcon scaledIcon = new ImageIcon(image);
        putValue(Action.SMALL_ICON, scaledIcon);
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        int option = JOptionPane.showConfirmDialog(parent,
                I18n.get("action.restoreConfirm"),
                I18n.get("action.confirmation"),
                JOptionPane.YES_NO_OPTION);

        if (option != JOptionPane.YES_OPTION) {
            return;
        }

        databaseManager.restore();
    }
}
