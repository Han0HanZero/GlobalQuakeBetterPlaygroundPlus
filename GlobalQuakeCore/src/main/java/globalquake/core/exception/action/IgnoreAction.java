package globalquake.core.exception.action;

import globalquake.ui.i18n.I18n;

import java.awt.Component;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;

public class IgnoreAction extends AbstractAction {

	public IgnoreAction() {
		super(I18n.get("error.actionIgnore"));
		putValue(SHORT_DESCRIPTION, I18n.get("error.actionIgnoreDesc"));
        putValue(MNEMONIC_KEY, KeyEvent.VK_I);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		Window w = SwingUtilities.getWindowAncestor((Component) e.getSource());

		if (w != null) {
			w.setVisible(false);
		}
	}

}
