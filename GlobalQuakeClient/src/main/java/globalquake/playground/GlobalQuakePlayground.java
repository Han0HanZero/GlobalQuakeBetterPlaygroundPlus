package globalquake.playground;

import globalquake.ui.i18n.I18n;
import globalquake.client.GlobalQuakeLocal;
import globalquake.core.GlobalQuake;
import globalquake.core.archive.EarthquakeArchive;
import globalquake.core.database.StationDatabaseManager;
import globalquake.core.earthquake.EarthquakeAnalysis;
import globalquake.core.earthquake.data.Earthquake;
import globalquake.core.exception.ApplicationErrorHandler;
import globalquake.core.regions.Regions;
import globalquake.core.station.GlobalStationManager;
import globalquake.main.Main;
import globalquake.sounds.Sounds;
import globalquake.utils.Scale;
import globalquake.utils.monitorable.MonitorableCopyOnWriteArrayList;
import org.tinylog.Logger;

import java.awt.EventQueue;
import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class GlobalQuakePlayground extends GlobalQuakeLocal {

    public long createdAtMillis;
    private final long playgroundStartMillis = LocalDate.of(2000, 1, 1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

    private final Collection<Earthquake> playgroundEarthquakes = new MonitorableCopyOnWriteArrayList<>();

    private String stationsFolderPath;
    private String currentStationsCsvPath;

    public static void main(String[] args) throws Exception {
        GlobalQuake.prepare(Main.MAIN_FOLDER, new ApplicationErrorHandler(null, false));
        Regions.init();
        Scale.load();
        Sounds.load();
        globalquake.intensity.ShakeMap.init();

        new GlobalQuakePlayground();
    }

    @Override
    public void startRuntime() {
        getGlobalQuakeRuntime().runThreads();
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> playgroundEarthquakes.removeIf(earthquake -> EarthquakeAnalysis.shouldRemove(earthquake, -30)), 0, 1, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unused")
    public GlobalQuakePlayground(StationDatabaseManager stationDatabaseManager, GlobalStationManager globalStationManager) {
        super(stationDatabaseManager, globalStationManager);
    }

    public GlobalQuakePlayground() {
        super(new StationDatabaseManagerPlayground(), new GlobalStationManagerPlayground());
        new WaveformGenerator(this);
        createdAtMillis = System.currentTimeMillis();
        findStationsFolder();
        createFrame();
        startRuntime();
    }

    private void findStationsFolder() {
        String cwd = System.getProperty("user.dir");
        String mainFolder = Main.MAIN_FOLDER.getAbsolutePath();

        String[] candidates = {
                cwd + "/playground_stations",
                mainFolder + "/playground_stations",
                cwd + "/.files/stations",
        };

        for (String path : candidates) {
            File folder = new File(path);
            if (folder.exists() && folder.isDirectory()) {
                stationsFolderPath = path;
                Logger.info("Found stations folder: " + path + " (press 'T' to import)");
                return;
            }
        }

        // Create default folder
        File defaultFolder = new File(cwd + "/playground_stations");
        if (!defaultFolder.exists()) {
            defaultFolder.mkdirs();
        }
        stationsFolderPath = defaultFolder.getAbsolutePath();
        Logger.info("Created stations folder: " + stationsFolderPath + " (add CSV files and press 'T' to import)");
    }

    public String getStationsFolderPath() {
        return stationsFolderPath;
    }

    public void importStationsFromCsv(Set<String> selectedNetworks, int maxCount) {
        if (currentStationsCsvPath == null) {
            Logger.warn("No stations CSV file selected!");
            return;
        }

        importStationsFromCsv(currentStationsCsvPath, selectedNetworks, maxCount);
    }

    public void importStationsFromCsv(String csvPath) {
        importStationsFromCsv(csvPath, null, 0);
    }

    public void importStationsFromCsv(String csvPath, Set<String> selectedNetworks, int maxCount) {
        importStationsFromCsv(csvPath, selectedNetworks, maxCount, true);
    }

    public void importStationsFromCsv(String csvPath, Set<String> selectedNetworks, int maxCount, boolean clearExisting) {
        this.currentStationsCsvPath = csvPath;

        StationDatabaseManagerPlayground dbManager = (StationDatabaseManagerPlayground) getStationDatabaseManager();
        GlobalStationManagerPlayground stationMgr = (GlobalStationManagerPlayground) getStationManager();

        dbManager.loadFromCsv(csvPath, selectedNetworks, maxCount);

        if (clearExisting) {
            stationMgr.loadStationsFromCsv(csvPath, selectedNetworks, maxCount);
        } else {
            stationMgr.addStationsFromCsv(csvPath, selectedNetworks, maxCount);
        }
    }

    public GlobalQuakePlayground createFrame() {
        EventQueue.invokeLater(() -> {
            try {
                globalQuakeFrame = new GlobalQuakeFramePlayground();
                globalQuakeFrame.setVisible(true);

                Main.getErrorHandler().setParent(globalQuakeFrame);
            } catch (Exception e) {
                Logger.error(e);
                System.exit(0);
            }
        });
        return this;
    }

    @Override
    public long currentTimeMillis() {
        return playgroundStartMillis + (System.currentTimeMillis() - createdAtMillis);
    }

    @Override
    public EarthquakeArchive createArchive() {
        return new EarthquakeArchive();
    }

    public Collection<Earthquake> getPlaygroundEarthquakes() {
        return playgroundEarthquakes;
    }

    public String getWatermark() {
        return I18n.get("playground.watermark");
    }

    @Override
    public boolean isSimulation() {
        return true;
    }
}