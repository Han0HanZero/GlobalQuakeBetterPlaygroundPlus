package globalquake.ui.table;

import globalquake.core.Settings;
import globalquake.ui.i18n.I18n;

import java.time.LocalDateTime;

public class LastUpdateRenderer<E> extends TableCellRendererAdapter<E, LocalDateTime> {

	@SuppressWarnings("unused")
	@Override
	public String getText(E entity, LocalDateTime value) {
		if(value == null){
			return I18n.get("table.never");
		}
		return Settings.formatDateTime(value);
	}

}
