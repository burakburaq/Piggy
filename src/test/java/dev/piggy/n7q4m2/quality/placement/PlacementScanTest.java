package dev.piggy.n7q4m2.quality.placement;

import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlacementScanTest {
    @Test void missesLayersOutsideFiniteReach() {
        assertNull(PlacementScan.layer(.5, 4, .5, .5, 2, 4, 0, 0, 0, 5));
        assertNull(PlacementScan.layer(.5, 2, .5, 4, 2, .5, 0, 0, 0, 5));
    }
    @Test void verticalAndBoundaryRaysKeepBothNeighborCells() {
        var bounds = PlacementScan.layer(0, 3, -1, 0, -2, -1, 0, 0, -1, 5);
        assertNotNull(bounds);
        assertTrue(bounds.minX() <= -1 && bounds.maxX() >= 0);
        assertTrue(bounds.minZ() <= -2 && bounds.maxZ() >= -1);
        assertNotNull(PlacementScan.layer(0, 1, 0, 4, 1, 0, 0, 0, 0, 5));
        assertNotNull(PlacementScan.layer(0, 1, 0, 4, 1, 0, 1, 0, 0, 5));
    }
    @Test void narrowsTypicalBridgeSearch() {
        var bounds = PlacementScan.layer(.5, 3.62, 1.5, .5, .2, 4.5, 0, 0, 1, 5);
        assertNotNull(bounds);
        assertTrue((bounds.maxX() - bounds.minX() + 1) * (bounds.maxZ() - bounds.minZ() + 1) < 25);
    }
    @Test void neverDropsARayTouchedCellWithinTheOriginalSearchArea() {
        Random random = new Random(20261015);
        for (int sample = 0; sample < 500; sample++) {
            double x = random.nextDouble() * 8 - 4, z = random.nextDouble() * 8 - 4;
            double y = random.nextDouble() * 6 - 3;
            double dx = random.nextDouble() * 10 - 5, dy = random.nextDouble() * 10 - 5, dz = random.nextDouble() * 10 - 5;
            int centerX = (int)Math.floor(x), centerZ = (int)Math.floor(z);
            for (int step = 0; step <= 100; step++) {
                double t = step / 100.0;
                int px = (int)Math.floor(x + dx * t), py = (int)Math.floor(y + dy * t), pz = (int)Math.floor(z + dz * t);
                var bounds = PlacementScan.layer(x, y, z, x + dx, y + dy, z + dz, py, centerX, centerZ, 5);
                assertNotNull(bounds);
                assertTrue(px >= bounds.minX() && px <= bounds.maxX() && pz >= bounds.minZ() && pz <= bounds.maxZ());
            }
        }
    }
}
