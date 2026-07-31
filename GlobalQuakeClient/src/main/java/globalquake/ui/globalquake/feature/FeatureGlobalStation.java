package globalquake.ui.globalquake.feature;

import globalquake.core.GQFonts;

import globalquake.client.GlobalQuakeClient;
import globalquake.core.analysis.AnalysisStatus;
import globalquake.core.analysis.Event;
import globalquake.core.earthquake.data.Cluster;
import globalquake.core.station.AbstractStation;
import globalquake.ui.globe.GlobeRenderer;
import globalquake.ui.globe.Point2D;
import globalquake.ui.globe.Polygon3D;
import globalquake.ui.globe.RenderProperties;
import globalquake.ui.globe.feature.RenderElement;
import globalquake.ui.globe.feature.RenderEntity;
import globalquake.ui.globe.feature.RenderFeature;
import globalquake.core.Settings;
import globalquake.core.intensity.IntensityScales;
import globalquake.core.intensity.Level;
import globalquake.ui.i18n.I18n;
import globalquake.ui.settings.StationsShape;
import globalquake.ui.stationselect.FeatureSelectableStation;
import globalquake.utils.Scale;
import gqserver.api.packets.station.InputType;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import java.awt.*;
import java.util.Collection;
import java.util.List;

public class FeatureGlobalStation extends RenderFeature<AbstractStation> {

    private final Collection<AbstractStation> globalStations;

    public static final double RATIO_YELLOW = 2000.0;
    public static final double RATIO_RED = 20000.0;

    public FeatureGlobalStation(Collection<AbstractStation> globalStations) {
        super(2);
        this.globalStations = globalStations;
    }

    public static double computePGA(AbstractStation station) {
        double ratio = station.getMaxRatio60S();
        if (ratio < 5.0) return 0;
        double ratioGain = Math.pow(ratio / 10.0, 0.5);

        if (station.isSensitivityValid()) {
            double velCounts = station.getMaxVelocity60S();
            if (velCounts <= 0) {
                return Math.min(1.0, ratioGain * 0.5);
            }
            double physV = velCounts / station.getSensitivity();
            double pga = physV * 2.0 * Math.PI * 1.2 * 100.0;
            return Math.max(pga, pga * Math.pow(ratio / 1000.0, 0.35)) * Math.max(1.0, ratioGain * 0.25);
        }

        // Unknown sensitivity (Seedlink remote stations): ratio-only empirical fit
        return 0.040 * Math.pow(ratio, 0.74);
    }

    public static Level computeIntensityLevel(AbstractStation station) {
        double pga = computePGA(station);
        if (pga < 0.5) return null;
        return IntensityScales.getIntensityScale().getLevel(pga);
    }

    @Override
    public Collection<AbstractStation> getElements() {
        return globalStations;
    }

    @Override
    public void createPolygon(GlobeRenderer renderer, RenderEntity<AbstractStation> entity, RenderProperties renderProperties) {
        RenderElement elementStationCircle = entity.getRenderElement(0);
        RenderElement elementStationSquare = entity.getRenderElement(1);
        if (elementStationCircle.getPolygon() == null) {
            elementStationCircle.setPolygon(new Polygon3D());
        }
        if (elementStationSquare.getPolygon() == null) {
            elementStationSquare.setPolygon(new Polygon3D());
        }

        double size = Math.min(36, renderer.pxToDeg(7.0, renderProperties)) * Settings.stationsSizeMul;

        if(Math.abs(size - entity.getOriginal()._lastRenderSize) < 0.1){
            return;
        }

        entity.getOriginal()._lastRenderSize = size;

        InputType inputType = entity.getOriginal().getInputType();

        StationsShape shape = StationsShape.values()[Settings.stationsShapeIndex];

        if(shape == StationsShape.CIRCLE){
            inputType = InputType.UNKNOWN;
        } else if(shape == StationsShape.TRIANGLE){
            inputType = InputType.VELOCITY;
        }

        switch (inputType){
            case UNKNOWN ->
                    renderer.createCircle(elementStationCircle.getPolygon(),
                            entity.getOriginal().getLatitude(),
                            entity.getOriginal().getLongitude(),
                            size, 0, 30);
            case VELOCITY ->
                    renderer.createTriangle(elementStationCircle.getPolygon(),
                            entity.getOriginal().getLatitude(),
                            entity.getOriginal().getLongitude(),
                            size * 1.41, 0, 0);
            case ACCELERATION ->
                    renderer.createTriangle(elementStationCircle.getPolygon(),
                            entity.getOriginal().getLatitude(),
                            entity.getOriginal().getLongitude(),
                            size * 1.41, 0, 180);
            case DISPLACEMENT ->
                    renderer.createSquare(elementStationCircle.getPolygon(),
                            entity.getOriginal().getLatitude(),
                            entity.getOriginal().getLongitude(),
                            size * 1.41, 0);
        }

        renderer.createSquare(elementStationSquare.getPolygon(),
                entity.getOriginal().getLatitude(),
                entity.getOriginal().getLongitude(),
                size * 2.0, 0);
    }

    @Override
    public boolean needsUpdateEntities() {
        return true;
    }

    @Override
    public boolean needsCreatePolygon(RenderEntity<AbstractStation> entity, boolean propertiesChanged) {
        return propertiesChanged;
    }

    @Override
    public boolean needsProject(RenderEntity<AbstractStation> entity, boolean propertiesChanged) {
        return propertiesChanged;
    }

    @Override
    public void project(GlobeRenderer renderer, RenderEntity<AbstractStation> entity, RenderProperties renderProperties) {
        RenderElement elementStationCircle = entity.getRenderElement(0);
        elementStationCircle.getShape().reset();
        elementStationCircle.shouldDraw = renderer.project3D(elementStationCircle.getShape(), elementStationCircle.getPolygon(), true, renderProperties);

        RenderElement elementStationSquare = entity.getRenderElement(1);
        elementStationSquare.getShape().reset();
        elementStationSquare.shouldDraw = renderer.project3D(elementStationSquare.getShape(), elementStationSquare.getPolygon(), true, renderProperties);
    }

    @Override
    public boolean isEntityVisible(RenderEntity<?> entity) {
        AbstractStation station = (AbstractStation) entity.getOriginal();

        if(Settings.hideDeadStations && !station.hasDisplayableData()){
            return false;
        }

        return !station.disabled;
    }

    @Override
    public void render(GlobeRenderer renderer, Graphics2D graphics, RenderEntity<AbstractStation> entity, RenderProperties renderProperties) {
        RenderElement elementStationCircle = entity.getRenderElement(0);

        if(!elementStationCircle.shouldDraw){
            return;
        }

        AbstractStation station = entity.getOriginal();
        Level level = computeIntensityLevel(station);

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                Settings.antialiasing ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);

        Vector3D point3D = GlobeRenderer.createVec3D(getCenterCoords(entity));
        Point2D centerPoint = renderer.projectPoint(point3D, renderProperties);

        RenderElement elementStationSquare = entity.getRenderElement(1);

        if (level != null && centerPoint != null) {
            List<Level> levels = IntensityScales.getIntensityScale().getLevels();
            int levelIdx = levels.indexOf(level);
            double radiusPx = (12.0 + Math.max(0, levelIdx) * 0.9) * Settings.stationsSizeMul;
            int r = (int) Math.round(radiusPx);
            int cx = (int) Math.round(centerPoint.x);
            int cy = (int) Math.round(centerPoint.y);
            graphics.setColor(level.getColor());
            graphics.fillOval(cx - r, cy - r, r * 2, r * 2);
            graphics.setColor(Color.white);
            graphics.setStroke(new BasicStroke(1.5f));
            graphics.drawOval(cx - r, cy - r, r * 2, r * 2);
            graphics.setStroke(new BasicStroke(1f));
            int fontSize = Math.max(10, Math.min(r * 2 / Math.max(1, level.getFullName().length()), r));
            graphics.setFont(GQFonts.font(Font.BOLD, fontSize));
            FontMetrics fm = graphics.getFontMetrics();
            String label = level.getFullName();
            int tw = fm.stringWidth(label);
            int th = fm.getAscent();
            graphics.setColor(Color.black);
            graphics.drawString(label, cx - tw / 2 + 1, cy + th / 2 - 2 + 1);
            graphics.setColor(Color.white);
            graphics.drawString(label, cx - tw / 2, cy + th / 2 - 2);
        } else {
            graphics.setColor(getDisplayColor(station));
            graphics.fill(elementStationCircle.getShape());
        }

        boolean mouseNearby = renderer.getLastMouse() != null && renderer.hasMouseMovedRecently() && elementStationCircle.getShape().contains(renderer.getLastMouse());

        if (mouseNearby && renderProperties.scroll < 1) {
            graphics.setColor(Color.yellow);
            graphics.setStroke(new BasicStroke(2f));
            graphics.draw(elementStationCircle.getShape());
        }

        graphics.setStroke(new BasicStroke(1f));

        graphics.setFont(GQFonts.font(Font.PLAIN, 13));

        if(Settings.displayClusters){
            for(Event event2 : station.getAnalysis().getDetectedEvents()){
                Cluster cluster = event2.assignedCluster;
                if(cluster != null){
                    Color c = !event2.isValid() ? Color.gray : cluster.color;

                    int _y = (int) centerPoint.y + 4;
                    _y += 16;

                    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

                    graphics.setColor(c);
                    graphics.draw(elementStationSquare.getShape());
                    graphics.drawString("Cluster #"+cluster.id, (int) centerPoint.x + 12, _y);
                }
            }
        } else if (station.isInEventMode() && ((System.currentTimeMillis() / 500) % 2 == 0)) {
            Color c = Color.green;

            double maxRatio = station.getMaxRatio60S();

            if (maxRatio >= RATIO_YELLOW) {
                c = Color.yellow;
            }

            if (maxRatio >= RATIO_RED) {
                c = Color.red;
            }

            graphics.setColor(c);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, Settings.antialiasing ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.draw(elementStationSquare.getShape());
        }


        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        drawDetails(mouseNearby, renderProperties.scroll, centerPoint, graphics, station, renderer, entity, renderProperties, level);
    }

    private void drawDetails(boolean mouseNearby, double scroll, Point2D centerPoint, Graphics2D g, AbstractStation station, GlobeRenderer renderer,
                             RenderEntity<AbstractStation> entity, RenderProperties renderProperties, Level level) {
        int _y = (int) (7 + 6 * Settings.stationsSizeMul);
        if (mouseNearby && scroll < 1) {
            g.setColor(Color.white);
            String str = station.toString();

            if(centerPoint == null) {
                var point3D = GlobeRenderer.createVec3D(getCenterCoords(entity));
                centerPoint = renderer.projectPoint(point3D, renderProperties);
            }

            int x = (int) centerPoint.x;
            int y = (int) centerPoint.y;

            g.drawString(str, x - g.getFontMetrics().stringWidth(str) / 2, y - _y);
            str = station.getSeedlinkNetwork() == null ? "" : station.getSeedlinkNetwork().getName();
            g.drawString(str, x - g.getFontMetrics().stringWidth(str) / 2, y - _y - 15);

            if(!station.hasDisplayableData()){
                str = "No data";
                g.drawString(str, x - g.getFontMetrics().stringWidth(str) / 2, y + _y + 22);
            } else {
                long delay = station.getDelayMS();
                if (delay == Long.MIN_VALUE) {
                    g.setColor(Color.magenta);
                    str = "Replay";
                    g.drawString(str, x - g.getFontMetrics().stringWidth(str) / 2, y + _y + 22);
                } else {
                    FeatureSelectableStation.drawDelay(g, x, y + 33, delay, "Delay");
                }
            }
        }
        if (scroll < Settings.stationIntensityVisibilityZoomLevel || (mouseNearby && scroll < 1)) {
            if (level == null) {
                String str = !station.hasDisplayableData() ? "-.-" : "%.1f".formatted(station.getMaxRatio60S());
                g.setFont(GQFonts.font(Font.PLAIN, 13));
                g.setColor(station.getAnalysis().getStatus() == AnalysisStatus.EVENT ? Color.green : Color.LIGHT_GRAY);
                if(centerPoint == null) {
                    var point3D = GlobeRenderer.createVec3D(getCenterCoords(entity));
                    centerPoint = renderer.projectPoint(point3D, renderProperties);
                }

                int x = (int) centerPoint.x;
                int y = (int) centerPoint.y;
                g.drawString(str, x - g.getFontMetrics().stringWidth(str) / 2, y + _y + 9);
            }
        }
    }

    private Color getDisplayColor(AbstractStation station) {
        if(station.disabled){
            return Color.DARK_GRAY;
        }
        if (!station.hasData()) {
            return Color.gray;
        }

        if ((GlobalQuakeClient.instance == null && station.getAnalysis().getStatus() == AnalysisStatus.INIT) || !station.hasDisplayableData()) {
            return Color.lightGray;
        } else {
            return Scale.getColorRatio(station.getMaxRatio60S());
        }

    }

    @Override
    public Point2D getCenterCoords(RenderEntity<?> entity) {
        return new Point2D(((AbstractStation) (entity.getOriginal())).getLatitude(), ((AbstractStation) (entity.getOriginal())).getLongitude());
    }
}
