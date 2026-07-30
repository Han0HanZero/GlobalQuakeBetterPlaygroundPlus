package globalquake.playground;

import globalquake.core.database.StationDatabaseManager;
import globalquake.core.database.StationDatabase;
import globalquake.core.database.Network;
import globalquake.core.database.Station;
import globalquake.core.database.Channel;
import globalquake.core.regions.Regions;
import globalquake.core.station.AbstractStation;
import globalquake.core.station.GlobalStationManager;
import globalquake.utils.GeoUtils;
import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class GlobalStationManagerPlayground extends GlobalStationManager {

    @Override
    public void initStations(StationDatabaseManager databaseManager) {
        if (databaseManager == null || databaseManager.getStationDatabase() == null) {
            return;
        }

        StationDatabase db = databaseManager.getStationDatabase();
        List<PlaygroundStation> list = new ArrayList<>();

        for (Network network : db.getNetworks()) {
            for (Station station : network.getStations()) {
                Channel channel = station.getSelectedChannel();
                if (channel == null) {
                    if (!station.getChannels().isEmpty()) {
                        channel = station.getChannels().get(0);
                    } else {
                        continue;
                    }
                }

                int id = nextID.incrementAndGet();
                list.add(new PlaygroundStation(
                        network.getNetworkCode(),
                        station.getStationCode(),
                        channel.getCode(),
                        channel.getLocationCode(),
                        channel.getLatitude(),
                        channel.getLongitude(),
                        channel.getElevation(),
                        id,
                        PlaygroundStation.DEFAULT_SENSITIVITY));
            }
        }

        if (!list.isEmpty()) {
            newStations();
            this.stations.forEach(AbstractStation::clear);
            this.stations.clear();
            this.stations.addAll(list);
            createListOfClosestStations(this.stations);
            Logger.info("Initialized " + list.size() + " stations from database.");
        }
    }

    public void loadStationsFromCsv(String csvPath) {
        loadStationsFromCsv(csvPath, null, 0);
    }

    public void loadStationsFromCsv(String csvPath, Set<String> selectedNetworks, int maxCount) {
        List<CsvStationLoader.StationRecord> records = CsvStationLoader.filterAndSample(csvPath, selectedNetworks, maxCount);
        if (records.isEmpty()) {
            Logger.warn("No stations found in CSV: " + csvPath);
            return;
        }

        newStations();
        List<PlaygroundStation> list = new ArrayList<>();

        for (CsvStationLoader.StationRecord record : records) {
            int id = nextID.incrementAndGet();
            list.add(new PlaygroundStation(
                    record.network(),
                    record.station(),
                    "BHZ",
                    "",
                    record.lat(),
                    record.lon(),
                    record.alt(),
                    id,
                    PlaygroundStation.DEFAULT_SENSITIVITY));
        }

        this.stations.forEach(AbstractStation::clear);
        this.stations.clear();
        this.stations.addAll(list);
        createListOfClosestStations(this.stations);
        Logger.info("Loaded " + list.size() + " stations from CSV: " + csvPath);
    }

    /**
     * 追加导入测站（不清除现有测站）
     */
    public void addStationsFromCsv(String csvPath, Set<String> selectedNetworks, int maxCount) {
        List<CsvStationLoader.StationRecord> records = CsvStationLoader.filterAndSample(csvPath, selectedNetworks, maxCount);
        if (records.isEmpty()) {
            Logger.warn("No stations found in CSV: " + csvPath);
            return;
        }

        newStations();
        List<PlaygroundStation> list = new ArrayList<>();

        for (CsvStationLoader.StationRecord record : records) {
            int id = nextID.incrementAndGet();
            list.add(new PlaygroundStation(
                    record.network(),
                    record.station(),
                    "BHZ",
                    "",
                    record.lat(),
                    record.lon(),
                    record.alt(),
                    id,
                    PlaygroundStation.DEFAULT_SENSITIVITY));
        }

        // 追加模式：不清除现有测站
        this.stations.addAll(list);
        createListOfClosestStations(this.stations);
        Logger.info("Added " + list.size() + " stations from CSV (append mode): " + csvPath);
    }

    public void generateRandomStations(int count, double radius, double fromLat, double fromLon) {
        Random r = new Random();
        newStations();
        List<PlaygroundStation> list = new ArrayList<>();
        int created = 0;
        int fails = 0;
        while (created < count) {
            if (fails > 500) {
                Logger.warn("Station generation aborted!");
                break;
            }
            double[] coords = randomCoords(r);
            double lat = coords[0];
            double lon = coords[1];
            double distGCD = GeoUtils.greatCircleDistance(lat, lon, fromLat, fromLon);
            if (distGCD > radius) {
                continue;
            }

            if (Regions.isOcean(lat, lon, true)) {
                fails++;
                continue;
            }

            int id = nextID.incrementAndGet();

            String name = "Dummy #%d".formatted(id);
            list.add(new PlaygroundStation(name, lat, lon, 0, nextID.getAndIncrement(), PlaygroundStation.DEFAULT_SENSITIVITY));
            created++;
            fails = 0;
        }

        this.stations.forEach(AbstractStation::clear);
        this.stations.clear();
        this.stations.addAll(list);
        createListOfClosestStations(this.stations);
    }

    private void newStations() {
        this.indexing = UUID.randomUUID();
    }

    public int getNextId() {
        return nextID.incrementAndGet();
    }

    public void addStation(PlaygroundStation station) {
        this.stations.add(station);
    }

    public void removeStations(List<AbstractStation> stationsToRemove) {
        this.stations.removeAll(stationsToRemove);
    }

    public void rebuildClosestStations() {
        createListOfClosestStations(this.stations);
    }

    public static double[] randomCoords(Random random) {
        double theta = 2 * Math.PI * random.nextDouble();
        double phi = Math.acos(2 * random.nextDouble() - 1);

        double x = Math.sin(phi) * Math.cos(theta);
        double y = Math.sin(phi) * Math.sin(theta);
        double z = Math.cos(phi);

        double latitude = Math.toDegrees(Math.asin(z));
        double longitude = Math.toDegrees(Math.atan2(y, x));
        return new double[]{latitude, longitude};
    }
}