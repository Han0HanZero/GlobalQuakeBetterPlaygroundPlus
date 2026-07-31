package globalquake.core.exception.action;

import globalquake.ui.i18n.I18n;

import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * Action responsible for terminating the application.
 */
public final class TerminateAction extends AbstractAction {

	public TerminateAction() {
        super(I18n.get("error.actionTerminate"));
        putValue(SHORT_DESCRIPTION, I18n.get("error.actionTerminateDesc"));
        putValue(MNEMONIC_KEY, KeyEvent.VK_T);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        System.exit(0);
    }
}
