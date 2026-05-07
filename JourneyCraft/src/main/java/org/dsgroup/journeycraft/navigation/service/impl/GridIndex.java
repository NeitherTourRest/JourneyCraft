package org.dsgroup.journeycraft.navigation.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.dsgroup.journeycraft.navigation.entity.RoadNode;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Grid-based spatial index for O(1) nearest-node lookup.
 * <p>
 * Divides the map area into a 256×256 grid, each cell stores node indices.
 * Search starts from the target cell and expands outward in a spiral pattern.
 *
 * @author 后端智能体
 * @since 2026-05-07
 */
@Slf4j
@Component
public class GridIndex {

    private static final int GRID_SIZE = 256;

    private double minLat, maxLat, minLng, maxLng;
    private double latStep, lngStep;
    private boolean built = false;

    // cells[row][col] = list of node indices in that grid cell
    private List<Integer>[][] cells;

    // Coordinate arrays for distance computation
    private double[] nodeLats;
    private double[] nodeLngs;

    @PostConstruct
    public void init() {
        log.info("[GridIndex] Initialized (built on demand)");
    }

    /**
     * Build the grid from loaded node data.
     *
     * @param nodes     list of RoadNode objects
     * @param nodeCount number of nodes to index (may be less than list size)
     */
    @SuppressWarnings("unchecked")
    public void build(List<RoadNode> nodes, int nodeCount) {
        if (nodes == null || nodes.isEmpty()) return;

        int effectiveCount = Math.min(nodeCount, nodes.size());

        // 1. Compute bounds
        minLat = maxLat = nodes.get(0).getLatitude().doubleValue();
        minLng = maxLng = nodes.get(0).getLongitude().doubleValue();
        for (int i = 0; i < effectiveCount; i++) {
            RoadNode n = nodes.get(i);
            if (n.getLatitude() == null || n.getLongitude() == null) continue;
            double lat = n.getLatitude().doubleValue();
            double lng = n.getLongitude().doubleValue();
            if (lat < minLat) minLat = lat;
            if (lat > maxLat) maxLat = lat;
            if (lng < minLng) minLng = lng;
            if (lng > maxLng) maxLng = lng;
        }

        latStep = (maxLat - minLat) / GRID_SIZE + 1e-10;
        lngStep = (maxLng - minLng) / GRID_SIZE + 1e-10;

        // 2. Initialize grid
        cells = new List[GRID_SIZE][GRID_SIZE];
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                cells[r][c] = new ArrayList<>();
            }
        }

        // 3. Populate coordinate arrays
        nodeLats = new double[effectiveCount];
        nodeLngs = new double[effectiveCount];

        // 4. Assign nodes to cells
        for (int i = 0; i < effectiveCount; i++) {
            RoadNode n = nodes.get(i);
            if (n.getLatitude() == null || n.getLongitude() == null) continue;
            double lat = n.getLatitude().doubleValue();
            double lng = n.getLongitude().doubleValue();
            nodeLats[i] = lat;
            nodeLngs[i] = lng;
            int row = (int) ((lat - minLat) / latStep);
            int col = (int) ((lng - minLng) / lngStep);
            row = Math.max(0, Math.min(GRID_SIZE - 1, row));
            col = Math.max(0, Math.min(GRID_SIZE - 1, col));
            cells[row][col].add(i);
        }

        built = true;
        log.info("[GridIndex] Built: {}×{} grid, {} nodes, bounds [{},{}]×[{},{}]",
                GRID_SIZE, GRID_SIZE, effectiveCount, minLat, maxLat, minLng, maxLng);
    }

    /**
     * Find nearest node index to given coordinates.
     * Searches current cell first, then expands outward in spiral pattern.
     *
     * @param lat target latitude
     * @param lng target longitude
     * @return node index, or -1 if not found
     */
    public int findNearest(double lat, double lng) {
        if (!built) return -1;

        int centerRow = (int) ((lat - minLat) / latStep);
        int centerCol = (int) ((lng - minLng) / lngStep);
        centerRow = Math.max(0, Math.min(GRID_SIZE - 1, centerRow));
        centerCol = Math.max(0, Math.min(GRID_SIZE - 1, centerCol));

        // Spiral search outward
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int dr = -r; dr <= r; dr++) {
                int absDc = r - Math.abs(dr);
                if (absDc == 0) {
                    int row = centerRow + dr;
                    int col = centerCol;
                    if (isValidCell(row, col)) {
                        int idx = findClosestInCell(row, col, lat, lng);
                        if (idx >= 0) return idx;
                    }
                } else {
                    for (int sign : new int[]{-1, 1}) {
                        int row = centerRow + dr;
                        int col = centerCol + absDc * sign;
                        if (isValidCell(row, col)) {
                            int idx = findClosestInCell(row, col, lat, lng);
                            if (idx >= 0) return idx;
                        }
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Set coordinate arrays externally (if build() was not used).
     */
    public void setNodeCoords(double[] lats, double[] lngs) {
        this.nodeLats = lats;
        this.nodeLngs = lngs;
    }

    /**
     * Whether the index has been built.
     */
    public boolean isBuilt() {
        return built;
    }

    // ── private helpers ──────────────────────────────────────────

    private boolean isValidCell(int row, int col) {
        return row >= 0 && row < GRID_SIZE && col >= 0 && col < GRID_SIZE;
    }

    private int findClosestInCell(int row, int col, double lat, double lng) {
        int best = -1;
        double minDist = Double.MAX_VALUE;
        for (int idx : cells[row][col]) {
            if (idx < 0 || idx >= nodeLats.length) continue;
            double d = haversineKm(lat, lng, nodeLats[idx], nodeLngs[idx]);
            if (d < minDist) {
                minDist = d;
                best = idx;
            }
        }
        return best;
    }

    /**
     * Haversine distance in kilometers between two lat/lng points.
     */
    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
