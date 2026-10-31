package dev.piggy.n7q4m2.quality.placement;

/** Conservative bounds of a finite sight ray inside one horizontal block layer. */
public final class PlacementScan {
    private PlacementScan() {}
    public record Bounds(int minX, int maxX, int minZ, int maxZ) {}
    public static Bounds layer(double eyeX, double eyeY, double eyeZ, double endX, double endY, double endZ,
                               int y, int centerX, int centerZ, int radius) {
        double dy = endY - eyeY, enter = 0, exit = 1;
        if (dy == 0) {
            if (eyeY < y - 1e-6 || eyeY > y + 1.0 + 1e-6) return null;
        } else {
            double a = (y - 1e-6 - eyeY) / dy, b = (y + 1.0 + 1e-6 - eyeY) / dy;
            enter = Math.max(0, Math.min(a, b));
            exit = Math.min(1, Math.max(a, b));
            if (enter > exit) return null;
        }
        double x0 = eyeX + (endX - eyeX) * enter, x1 = eyeX + (endX - eyeX) * exit;
        double z0 = eyeZ + (endZ - eyeZ) * enter, z1 = eyeZ + (endZ - eyeZ) * exit;
        // Include neighbors at integer boundaries; the existing exact box/ray test decides acceptance.
        int minX = Math.max(centerX - radius, (int)Math.floor(Math.min(x0, x1)) - 1);
        int maxX = Math.min(centerX + radius, (int)Math.floor(Math.max(x0, x1)) + 1);
        int minZ = Math.max(centerZ - radius, (int)Math.floor(Math.min(z0, z1)) - 1);
        int maxZ = Math.min(centerZ + radius, (int)Math.floor(Math.max(z0, z1)) + 1);
        return minX > maxX || minZ > maxZ ? null : new Bounds(minX, maxX, minZ, maxZ);
    }
}
