package com.nostalgiape.client.render;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import com.nostalgiape.client.entity.LocalPlayer;
import com.nostalgiape.client.util.Mat4;
import com.nostalgiape.client.world.World;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * OpenGL ES 2.0 renderer. Draws chunk meshes with a simple coloured-vertex
 * shader and a sky-coloured clear. Clean-room; no external engine used.
 */
public class WorldRenderer implements GLSurfaceView.Renderer {

    private static final String VERT_SRC =
            "uniform mat4 uMVP;\n" +
            "attribute vec3 aPos;\n" +
            "attribute vec3 aColor;\n" +
            "varying vec3 vColor;\n" +
            "void main() {\n" +
            "  vColor = aColor;\n" +
            "  gl_Position = uMVP * vec4(aPos, 1.0);\n" +
            "}\n";

    private static final String FRAG_SRC =
            "precision mediump float;\n" +
            "varying vec3 vColor;\n" +
            "void main() {\n" +
            "  gl_FragColor = vec4(vColor, 1.0);\n" +
            "}\n";

    private final World world;
    private final LocalPlayer player;

    private int program;
    private int aPos, aColor, uMVP;
    private int width, height;

    private final Map<Long, ChunkMesh> meshes = new HashMap<>();

    public WorldRenderer(World world, LocalPlayer player) {
        this.world = world;
        this.player = player;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.58f, 0.72f, 0.98f, 1f); // classic sky blue
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glFrontFace(GLES20.GL_CCW);

        int vs = compile(GLES20.GL_VERTEX_SHADER, VERT_SRC);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, FRAG_SRC);
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);
        aPos = GLES20.glGetAttribLocation(program, "aPos");
        aColor = GLES20.glGetAttribLocation(program, "aColor");
        uMVP = GLES20.glGetUniformLocation(program, "uMVP");
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        this.width = w;
        this.height = h;
        GLES20.glViewport(0, 0, w, h);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glUseProgram(program);

        float aspect = height == 0 ? 1f : (float) width / height;
        float[] proj = Mat4.perspective(70f, aspect, 0.05f, 256f);
        float[] view = Mat4.fpsView(player.x, player.eyeY(), player.z, player.pitch, player.yaw);
        float[] mvp = Mat4.multiply(proj, view);
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0);

        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glEnableVertexAttribArray(aColor);

        // rebuild dirty chunk meshes (bounded per frame to avoid hitching)
        int rebuilt = 0;
        for (World.Chunk c : world.chunks()) {
            long k = (((long) c.cx) & 0xFFFFFFFFL) | ((((long) c.cz) & 0xFFFFFFFFL) << 32);
            if (c.dirty && rebuilt < 2) {
                ChunkMesh m = new ChunkMesh();
                m.build(world, c);
                meshes.put(k, m);
                c.dirty = false;
                rebuilt++;
            }
            ChunkMesh mesh = meshes.get(k);
            if (mesh != null && mesh.vertexCount > 0) {
                FloatBuffer buf = mesh.buffer;
                buf.position(0);
                GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 24, buf);
                buf.position(3);
                GLES20.glVertexAttribPointer(aColor, 3, GLES20.GL_FLOAT, false, 24, buf);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount);
            }
        }

        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aColor);
    }

    private static int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] status = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(s);
            GLES20.glDeleteShader(s);
            throw new RuntimeException("Shader compile failed: " + log);
        }
        return s;
    }
}
