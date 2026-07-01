package com.nostalgiape.client.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Simple title screen: pick a username, server host and port, then join.
 * Defaults target the classic server nostalgiape.ddns.net:19132.
 */
public class TitleActivity extends Activity {

    public static final String EXTRA_HOST = "host";
    public static final String EXTRA_PORT = "port";
    public static final String EXTRA_NAME = "name";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(30, 30, 40));
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("NostalgiaPE");
        title.setTextColor(Color.WHITE);
        title.setTextSize(34);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Classic Minecraft PE Alpha 0.8.1 client");
        sub.setTextColor(Color.rgb(180, 180, 190));
        sub.setTextSize(14);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, dp(24));
        root.addView(sub);

        final EditText name = new EditText(this);
        name.setHint("Username");
        name.setText("Player" + (100 + (int) (Math.random() * 900)));
        name.setTextColor(Color.WHITE);
        name.setInputType(InputType.TYPE_CLASS_TEXT);
        root.addView(name);

        final EditText host = new EditText(this);
        host.setHint("Server address");
        host.setText("nostalgiape.ddns.net");
        host.setTextColor(Color.WHITE);
        host.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(host);

        final EditText port = new EditText(this);
        port.setHint("Port");
        port.setText("19132");
        port.setTextColor(Color.WHITE);
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(port);

        Button join = new Button(this);
        join.setText("Join Server");
        join.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(TitleActivity.this, GameActivity.class);
                i.putExtra(EXTRA_NAME, name.getText().toString().trim());
                i.putExtra(EXTRA_HOST, host.getText().toString().trim());
                int p = 19132;
                try { p = Integer.parseInt(port.getText().toString().trim()); } catch (Exception ignored) {}
                i.putExtra(EXTRA_PORT, p);
                startActivity(i);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(24);
        root.addView(join, lp);

        setContentView(root);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
