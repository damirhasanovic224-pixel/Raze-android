package com.razemobile;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Start menu. SWF files are picked from the phone and copied into app storage (never bundled in the APK). */
public class MainActivity extends Activity {
    private static final int PICK_SWF = 1;
    private int pendingSlot = 2;
    private static final String[] RENDERERS = {"auto", "webgl", "wgpu-webgl", "canvas"};
    private static final int[] AGI_DELAYS = {0, 15, 30, 45};
    private Button rendererBtn;
    private Button agiBtn;
    private Button resetBtn;
    private SharedPreferences prefs;

    private String renderer() { return prefs.getString("renderer", "auto"); }
    private int agiDelay() { return prefs.getInt("agiDelay", 30); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("raze", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(64, 48, 64, 48);
        root.setBackgroundColor(0xFF161616);

        root.addView(makeButton(2));
        root.addView(makeButton(3));

        rendererBtn = new Button(this);
        rendererBtn.setText("Graphics: " + renderer());
        rendererBtn.setOnClickListener(v -> {
            int i = java.util.Arrays.asList(RENDERERS).indexOf(renderer());
            String next = RENDERERS[(i + 1) % RENDERERS.length];
            prefs.edit().putString("renderer", next).apply();
            rendererBtn.setText("Graphics: " + next);
        });
        root.addView(rendererBtn);

        agiBtn = new Button(this);
        agiBtn.setText("Raze 2 login delay: " + agiDelay() + "s");
        agiBtn.setOnClickListener(v -> {
            int cur = agiDelay(), next = AGI_DELAYS[0];
            for (int i = 0; i < AGI_DELAYS.length; i++) {
                if (AGI_DELAYS[i] == cur) { next = AGI_DELAYS[(i + 1) % AGI_DELAYS.length]; break; }
            }
            prefs.edit().putInt("agiDelay", next).apply();
            agiBtn.setText("Raze 2 login delay: " + next + "s");
        });
        root.addView(agiBtn);

        resetBtn = new Button(this);
        resetBtn.setText("Reset Raze 2 save (next launch)");
        resetBtn.setOnClickListener(v -> {
            prefs.edit().putBoolean("resetRaze2", true).apply();
            Toast.makeText(this, "Raze 2 save will be cleared next time you open Raze 2", Toast.LENGTH_LONG).show();
        });
        root.addView(resetBtn);

        TextView hint = new TextView(this);
        hint.setText("Tap to play. On first run you pick the SWF file.\nLong-press to choose a different file.");
        hint.setTextColor(0xFFA0A0A0);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 32, 0, 0);
        root.addView(hint);

        setContentView(root);
    }

    private Button makeButton(final int slot) {
        Button b = new Button(this);
        b.setText("Raze " + slot);
        b.setTextSize(22);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 12, 0, 12);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> {
            if (swfFile(slot).exists()) play(slot); else pick(slot);
        });
        b.setOnLongClickListener(v -> {
            pick(slot);
            return true;
        });
        return b;
    }

    private File swfFile(int slot) {
        File dir = new File(getFilesDir(), "swf");
        dir.mkdirs();
        return new File(dir, "raze" + slot + ".swf");
    }

    private void pick(int slot) {
        pendingSlot = slot;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, PICK_SWF);
    }

    private void play(int slot) {
        Intent i = new Intent(this, GameActivity.class);
        i.putExtra("slot", slot);
        i.putExtra("renderer", renderer());
        i.putExtra("agiDelay", agiDelay());
        boolean reset = slot == 2 && prefs.getBoolean("resetRaze2", false);
        if (reset) prefs.edit().putBoolean("resetRaze2", false).apply();
        i.putExtra("reset", reset);
        startActivity(i);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_SWF || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        final int slot = pendingSlot;
        final File target = swfFile(slot);
        new Thread(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                runOnUiThread(() -> play(slot));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Couldn't read that file: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
