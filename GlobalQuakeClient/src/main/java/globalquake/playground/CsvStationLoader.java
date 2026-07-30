package globalquake.playground;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import globalquake.core.database.StationDatabase;
import globalquake.core.database.StationSource;
import globalquake.core.database.Network;
import globalquake.core.database.Station;
import globalquake.core.database.Channel;
import gqserver.api.packets.station.InputType;
import org.tinylog.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class CsvStationLoader {

    public record StationRecord(String network, String station, String name,
                                double lat, double lon, double alt,
                                double sensitivity, String site, double vs30) {
    }

    public record StationFileInfo(String displayName, String filePath, int stationCount) {
    }

    public record StationFolderInfo(String name, String author, String description,
                                    String csvPath, int stationCount) {
    }

    public static List<StationFolderInfo> scanStationFolders(String folderPath) {
        List<StationFolderInfo> folders = new ArrayList<>();
        File rootFolder = new File(folderPath);

        if (!rootFolder.exists() || !rootFolder.isDirectory()) {
            Logger.info("Station folder not found: " + folderPath);
            return folders;
        }

        File[] subFolders = rootFolder.listFiles(File::isDirectory);
        if (subFolders != null) {
            for (File subFolder : subFolders) {
                StationFolderInfo info = loadFolderInfo(subFolder);
                if (info != null) {
                    folders.add(info);
                }
            }
        }

        folders.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        Logger.info("Found " + folders.size() + " station folders in " + folderPath);
        return folders;
    }

    private static StationFolderInfo loadFolderInfo(File folder) {
        File csvFile = findCsvFile(folder);
        if (csvFile == null) {
            Logger.debug("No CSV file found in folder: " + folder.getAbsolutePath());
            return null;
        }

        String name = folder.getName();
        String author = "Unknown";
        String description = "";

        File infoFile = new File(folder, "info.json");
        if (infoFile.exists()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(infoFile);
                if (root.has("name")) name = root.get("name").asText();
                if (root.has("author")) author = root.get("author").asText();
                if (root.has("description")) description = root.get("description").asText();
            } catch (Exception e) {
                Logger.warn("Failed to parse info.json in: " + folder.getAbsolutePath());
            }
        }

        int count = countStations(csvFile.getAbsolutePath());
        return new StationFolderInfo(name, author, description, csvFile.getAbsolutePath(), count);
    }

    private static File findCsvFile(File folder) {
        File[] csvFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv"));
        if (csvFiles != null && csvFiles.length > 0) {
            return csvFiles[0];
        }
        return null;
    }

    private static int countStations(String filePath) {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(
                new FileReader(filePath, StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                line = line.trim();
                if (!line.isEmpty()) {
                    count++;
                }
            }
        } catch (Exception e) {
            Logger.warn("Failed to count stations in: " + filePath);
        }
        return count;
    }

    public static List<StationRecord> loadCsv(String filePath) {
        List<StationRecord> records = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new FileReader(filePath, StandardCharsets.UTF_8))) {

            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split(",", -1);
                if (parts.length < 5) {
                    continue;
                }

                try {
                    String network = parts[0].trim();
                    String station = parts[1].trim();
                    String name = parts.length > 2 ? parts[2].trim() : station;
                    double lat = Double.parseDouble(parts[3].trim());
                    double lon = Double.parseDouble(parts[4].trim());
                    double alt = parts.length > 5 ? parseDoubleSafe(parts[5], 0) : 0;
                    double sensitivity = parts.length > 6 ? parseDoubleSafe(parts[6], 7e10) : 7e10;
                    String site = parts.length > 7 ? parts[7].trim() : "";
                    double vs30 = parts.length > 8 ? parseDoubleSafe(parts[8], 760) : 760;

                    records.add(new StationRecord(network, station, name,
                            lat, lon, alt, sensitivity, site, vs30));
                } catch (NumberFormatException e) {
                    Logger.warn("Skipping malformed CSV line: " + line);
                }
            }

            Logger.info("Loaded " + records.size() + " stations from " + filePath);
        } catch (Exception e) {
            Logger.error("Failed to load station CSV: " + filePath, e);
        }

        return records;
    }

    public static List<String> getNetworks(String filePath) {
        List<StationRecord> all = loadCsv(filePath);
        Set<String> networks = new LinkedHashSet<>();
        for (StationRecord r : all) {
            networks.add(r.network());
        }
        return new ArrayList<>(networks);
    }

    public static List<StationRecord> filterAndSample(String filePath,
                                                       Set<String> selectedNetworks,
                                                       int maxCount) {
        List<StationRecord> all = loadCsv(filePath);

        List<StationRecord> filtered;
        if (selectedNetworks == null || selectedNetworks.isEmpty() || selectedNetworks.contains("ALL")) {
            filtered = new ArrayList<>(all);
        } else {
            filtered = all.stream()
                    .filter(r -> selectedNetworks.contains(r.network()))
                    .collect(Collectors.toList());
        }

        Logger.info("Filtered: " + filtered.size() + " stations from selected networks");

        if (maxCount > 0 && filtered.size() > maxCount) {
            Collections.shuffle(filtered, new Random());
            filtered = filtered.subList(0, maxCount);
            Logger.info("Sampled down to " + maxCount + " stations");
        }

        return filtered;
    }

    public static StationDatabase loadIntoDatabase(String filePath,
                                                    Set<String> selectedNetworks,
                                                    int maxCount) {
        StationDatabase db = new StationDatabase();
        List<StationRecord> records = filterAndSample(filePath, selectedNetworks, maxCount);

        StationSource csvSource = new StationSource("CSV Import", "file://" + filePath);

        for (StationRecord record : records) {
            Network network = StationDatabase.getOrCreateNetwork(
                    db.getNetworks(), record.network(), "CSV Imported");
            Station station = StationDatabase.getOrCreateStation(
                    network, record.station(), record.name(),
                    record.lat(), record.lon(), record.alt());

            StationDatabase.getOrCreateChannel(
                    station, "BHZ", "", 40.0,
                    record.lat(), record.lon(), record.alt(),
                    csvSource, record.sensitivity(), InputType.VELOCITY);
        }

        return db;
    }

    private static double parseDoubleSafe(String s, double defaultValue) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}