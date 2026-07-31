package globalquake.playground;

import globalquake.core.GQFonts;

import globalquake.core.GlobalQuake;
import globalquake.core.earthquake.data.Cluster;
import globalquake.core.earthquake.data.Earthquake;
import globalquake.core.earthquake.data.Hypocenter;
import globalquake.core.earthquake.data.MagnitudeReading;
import globalquake.core.earthquake.interval.DepthConfidenceInterval;
import globalquake.core.earthquake.interval.PolygonConfidenceInterval;
import globalquake.core.events.specific.QuakeRemoveEvent;
import globalquake.core.station.AbstractStation;
import globalquake.ui.globalquake.GlobalQuakePanel;
import globalquake.ui.globe.GlobeRenderer;
import globalquake.ui.globe.Polygon3D;
import globalquake.ui.globe.RenderProperties;
import globalquake.ui.i18n.I18n;
import globalquake.utils.GeoUtils;
import gqserver.api.packets.station.InputType;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.io.File;
import java.util.*;
import java.util.List;

public class GlobalQuakePanelPlayground extends GlobalQuakePanel {

    public static boolean displayPlaygroundQuakes = true;
    private final JFrame parent;

    enum InsertType {
        NONE, EARTHQUAKE, RANDOM_STATIONS, BRUSH_ADD, BRUSH_DELETE
    }

    private record GeoPoint(double lat, double lon) {}

    private InsertType insertType = InsertType.NONE;
    private double brushRadiusDegrees = 1.0;
    private GeoPoint currentMouseGeoPos = null;
    private int mouseX = 0;
    private int mouseY = 0;
    private GeoPoint lastAddPos = null;

    public GlobalQuakePanelPlayground(JFrame parent) {
        super(parent);
        this.parent = parent;

        this.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
                if (insertType == InsertType.BRUSH_ADD || insertType == InsertType.BRUSH_DELETE) {
                    updateMouseGeoPos(e);
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
                if (insertType == InsertType.BRUSH_ADD || insertType == InsertType.BRUSH_DELETE) {
                    updateMouseGeoPos(e);
                    applyBrushAt(currentMouseGeoPos);
                    e.consume();
                }
            }
        });

        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (isInSidebarToggleArea(e)) {
                    ((GlobalQuakeFramePlayground) parent).toggleList();
                    e.consume();
                    return;
                }
                if (insertType == InsertType.BRUSH_ADD || insertType == InsertType.BRUSH_DELETE) {
                    updateMouseGeoPos(e);
                    applyBrushAt(currentMouseGeoPos);
                    e.consume();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (insertType == InsertType.BRUSH_ADD || insertType == InsertType.BRUSH_DELETE) {
                    GlobalStationManagerPlayground stationMgr = (GlobalStationManagerPlayground) GlobalQuake.instance.getStationManager();
                    stationMgr.rebuildClosestStations();
                    if (insertType == InsertType.BRUSH_ADD) {
                        lastAddPos = null;
                    }
                    e.consume();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (insertType == InsertType.BRUSH_ADD || insertType == InsertType.BRUSH_DELETE) {
                    e.consume();
                }
            }
        });

        parent.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE && insertType != InsertType.NONE
                        && insertType != InsertType.BRUSH_ADD && insertType != InsertType.BRUSH_DELETE) {
                    insertSmth();
                    insertType = InsertType.NONE;
                }

                if (e.getKeyCode() == KeyEvent.VK_R) {
                    if (insertType == InsertType.RANDOM_STATIONS) {
                        insertType = InsertType.NONE;
                    } else {
                        insertType = InsertType.RANDOM_STATIONS;
                        insertSmth();
                        insertType = InsertType.NONE;
                    }
                }

                if (e.getKeyCode() == KeyEvent.VK_F) {
                    displayPlaygroundQuakes = !displayPlaygroundQuakes;
                }

                if (e.getKeyCode() == KeyEvent.VK_I) {
                    importStationsFromCsv();
                }

                if (e.getKeyCode() == KeyEvent.VK_T) {
                    showStationImportDialog();
                }

                if (e.getKeyCode() == KeyEvent.VK_E) {
                    insertType = toggle(InsertType.EARTHQUAKE);
                }

                if (e.getKeyCode() == KeyEvent.VK_B) {
                    if (insertType == InsertType.BRUSH_ADD) {
                        insertType = InsertType.NONE;
                    } else {
                        insertType = InsertType.BRUSH_ADD;
                        lastAddPos = null;
                    }
                }

                if (e.getKeyCode() == KeyEvent.VK_D) {
                    if (insertType == InsertType.BRUSH_DELETE) {
                        insertType = InsertType.NONE;
                    } else {
                        insertType = InsertType.BRUSH_DELETE;
                    }
                }

                if (e.getKeyCode() == KeyEvent.VK_U) {
                    // Increase brush radius
                    brushRadiusDegrees = Math.min(brushRadiusDegrees * 2, 45.0);
                }

                if (e.getKeyCode() == KeyEvent.VK_J) {
                    // Decrease brush radius
                    brushRadiusDegrees = Math.max(brushRadiusDegrees / 2, 0.1);
                }

                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    ((GlobalQuakePlayground) GlobalQuake.instance).getPlaygroundEarthquakes().clear();
                    for (Earthquake earthquake : GlobalQuake.instance.getEarthquakeAnalysis().getEarthquakes()) {
                        GlobalQuake.instance.getEventHandler().fireEvent(new QuakeRemoveEvent(earthquake));
                    }

                    for (AbstractStation station : GlobalQuake.instance.getStationManager().getStations()) {
                        station.clear();
                    }

                    GlobalQuake.instance.getStationManager().getStations().clear();

                    GlobalQuake.instance.clear();
                }
                if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                    ((GlobalQuakePlayground) GlobalQuake.instance).createdAtMillis += 5 * 1000;
                }
                if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                    ((GlobalQuakePlayground) GlobalQuake.instance).createdAtMillis -= 5 * 1000;
                }
            }
        });
    }

    @Override
    public boolean interactionAllowed() {
        return insertType != InsertType.BRUSH_ADD && insertType != InsertType.BRUSH_DELETE;
    }

    @Override
    protected boolean showStationsAndSeedlinks() {
        return false;
    }

    @Override
    public void featuresClicked(ArrayList<globalquake.ui.globe.feature.RenderEntity<?>> clicked) {
        if (insertType == InsertType.BRUSH_ADD || insertType == InsertType.BRUSH_DELETE) {
            return;
        }
        super.featuresClicked(clicked);
    }

    private boolean isInSidebarToggleArea(MouseEvent e) {
        return e.getX() >= getWidth() - 30 && e.getX() <= getWidth() && e.getY() >= 0 && e.getY() <= 30;
    }

    private void updateMouseGeoPos(MouseEvent e) {
        var renderer = getRenderer();
        if (renderer == null) return;

        var rps = renderer.getRenderProperties();
        if (rps == null || rps.getRenderPrecomputedValues() == null) return;

        currentMouseGeoPos = screenToGeo(e.getX(), e.getY(), rps);
    }

    private GeoPoint screenToGeo(int screenX, int screenY, RenderProperties rps) {
        var renderer = getRenderer();
        if (renderer == null) return null;
        var pre = rps.getRenderPrecomputedValues();
        if (pre == null) return null;

        Vector3D origin = pre.cameraPoint;

        double tanHalfFov = Math.tan(Math.PI / 6.0);

        double kx = 1.0 - (screenX + rps.width / 2.0) / rps.width;
        double ky = (screenY - rps.height / 2.0) / rps.width;

        double camDirX = kx;
        double camDirY = ky;
        double camDirZ = -tanHalfFov;

        double tmpY = camDirY * pre.cosPitch + camDirZ * pre.sinPitch;
        double tmpZ = -camDirY * pre.sinPitch + camDirZ * pre.cosPitch;
        camDirY = tmpY;
        camDirZ = tmpZ;

        double tmpX = camDirX * pre.cosYaw - camDirZ * pre.sinYaw;
        tmpZ = camDirX * pre.sinYaw + camDirZ * pre.cosYaw;
        camDirX = tmpX;
        camDirZ = tmpZ;

        double dirLen = Math.sqrt(camDirX * camDirX + camDirY * camDirY + camDirZ * camDirZ);
        if (dirLen < 1e-10) return null;
        camDirX /= dirLen;
        camDirY /= dirLen;
        camDirZ /= dirLen;

        Vector3D dir = new Vector3D(camDirX, camDirY, camDirZ);

        double a = dir.dotProduct(dir);
        double b = 2.0 * origin.dotProduct(dir);
        double c = origin.dotProduct(origin) - GeoUtils.EARTH_RADIUS * GeoUtils.EARTH_RADIUS;

        double discriminant = b * b - 4 * a * c;
        if (discriminant < 0) return null;

        double sqrtDisc = Math.sqrt(discriminant);
        double t1 = (-b - sqrtDisc) / (2 * a);
        double t2 = (-b + sqrtDisc) / (2 * a);

        double t = t1 > 0.001 ? t1 : (t2 > 0.001 ? t2 : -1);
        if (t < 0) return null;

        Vector3D intersection = origin.add(dir.scalarMultiply(t));

        double lat = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, intersection.getY() / GeoUtils.EARTH_RADIUS))));
        double lon = Math.toDegrees(Math.atan2(-intersection.getX(), -intersection.getZ()));
        lon = ((lon + 180) % 360 + 360) % 360 - 180;

        GeoPoint result = new GeoPoint(lat, lon);

        double pixelPerDeg = pre.oneDegPx;
        if (pixelPerDeg <= 0) return result;

        Vector3D testPos = new Vector3D(
                GlobeRenderer.getX_3D(result.lat(), result.lon(), 0),
                GlobeRenderer.getY_3D(result.lat(), result.lon(), 0),
                GlobeRenderer.getZ_3D(result.lat(), result.lon(), 0));

        if (!renderer.isAboveHorizon(testPos, rps)) return result;

        globalquake.ui.globe.Point2D projected = renderer.projectPoint(testPos, rps);
        double errX = projected.x - screenX;
        double errY = projected.y - screenY;
        double errDist = Math.sqrt(errX * errX + errY * errY);

        if (errDist > 3.0) {
            double cosLat = Math.max(0.01, Math.cos(Math.toRadians(result.lat())));
            double degPerPx = 1.0 / pixelPerDeg;
            double dLat = -errY * degPerPx;
            double dLon = -errX * degPerPx / cosLat;
            double newLat = Math.max(-89.0, Math.min(89.0, result.lat() + dLat));
            double newLon = result.lon() + dLon;
            newLon = ((newLon + 180) % 360 + 360) % 360 - 180;

            for (int iter = 0; iter < 5; iter++) {
                testPos = new Vector3D(
                        GlobeRenderer.getX_3D(newLat, newLon, 0),
                        GlobeRenderer.getY_3D(newLat, newLon, 0),
                        GlobeRenderer.getZ_3D(newLat, newLon, 0));

                if (!renderer.isAboveHorizon(testPos, rps)) break;

                projected = renderer.projectPoint(testPos, rps);
                errX = projected.x - screenX;
                errY = projected.y - screenY;
                errDist = Math.sqrt(errX * errX + errY * errY);

                if (errDist < 2.0) break;

                cosLat = Math.max(0.01, Math.cos(Math.toRadians(newLat)));
                degPerPx = 1.0 / pixelPerDeg;
                dLat = -errY * degPerPx;
                dLon = -errX * degPerPx / cosLat;
                newLat = Math.max(-89.0, Math.min(89.0, newLat + dLat));
                newLon = newLon + dLon;
                newLon = ((newLon + 180) % 360 + 360) % 360 - 180;
            }

            result = new GeoPoint(newLat, newLon);
        }

        return result;
    }

    private void applyBrushAt(GeoPoint geoPos) {
        if (geoPos == null) return;

        GlobalStationManagerPlayground stationMgr = (GlobalStationManagerPlayground) GlobalQuake.instance.getStationManager();

        if (insertType == InsertType.BRUSH_ADD) {
            addStationsInRadius(geoPos, stationMgr);
        } else if (insertType == InsertType.BRUSH_DELETE) {
            deleteStationsInRadius(geoPos, stationMgr);
        }

        repaint();
    }

    private void addStationsInRadius(GeoPoint center, GlobalStationManagerPlayground stationMgr) {
        double spacingKm = 50.0;
        try {
            if (globalquake.core.Settings.brushStationSpacingKm != null) {
                spacingKm = globalquake.core.Settings.brushStationSpacingKm;
            }
        } catch (Exception ignored) {}

        if (lastAddPos != null) {
            double dist = GeoUtils.greatCircleDistance(
                    lastAddPos.lat(), lastAddPos.lon(),
                    center.lat(), center.lon());
            if (dist < spacingKm) {
                return;
            }
        }

        double lat = center.lat();
        double lon = center.lon();

        int id = stationMgr.getNextId();
        String network = "BR";
        String stationCode = "B" + String.format("%04d", id);

        PlaygroundStation station = new PlaygroundStation(
                network, stationCode, "BHZ", "",
                lat, lon, 0,
                id, PlaygroundStation.DEFAULT_SENSITIVITY);

        stationMgr.addStation(station);
        lastAddPos = center;
    }

    private void deleteStationsInRadius(GeoPoint center, GlobalStationManagerPlayground stationMgr) {
        double lat = center.lat();
        double lon = center.lon();
        double radiusKm = brushRadiusDegrees * 111.0;

        List<AbstractStation> toRemove = new ArrayList<>();
        for (AbstractStation station : stationMgr.getStations()) {
            double dist = GeoUtils.greatCircleDistance(
                    lat, lon,
                    station.getLatitude(), station.getLongitude());
            if (dist <= radiusKm) {
                toRemove.add(station);
            }
        }

        if (toRemove.isEmpty()) return;

        for (AbstractStation station : toRemove) {
            station.clear();
            stationMgr.getStations().remove(station);
        }

        stationMgr.rebuildClosestStations();
    }

    private InsertType toggle(InsertType insertType) {
        if (this.insertType == insertType) {
            return InsertType.NONE;
        }
        // Exit brush modes when switching to other modes
        if (insertType != InsertType.BRUSH_ADD && insertType != InsertType.BRUSH_DELETE) {
            // keep brush radius settings
        }
        return insertType;
    }

    private void insertSmth() {
        switch (insertType) {
            case EARTHQUAKE -> createDebugQuake();
            case RANDOM_STATIONS -> createRandomStations();
        }
    }

    private void showStationImportDialog() {
        GlobalQuakePlayground gq = (GlobalQuakePlayground) GlobalQuake.instance;
        String folderPath = gq.getStationsFolderPath();

        if (folderPath == null) {
            JOptionPane.showMessageDialog(parent,
                    I18n.get("playground.noStationFolder"),
                    I18n.get("stationimport.title"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        StationImportDialog dialog = new StationImportDialog(parent, folderPath);

        // Check if import was successful
        if (dialog.getSelectedNetworks() != null && dialog.getSelectedCsvPath() != null) {
            String csvPath = dialog.getSelectedCsvPath();
            Set<String> selectedNetworks = dialog.getSelectedNetworks();
            int maxCount = dialog.getMaxCount();
            boolean clearExisting = dialog.isClearExisting();

            gq.importStationsFromCsv(csvPath, selectedNetworks, maxCount, clearExisting);
        }
    }

    private void importStationsFromCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(I18n.get("playground.importCsvTitle"));
        chooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));

        String cwd = System.getProperty("user.dir");
        File defaultDir = new File(cwd + "/.files");
        if (!defaultDir.exists() || !defaultDir.isDirectory()) {
            defaultDir = new File(cwd);
        }
        if (defaultDir.exists() && defaultDir.isDirectory()) {
            chooser.setCurrentDirectory(defaultDir);
        }

        int result = chooser.showOpenDialog(parent);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            String path = selectedFile.getAbsolutePath();
            ((GlobalQuakePlayground) GlobalQuake.instance).importStationsFromCsv(path);
        }
    }

    private void createRandomStations() {
        java.util.List<DecimalInput> inputs = new ArrayList<>();
        DecimalInput radius;
        inputs.add(radius = new DecimalInput(I18n.get("playground.input.radius"), 50, 30000, 1000.0));
        DecimalInput amount;
        inputs.add(amount = new DecimalInput(I18n.get("playground.input.amount"), 10, 10000, 1000.0));

        new DecimalInputDialog(parent, I18n.get("playground.chooseParameters"), inputs, () -> (((GlobalStationManagerPlayground) GlobalQuake.instance.getStationManager())).generateRandomStations(
                (int) amount.getValue(),
                radius.getValue(),
                getRenderer().getRenderProperties().centerLat,
                getRenderer().getRenderProperties().centerLon));
    }

    @Override
    protected void addRenderFeatures() {
        super.addRenderFeatures();
        getRenderer().addFeature(new FeaturePlaygroundEarthquake(((GlobalQuakePlayground) GlobalQuake.instance).getPlaygroundEarthquakes()));
    }

    private void createDebugQuake() {
        java.util.List<DecimalInput> inputs = new ArrayList<>();
        DecimalInput magInput;
        inputs.add(magInput = new DecimalInput(I18n.get("playground.input.magnitude"), 0, 10, 4.0));
        DecimalInput depthInput;
        inputs.add(depthInput = new DecimalInput(I18n.get("playground.input.depth"), 0, 700, 10.0));
        new DecimalInputDialog(parent, I18n.get("playground.chooseParameters"), inputs, () -> _createDebugEarthquake(
                magInput.getValue(), depthInput.getValue(), getRenderer().getRenderProperties().centerLat, getRenderer().getRenderProperties().centerLon));
    }

    public void _createDebugEarthquake(double magnitude, double depth, double lat, double lon) {
        Earthquake quake;
        Cluster clus = new Cluster();
        clus.updateLevel(4);

        Hypocenter hyp = new Hypocenter(
                lat, lon,
                depth,
                GlobalQuake.instance.currentTimeMillis(), 0, 10,
                new DepthConfidenceInterval(10, 100),
                List.of(new PolygonConfidenceInterval(16, 0, List.of(
                        0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0, 14.0, 15.0), 1000, 10000)));

        clus.updateRoot(hyp.lat, hyp.lon);

        hyp.usedEvents = 20;

        hyp.magnitude = magnitude;

        hyp.correctEvents = 6;

        hyp.calculateQuality();

        clus.setPreviousHypocenter(hyp);

        quake = new Earthquake(clus);

        clus.setEarthquake(quake);
        hyp.magnitude = quake.getMag();

        List<MagnitudeReading> mags = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            double mag = 5 + Math.tan(i / 100.0 * 3.14159);
            mags.add(new MagnitudeReading(mag, 0, 55555, InputType.VELOCITY));
        }

        hyp.mags = mags;

        ((GlobalQuakePlayground) GlobalQuake.instance).getPlaygroundEarthquakes().add(quake);
    }

    @Override
    public void paint(Graphics gr) {
        super.paint(gr);
        var g = ((Graphics2D) gr);
        String str = ((GlobalQuakePlayground) GlobalQuake.getInstance()).getWatermark();
        g.setColor(new Color(255, 100, 0, (int) ((1.0 + Math.sin(System.currentTimeMillis() / 300.0)) * 40.0 + 80)));

        Font font = GQFonts.font(Font.BOLD, 48);
        g.setFont(font);

        g.drawString(str, getWidth() / 2 - g.getFontMetrics().stringWidth(str) / 2, (getHeight() / 2 - 48 + font.getSize() / 4));

        if (insertType != InsertType.NONE) {
            if (insertType == InsertType.BRUSH_ADD || insertType == InsertType.BRUSH_DELETE) {
                if (insertType == InsertType.BRUSH_ADD) {
                    if (mouseX < 0) mouseX = getWidth() / 2;
                    if (mouseY < 0) mouseY = getHeight() / 2;
                    g.setColor(Color.WHITE);
                    g.fill(new Ellipse2D.Double(mouseX - 4, mouseY - 4, 8, 8));
                } else {
                    Color brushColor = new Color(255, 50, 50, 180);
                    Color brushFill = new Color(255, 50, 50, 60);

                    if (mouseX < 0) mouseX = getWidth() / 2;
                    if (mouseY < 0) mouseY = getHeight() / 2;

                    double radiusPx = 15.0;
                    GeoPoint geoPos = currentMouseGeoPos;

                    if (geoPos != null) {
                        var renderer = getRenderer();
                        if (renderer != null) {
                            var rps = renderer.getRenderProperties();
                            if (rps != null && rps.getRenderPrecomputedValues() != null) {
                                globalquake.ui.globe.Point2D centerPt = renderer.projectPoint(
                                        new Vector3D(
                                                GlobeRenderer.getX_3D(geoPos.lat(), geoPos.lon(), 0),
                                                GlobeRenderer.getY_3D(geoPos.lat(), geoPos.lon(), 0),
                                                GlobeRenderer.getZ_3D(geoPos.lat(), geoPos.lon(), 0)),
                                        rps);

                                double offsetLat = geoPos.lat() + brushRadiusDegrees;
                                if (offsetLat > 89.0) offsetLat = 89.0;
                                globalquake.ui.globe.Point2D edgePt = renderer.projectPoint(
                                        new Vector3D(
                                                GlobeRenderer.getX_3D(offsetLat, geoPos.lon(), 0),
                                                GlobeRenderer.getY_3D(offsetLat, geoPos.lon(), 0),
                                                GlobeRenderer.getZ_3D(offsetLat, geoPos.lon(), 0)),
                                        rps);

                                double dx = edgePt.x - centerPt.x;
                                double dy = edgePt.y - centerPt.y;
                                radiusPx = Math.sqrt(dx * dx + dy * dy);

                                if (radiusPx < 5) radiusPx = 5;
                                if (radiusPx > 1000) radiusPx = 1000;

                                mouseX = (int) centerPt.x;
                                mouseY = (int) centerPt.y;
                            }
                        }
                    }

                    g.setColor(brushColor);
                    g.setStroke(new BasicStroke(2f));
                    g.draw(new Ellipse2D.Double(mouseX - radiusPx, mouseY - radiusPx, radiusPx * 2, radiusPx * 2));
                    g.setColor(brushFill);
                    g.fill(new Ellipse2D.Double(mouseX - radiusPx, mouseY - radiusPx, radiusPx * 2, radiusPx * 2));
                    g.setColor(Color.WHITE);
                    g.fill(new Ellipse2D.Double(mouseX - 3, mouseY - 3, 6, 6));
                }
            } else {
                g.setColor(Color.white);
                g.setStroke(new BasicStroke(2f));
                g.draw(new Ellipse2D.Double(getWidth() / 2.0 - 5, getHeight() / 2.0 - 5, 10, 10));

                str = getDescription(insertType);
                g.setColor(Color.white);
                font = GQFonts.font(Font.BOLD, 32);
                g.setFont(font);
                g.drawString(str, getWidth() / 2 - g.getFontMetrics().stringWidth(str) / 2, (int) (getHeight() * 0.66 + font.getSize() / 4));
            }
        }

        GlobalQuakeFramePlayground frame = (GlobalQuakeFramePlayground) parent;
        boolean hoverToggle = frame.containsListToggle();
        boolean listHidden = frame.isListHidden();
        g.setColor(hoverToggle ? new Color(120, 120, 120) : new Color(180, 180, 180));
        g.fillRect(getWidth() - 30, 0, 30, 30);
        g.setColor(Color.white);
        g.drawRect(getWidth() - 30, 0, 30, 30);
        g.setFont(GQFonts.font(Font.BOLD, 18));
        g.setColor(Color.white);
        g.drawString(listHidden ? "<" : ">", getWidth() - 20, 21);

        if (insertType == InsertType.BRUSH_ADD || insertType == InsertType.BRUSH_DELETE) {
            Font brushFont = GQFonts.font(Font.BOLD, 28);
            g.setFont(brushFont);
            g.setColor(insertType == InsertType.BRUSH_ADD ? new Color(0, 255, 100) : new Color(255, 80, 80));
            String brushInfo = I18n.format("playground.brush.info",
                    I18n.get(insertType == InsertType.BRUSH_ADD ? "playground.brush.add" : "playground.brush.delete"),
                    String.format(Locale.ENGLISH, "%.1f", brushRadiusDegrees),
                    String.format(Locale.ENGLISH, "%.0f", brushRadiusDegrees * 111));
            int infoY = (int) (getHeight() * 0.66 + brushFont.getSize() / 4);
            g.drawString(brushInfo, getWidth() / 2 - g.getFontMetrics().stringWidth(brushInfo) / 2, infoY);
        }
    }

    private String getDescription(InsertType insertType) {
        return switch (insertType) {
            case EARTHQUAKE -> I18n.get("playground.desc.earthquake");
            case RANDOM_STATIONS -> I18n.get("playground.desc.randomStations");
            case BRUSH_ADD -> I18n.get("playground.desc.brushAdd");
            case BRUSH_DELETE -> I18n.get("playground.desc.brushDelete");
            default -> "";
        };
    }
}