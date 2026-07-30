package globalquake.playground;

import globalquake.core.database.StationDatabase;
import globalquake.core.database.StationDatabaseManager;
import org.tinylog.Logger;

import java.util.Set;

public class StationDatabaseManagerPlayground extends StationDatabaseManager {

    public StationDatabaseManagerPlayground() {
        super();
    }

    @Override
    public void load() {
    }

    @Override
    public void save() {

    }

    public void loadFromCsv(String csvPath) {
        loadFromCsv(csvPath, null, 0);
    }

    public void loadFromCsv(String csvPath, Set<String> selectedNetworks, int maxCount) {
        StationDatabase db = CsvStationLoader.loadIntoDatabase(csvPath, selectedNetworks, maxCount);
        setStationDatabase(db);
        Logger.info("Loaded " + db.getNetworks().size() + " networks from CSV");
        fireUpdateEvent();
    }
}