package gqserver.ui.server.table.model;

import globalquake.core.earthquake.data.Earthquake;
import globalquake.core.earthquake.quality.QualityClass;
import globalquake.ui.table.Column;
import globalquake.ui.table.FilterableTableModel;
import globalquake.ui.table.LastUpdateRenderer;
import globalquake.ui.table.TableCellRendererAdapter;
import globalquake.ui.i18n.I18n;

import java.time.LocalDateTime;
import java.util.List;

public class EarthquakeTableModel extends FilterableTableModel<Earthquake> {
    private final List<Column<Earthquake, ?>> columns = List.of(
            Column.readonly(I18n.get("table.origin"), LocalDateTime.class, Earthquake::getOriginDate, new LastUpdateRenderer<>()),
            Column.readonly(I18n.get("table.region"), String.class, Earthquake::getRegion, new TableCellRendererAdapter<>()),
            Column.readonly(I18n.get("table.magnitude"), Double.class, Earthquake::getMag, new TableCellRendererAdapter<>()),
            Column.readonly(I18n.get("table.depth"), Double.class, Earthquake::getDepth, new TableCellRendererAdapter<>()),
            Column.readonly(I18n.get("table.lat"), Double.class, Earthquake::getLat, new TableCellRendererAdapter<>()),
            Column.readonly(I18n.get("table.lon"), Double.class, Earthquake::getLon, new TableCellRendererAdapter<>()),
            Column.readonly(I18n.get("table.quality"), QualityClass.class, earthquake -> earthquake.getHypocenter().quality.getSummary(), new TableCellRendererAdapter<>()),
            Column.readonly(I18n.get("table.revision"), Integer.class, Earthquake::getRevisionID, new TableCellRendererAdapter<>()));


    public EarthquakeTableModel(List<Earthquake> data) {
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
        Earthquake event = getEntity(rowIndex);
        return columns.get(columnIndex).getValue(event);
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        Earthquake event = getEntity(rowIndex);
        columns.get(columnIndex).setValue(value, event);
    }
}
