package com.nostalgiape.client.util;

/** Column-major 4x4 matrix helpers for the GL pipeline (clean-room). */
public final class Mat4 {
    private Mat4() {}

    public static float[] identity() {
        return new float[]{1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
    }

    public static float[] perspective(float fovYDeg, float aspect, float near, float far) {
        float f = (float) (1.0 / Math.tan(Math.toRadians(fovYDeg) / 2.0));
        float[] m = new float[16];
        m[0] = f / aspect;
        m[5] = f;
        m[10] = (far + near) / (near - far);
        m[11] = -1;
        m[14] = (2 * far * near) / (near - far);
        return m;
    }

    public static float[] multiply(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0;
                for (int k = 0; k < 4; k++) sum += a[k * 4 + row] * b[c * 4 + k];
                r[c * 4 + row] = sum;
            }
        }
        return r;
    }

    /** Builds a view matrix from yaw/pitch (degrees) and eye position. */
    public static float[] fpsView(float eyeX, float eyeY, float eyeZ, float pitchDeg, float yawDeg) {
        double pitch = Math.toRadians(pitchDeg);
        double yaw = Math.toRadians(yawDeg);
        float cosPitch = (float) Math.cos(pitch);
        float sinPitch = (float) Math.sin(pitch);
        float cosYaw = (float) Math.cos(yaw);
        float sinYaw = (float) Math.sin(yaw);

        // forward vector
        float fx = cosPitch * sinYaw;
        float fy = -sinPitch;
        float fz = -cosPitch * cosYaw;
        // right = normalize(cross(forward, worldUp))
        float rx = cosYaw;
        float ry = 0;
        float rz = sinYaw;
        // up = cross(right, forward)
        float ux = ry * fz - rz * fy;
        float uy = rz * fx - rx * fz;
        float uz = rx * fy - ry * fx;

        float[] m = new float[16];
        m[0] = rx;  m[4] = ry;  m[8]  = rz;  m[12] = -(rx*eyeX + ry*eyeY + rz*eyeZ);
        m[1] = ux;  m[5] = uy;  m[9]  = uz;  m[13] = -(ux*eyeX + uy*eyeY + uz*eyeZ);
        m[2] = -fx; m[6] = -fy; m[10] = -fz; m[14] = (fx*eyeX + fy*eyeY + fz*eyeZ);
        m[3] = 0;   m[7] = 0;   m[11] = 0;   m[15] = 1;
        return m;
    }
}
