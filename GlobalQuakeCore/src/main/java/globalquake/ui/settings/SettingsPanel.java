package globalquake.ui.settings;


import globalquake.core.exception.RuntimeApplicationException;
import globalquake.ui.i18n.I18n;

import javax.swing.*;

public abstract class SettingsPanel extends JPanel{

	public abstract void save() throws NumberFormatException;
	
	public abstract String getTitle();

	public void refreshUI() {}

	public double parseDouble(String str, String name, double min, double max){
		double d = Double.parseDouble(str.replace(',', '.'));
		if(Double.isNaN(d) || Double.isInfinite(d)){
			throw new RuntimeApplicationException(I18n.format("settings.error.invalidNumber", str));
		}

		if(d < min || d > max){
			throw new RuntimeApplicationException(I18n.format("settings.error.outOfRange", name, min, max, d));
		}

		return d;
	}

	public int parseInt(String str, String name, int min, int max){
		int n = Integer.parseInt(str);

		if(n < min || n > max){
			throw new RuntimeApplicationException(I18n.format("settings.error.outOfRange", name, min, max, n));
		}

		return n;
	}

	public void fill(JPanel panel, int n){
		for(int i = 0; i < n; i++){
			panel.add(new JPanel());
		}
	}

}
