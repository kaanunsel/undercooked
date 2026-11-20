package undercooked.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import undercooked.net.MiniJson;

/**
 * 12x8 top-down cooperative kitchen grid matching GAME_DESIGN.md section 2.2.
 * Assigns deterministic unique resourceIds (10..49) to all stations.
 */
public class KitchenMap {

    public static final int WIDTH = 12;
    public static final int HEIGHT = 8;

    private static final double[][] SPAWNS = {
            {3.5, 2.5},
            {7.5, 2.5},
            {3.5, 5.5},
            {7.5, 5.5}
    };

    private final Station[][] grid = new Station[HEIGHT][WIDTH];
    private final List<Station> uniqueStations = new ArrayList<>();
    private final List<Station> stoves = new ArrayList<>();
    private final List<Station> tileEntries = new ArrayList<>(); // includes both serving window tiles for rendering

    public KitchenMap(int maxThreadSlots) {
        int nextId = 10;

        // Row 0: [W][B][M][C][S0][S1][C][L][T][H][C][W]
        Station.Type[] row0 = {
                Station.Type.WALL,
                Station.Type.CRATE_BUN,
                Station.Type.CRATE_MEAT,
                Station.Type.COUNTER,
                Station.Type.STOVE,
                Station.Type.STOVE,
                Station.Type.COUNTER,
                Station.Type.CRATE_LETTUCE,
                Station.Type.CRATE_TOMATO,
                Station.Type.CRATE_CHEESE,
                Station.Type.COUNTER,
                Station.Type.WALL
        };
        for (int x = 0; x < WIDTH; x++) {
            nextId = place(x, 0, row0[x], nextId, maxThreadSlots);
        }

        // Row 1: [C] . . . . . . . . . . [P]
        nextId = place(0, 1, Station.Type.COUNTER, nextId, maxThreadSlots);
        nextId = place(11, 1, Station.Type.PLATE_DISPENSER, nextId, maxThreadSlots);

        // Row 2: [CB0] . . . . . . . . . . [C]
        nextId = place(0, 2, Station.Type.CHOPPING_BOARD, nextId, maxThreadSlots);
        nextId = place(11, 2, Station.Type.COUNTER, nextId, maxThreadSlots);

        // Row 3: [CB1] . . . [C] [C] . . . . . [SV]
        nextId = place(0, 3, Station.Type.CHOPPING_BOARD, nextId, maxThreadSlots);
        nextId = place(4, 3, Station.Type.COUNTER, nextId, maxThreadSlots);
        nextId = place(5, 3, Station.Type.COUNTER, nextId, maxThreadSlots);
        Station servingWindow = new Station(nextId++, 11, 3, Station.Type.SERVING_WINDOW, maxThreadSlots);
        grid[3][11] = servingWindow;
        grid[4][11] = servingWindow; // Both (11,3) and (11,4) share one logical serving station
        uniqueStations.add(servingWindow);
        tileEntries.add(servingWindow);
        tileEntries.add(new Station(servingWindow.getResourceId(), 11, 4, Station.Type.SERVING_WINDOW, maxThreadSlots));

        // Row 4: [C] . . . . . . . . . . [SV]
        nextId = place(0, 4, Station.Type.COUNTER, nextId, maxThreadSlots);

        // Row 5: [C] . . . . . . . . . . [C]
        nextId = place(0, 5, Station.Type.COUNTER, nextId, maxThreadSlots);
        nextId = place(11, 5, Station.Type.COUNTER, nextId, maxThreadSlots);

        // Row 6: [TR] . . . . . . . . . . [C]
        nextId = place(0, 6, Station.Type.TRASH_BIN, nextId, maxThreadSlots);
        nextId = place(11, 6, Station.Type.COUNTER, nextId, maxThreadSlots);

        // Row 7: [W][C][C][C][C][C][C][C][C][C][C][W]
        nextId = place(0, 7, Station.Type.WALL, nextId, maxThreadSlots);
        for (int x = 1; x <= 10; x++) {
            nextId = place(x, 7, Station.Type.COUNTER, nextId, maxThreadSlots);
        }
        place(11, 7, Station.Type.WALL, nextId, maxThreadSlots);
    }

    private int place(int x, int y, Station.Type type, int id, int maxThreadSlots) {
        Station s = new Station(id, x, y, type, maxThreadSlots);
        grid[y][x] = s;
        uniqueStations.add(s);
        tileEntries.add(s);
        if (type == Station.Type.STOVE) {
            stoves.add(s);
        }
        return id + 1;
    }

    public boolean isWalkable(double x, double y) {
        double radius = 0.28;
        return isTileWalkable((int) Math.floor(x - radius), (int) Math.floor(y - radius))
                && isTileWalkable((int) Math.floor(x + radius), (int) Math.floor(y - radius))
                && isTileWalkable((int) Math.floor(x - radius), (int) Math.floor(y + radius))
                && isTileWalkable((int) Math.floor(x + radius), (int) Math.floor(y + radius));
    }

    private boolean isTileWalkable(int tx, int ty) {
        if (tx < 0 || tx >= WIDTH || ty < 0 || ty >= HEIGHT) {
            return false;
        }
        return grid[ty][tx] == null;
    }

    public Station getStationAt(int tx, int ty) {
        if (tx < 0 || tx >= WIDTH || ty < 0 || ty >= HEIGHT) {
            return null;
        }
        return grid[ty][tx];
    }

    /**
     * Resolves the station directly in front of a chef based on position and facing direction.
     */
    public Station getFacingStation(double x, double y, String facing) {
        double reach = 0.85;
        double targetX = x;
        double targetY = y;
        switch (facing) {
            case "N" -> targetY -= reach;
            case "S" -> targetY += reach;
            case "E" -> targetX += reach;
            case "W" -> targetX -= reach;
            default -> targetY += reach;
        }
        int tx = (int) Math.floor(targetX);
        int ty = (int) Math.floor(targetY);
        Station s = getStationAt(tx, ty);
        if (s != null && s.getType() != Station.Type.WALL) {
            return s;
        }
        return null;
    }

    public double[] getSpawn(int slot) {
        return SPAWNS[slot % SPAWNS.length];
    }

    public List<Station> getUniqueStations() {
        return Collections.unmodifiableList(uniqueStations);
    }

    public List<Station> getStoves() {
        return Collections.unmodifiableList(stoves);
    }

    public void resetAllStations() {
        for (Station s : uniqueStations) {
            s.reset();
        }
    }

    public String stationsStaticJson() {
        MiniJson.Arr arr = new MiniJson.Arr();
        for (Station s : tileEntries) {
            arr.addRaw(s.toStaticJson());
        }
        return arr.toString();
    }
}
