package ftcsim.hal;

import org.firstinspires.ftc.robotcore.external.navigation.AxesOrder;
import org.firstinspires.ftc.robotcore.external.navigation.AxesReference;

/**
 * Rotation math shared by the navigation classes and the simulated sensors.
 * Matrices are row-major double[3][3] and act on column vectors.
 */
public final class Rotations {
    private Rotations() {
    }

    public static double[][] identity() {
        return new double[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
    }

    /** Right-handed rotation by {@code angle} radians about axis 0 (X), 1 (Y) or 2 (Z). */
    public static double[][] axis(int axis, double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        switch (axis) {
            case 0: return new double[][]{{1, 0, 0}, {0, c, -s}, {0, s, c}};
            case 1: return new double[][]{{c, 0, s}, {0, 1, 0}, {-s, 0, c}};
            default: return new double[][]{{c, -s, 0}, {s, c, 0}, {0, 0, 1}};
        }
    }

    public static double[][] multiply(double[][] a, double[][] b) {
        double[][] r = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
            }
        }
        return r;
    }

    public static double[][] transpose(double[][] a) {
        double[][] r = new double[3][3];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) r[i][j] = a[j][i];
        return r;
    }

    public static double[] apply(double[][] m, double[] v) {
        return new double[]{
                m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
                m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
                m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]};
    }

    /** Rotation matrix for three Euler angles (radians), in the SDK's axes-reference conventions. */
    public static double[][] fromEuler(AxesReference reference, AxesOrder order, double a1, double a2, double a3) {
        int[] ix = order.indices();
        if (reference == AxesReference.INTRINSIC) {
            // Axes move with the body: R = R1 * R2 * R3
            return multiply(multiply(axis(ix[0], a1), axis(ix[1], a2)), axis(ix[2], a3));
        } else {
            // Axes fixed in the world: the first rotation is applied first, R = R3 * R2 * R1
            return multiply(multiply(axis(ix[2], a3), axis(ix[1], a2)), axis(ix[0], a1));
        }
    }

    /** Euler angles (radians, in chronological order) of a rotation matrix. */
    public static double[] toEuler(double[][] r, AxesReference reference, AxesOrder order) {
        int[] ix = order.indices();
        if (reference == AxesReference.INTRINSIC) {
            return intrinsic(r, ix[0], ix[1], ix[2]);
        }
        // Extrinsic (a1, a2, a3) equals intrinsic (a3, a2, a1) with the angles reversed.
        double[] rev = intrinsic(r, ix[2], ix[1], ix[0]);
        return new double[]{rev[2], rev[1], rev[0]};
    }

    private static boolean cyclic(int p, int q, int r) {
        return (p == 0 && q == 1 && r == 2) || (p == 1 && q == 2 && r == 0) || (p == 2 && q == 0 && r == 1);
    }

    /** Angles (alpha, beta, gamma) such that R = R_p(alpha) * R_q(beta) * R_r(gamma). */
    private static double[] intrinsic(double[][] m, int p, int q, int r) {
        final double eps = 1e-9;
        if (p != r) {
            // Tait-Bryan angles
            double e = cyclic(p, q, r) ? 1 : -1;
            double sb = clamp(e * m[p][r]);
            double beta = Math.asin(sb);
            double alpha, gamma;
            if (Math.abs(sb) < 1 - eps) {
                alpha = Math.atan2(-e * m[q][r], m[r][r]);
                gamma = Math.atan2(-e * m[p][q], m[p][p]);
            } else {
                gamma = 0;
                alpha = Math.atan2(e * m[r][q], m[q][q]);
            }
            return new double[]{alpha, beta, gamma};
        } else {
            // Proper Euler angles
            int s = 3 - p - q;
            double e = cyclic(p, q, s) ? 1 : -1;
            double cb = clamp(m[p][p]);
            double beta = Math.acos(cb);
            double alpha, gamma;
            if (Math.abs(cb) < 1 - eps) {
                alpha = Math.atan2(m[q][p], -e * m[s][p]);
                gamma = Math.atan2(m[p][q], e * m[p][s]);
            } else {
                gamma = 0;
                alpha = Math.atan2(e * m[s][q], m[q][q]);
            }
            return new double[]{alpha, beta, gamma};
        }
    }

    private static double clamp(double v) {
        return Math.max(-1, Math.min(1, v));
    }

    /** An {@link org.firstinspires.ftc.robotcore.external.navigation.Orientation} for a rotation matrix. */
    public static org.firstinspires.ftc.robotcore.external.navigation.Orientation toOrientation(
            double[][] m, AxesReference reference, AxesOrder order,
            org.firstinspires.ftc.robotcore.external.navigation.AngleUnit unit, long acquisitionTime) {
        double[] a = toEuler(m, reference, order);
        return new org.firstinspires.ftc.robotcore.external.navigation.Orientation(reference, order, unit,
                (float) unit.fromRadians(a[0]), (float) unit.fromRadians(a[1]), (float) unit.fromRadians(a[2]),
                acquisitionTime);
    }

    /** Quaternion (w, x, y, z) of a rotation matrix. */
    public static double[] toQuaternion(double[][] m) {
        double trace = m[0][0] + m[1][1] + m[2][2];
        double w, x, y, z;
        if (trace > 0) {
            double s = 0.5 / Math.sqrt(trace + 1.0);
            w = 0.25 / s;
            x = (m[2][1] - m[1][2]) * s;
            y = (m[0][2] - m[2][0]) * s;
            z = (m[1][0] - m[0][1]) * s;
        } else if (m[0][0] > m[1][1] && m[0][0] > m[2][2]) {
            double s = 2.0 * Math.sqrt(1.0 + m[0][0] - m[1][1] - m[2][2]);
            w = (m[2][1] - m[1][2]) / s;
            x = 0.25 * s;
            y = (m[0][1] + m[1][0]) / s;
            z = (m[0][2] + m[2][0]) / s;
        } else if (m[1][1] > m[2][2]) {
            double s = 2.0 * Math.sqrt(1.0 + m[1][1] - m[0][0] - m[2][2]);
            w = (m[0][2] - m[2][0]) / s;
            x = (m[0][1] + m[1][0]) / s;
            y = 0.25 * s;
            z = (m[1][2] + m[2][1]) / s;
        } else {
            double s = 2.0 * Math.sqrt(1.0 + m[2][2] - m[0][0] - m[1][1]);
            w = (m[1][0] - m[0][1]) / s;
            x = (m[0][2] + m[2][0]) / s;
            y = (m[1][2] + m[2][1]) / s;
            z = 0.25 * s;
        }
        double n = Math.sqrt(w * w + x * x + y * y + z * z);
        if (w < 0) n = -n;   // canonical form with w >= 0
        return new double[]{w / n, x / n, y / n, z / n};
    }

    /** Rotation matrix of a quaternion (w, x, y, z); the quaternion need not be normalized. */
    public static double[][] fromQuaternion(double w, double x, double y, double z) {
        double n = Math.sqrt(w * w + x * x + y * y + z * z);
        if (n == 0) return identity();
        w /= n; x /= n; y /= n; z /= n;
        return new double[][]{
                {1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)},
                {2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)},
                {2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)}};
    }
}
