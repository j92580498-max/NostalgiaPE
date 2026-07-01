package com.nostalgiape.client.ui;

import android.app.Activity;
import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nostalgiape.client.entity.LocalPlayer;
import com.nostalgiape.client.net.GameSession;
import com.nostalgiape.client.render.WorldRenderer;
import com.nostalgiape.client.world.World;

/**
 * Main game screen. Hosts the GL surface, a virtual movement pad, look-drag,
 * jump/place/break buttons and a chat overlay. Runs a fixed-step client tick
 * that applies gravity/collision and streams movement to the server.
 */
public class GameActivity extends Activity implements GameSession.GameListener {

    private World world;
    private LocalPlayer player;
    private GameSession session;
    private WorldRenderer renderer;
    private GLSurfaceView glView;

    private TextView statusText;
    private TextView chatLog;
    private final StringBuilder chatBuffer = new StringBuilder();
    private final Handler ui = new Handler(Looper.getMainLooper());

    // input state
    private float moveX, moveZ;         // -1..1 from the dpad
    private boolean jumpHeld;
    private float lastLookX, lastLookY;
    private int lookPointerId = -1;

    private Thread tickThread;
    private volatile boolean ticking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String host = getIntent().getStringExtra(TitleActivity.EXTRA_HOST);
        int port = getIntent().getIntExtra(TitleActivity.EXTRA_PORT, 19132);
        String name = getIntent().getStringExtra(TitleActivity.EXTRA_NAME);
        if (name == null || name.isEmpty()) name = "Player";

        world = new World();
        player = new LocalPlayer();
        renderer = new WorldRenderer(world, player);

        FrameLayout root = new FrameLayout(this);

        glView = new GLSurfaceView(this);
        glView.setEGLContextClientVersion(2);
        glView.setRenderer(renderer);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        root.addView(glView);

        // Look layer: dragging on the right half rotates the camera.
        View lookLayer = new View(this);
        lookLayer.setOnTouchListener(this::onLookTouch);
        root.addView(lookLayer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        root.addView(buildHud());

        statusText = new TextView(this);
        statusText.setTextColor(Color.WHITE);
        statusText.setBackgroundColor(0x88000000);
        statusText.setPadding(16, 16, 16, 16);
        statusText.setText("Connecting...");
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        sp.gravity = Gravity.TOP | Gravity.START;
        root.addView(statusText, sp);

        chatLog = new TextView(this);
        chatLog.setTextColor(Color.WHITE);
        chatLog.setTextSize(12);
        chatLog.setBackgroundColor(0x55000000);
        chatLog.setPadding(16, 16, 16, 16);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                dp(260), FrameLayout.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.TOP | Gravity.START;
        cp.topMargin = dp(56);
        root.addView(chatLog, cp);

        setContentView(root);

        session = new GameSession(host, port, name, world, this);
        session.start();
        startTick();
    }

    private View buildHud() {
        FrameLayout hud = new FrameLayout(this);

        // Movement dpad (bottom-left)
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        Button up = padButton("^");
        LinearLayout mid = new LinearLayout(this);
        Button left = padButton("<");
        Button down = padButton("v");
        Button right = padButton(">");
        mid.addView(left); mid.addView(down); mid.addView(right);
        pad.addView(up);
        pad.addView(mid);

        up.setOnTouchListener(dirTouch(0, 1));
        down.setOnTouchListener(dirTouch(0, -1));
        left.setOnTouchListener(dirTouch(-1, 0));
        right.setOnTouchListener(dirTouch(1, 0));

        FrameLayout.LayoutParams padLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        padLp.gravity = Gravity.BOTTOM | Gravity.START;
        padLp.setMargins(dp(16), 0, 0, dp(16));
        hud.addView(pad, padLp);

        // Action buttons (bottom-right)
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        Button jump = padButton("JUMP");
        jump.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) jumpHeld = true;
            else if (e.getAction() == MotionEvent.ACTION_UP) jumpHeld = false;
            return true;
        });
        Button breakBtn = padButton("BREAK");
        breakBtn.setOnClickListener(v -> breakBlock());
        Button placeBtn = padButton("PLACE");
        placeBtn.setOnClickListener(v -> placeBlock());
        Button chatBtn = padButton("CHAT");
        chatBtn.setOnClickListener(v -> openChatInput());
        actions.addView(jump);
        actions.addView(breakBtn);
        actions.addView(placeBtn);
        actions.addView(chatBtn);

        FrameLayout.LayoutParams aLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        aLp.gravity = Gravity.BOTTOM | Gravity.END;
        aLp.setMargins(0, 0, dp(16), dp(16));
        hud.addView(actions, aLp);

        return hud;
    }

    private Button padButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(0x66000000);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(64), dp(48));
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        b.setLayoutParams(lp);
        return b;
    }

    private View.OnTouchListener dirTouch(final int dx, final int dz) {
        return (v, e) -> {
            int a = e.getAction();
            if (a == MotionEvent.ACTION_DOWN) { moveX = dx; moveZ = dz; }
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) { moveX = 0; moveZ = 0; }
            return true;
        };
    }

    private boolean onLookTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lookPointerId = e.getPointerId(0);
                lastLookX = e.getX();
                lastLookY = e.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                int idx = e.findPointerIndex(lookPointerId);
                if (idx < 0) return true;
                float nx = e.getX(idx), ny = e.getY(idx);
                float dxp = nx - lastLookX, dyp = ny - lastLookY;
                lastLookX = nx; lastLookY = ny;
                player.yaw += dxp * 0.3f;
                player.pitch -= dyp * 0.3f;
                if (player.pitch > 89) player.pitch = 89;
                if (player.pitch < -89) player.pitch = -89;
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                lookPointerId = -1;
                return true;
        }
        return false;
    }

    // ---- Client tick ----

    private void startTick() {
        ticking = true;
        tickThread = new Thread(() -> {
            long last = System.nanoTime();
            long moveTimer = 0;
            while (ticking) {
                long now = System.nanoTime();
                float dt = (now - last) / 1e9f;
                last = now;
                if (dt > 0.1f) dt = 0.1f;

                player.applyInput(moveX, moveZ, jumpHeld, dt, world);
                player.tickPhysics(dt, world);

                moveTimer += (now - last);
                // stream movement ~20/s
                if (session != null && session.isSpawned()) {
                    session.sendMove(player.x, player.y, player.z, player.yaw, player.pitch);
                }

                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            }
        }, "client-tick");
        tickThread.setDaemon(true);
        tickThread.start();
    }

    private void breakBlock() {
        int[] hit = player.raycast(world, 6f, false);
        if (hit != null) {
            world.setBlock(hit[0], hit[1], hit[2], 0);
            if (session != null) session.sendRemoveBlock(hit[0], hit[1], hit[2]);
        }
    }

    private void placeBlock() {
        int[] hit = player.raycast(world, 6f, true);
        if (hit != null) {
            world.setBlock(hit[0], hit[1], hit[2], 1); // place stone
            if (session != null) session.sendUseItem(hit[0], hit[1], hit[2], hit[3], 1, 0);
        }
    }

    private void openChatInput() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Chat")
                .setView(input)
                .setPositiveButton("Send", (d, w) -> {
                    String msg = input.getText().toString();
                    if (!msg.isEmpty() && session != null) session.sendChat(msg);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- GameSession.GameListener ----

    @Override
    public void onStatus(String message) {
        ui.post(() -> statusText.setText(message));
    }

    @Override
    public void onLoginSuccess(long entityId, float x, float y, float z) {
        player.x = x; player.y = y; player.z = z;
        ui.post(() -> statusText.setText("Spawned at " + (int) x + "," + (int) y + "," + (int) z));
    }

    @Override
    public void onSpawn() {
        ui.post(() -> statusText.setText("In game"));
    }

    @Override
    public void onChatMessage(String source, String message) {
        String line = (source == null || source.isEmpty()) ? message : "<" + source + "> " + message;
        ui.post(() -> {
            chatBuffer.append(line).append("\n");
            String[] lines = chatBuffer.toString().split("\n");
            int start = Math.max(0, lines.length - 8);
            StringBuilder shown = new StringBuilder();
            for (int i = start; i < lines.length; i++) shown.append(lines[i]).append("\n");
            chatLog.setText(shown.toString());
        });
    }

    @Override
    public void onDisconnected(String reason) {
        ui.post(() -> statusText.setText("Disconnected: " + reason));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ticking = false;
        if (session != null) session.stop();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
