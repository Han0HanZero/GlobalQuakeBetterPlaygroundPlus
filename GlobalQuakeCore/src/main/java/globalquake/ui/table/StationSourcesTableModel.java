package globalquake.ui.table;

import globalquake.core.database.StationSource;
import globalquake.ui.i18n.I18n;

import javax.swing.*;
import java.time.LocalDateTime;
import java.util.List;

public class StationSourcesTableModel extends FilterableTableModel<StationSource>{
    private final List<Column<StationSource, ?>> columns = List.of(
            Column.readonly(I18n.get("table.name"), String.class, StationSource::getName, new TableCellRendererAdapter<>()),
            Column.readonly(I18n.get("table.url"), String.class, StationSource::getUrl, new TableCellRendererAdapter<>()),
            Column.readonly(I18n.get("table.lastUpdate"), LocalDateTime.class, StationSource::getLastUpdate, new LastUpdateRenderer<>()),
            Column.readonly(I18n.get("table.status"), JProgressBar.class, StationSource::getStatus, new ProgressBarRenderer<>()));

    public StationSourcesTableModel(List<StationSource> data) {
        super(data);
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public String getColumnName(int columnIndex) {
        return columns.get(columnIndex).getName();
    }

    @Override
    public TableCellRendererAdapter<?, ?> getColumnRenderer(int columnIndex) {
        return columns.get(columnIndex).getRenderer();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columns.get(columnIndex).getColumnType();
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columns.get(columnIndex).isEditable();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        StationSource event = getEntity(rowIndex);
        return columns.get(columnIndex).getValue(event);
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        StationSource event = getEntity(rowIndex);
        columns.get(columnIndex).setValue(value, event);
    }
}
