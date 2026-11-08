package com.lemonrsh.fodcustomizer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_FILE = 701;
    private static final int BG = Color.rgb(12, 18, 21);
    private static final int CARD = Color.rgb(22, 31, 34);
    private static final int BORDER = Color.rgb(49, 63, 64);
    private static final int TEXT = Color.rgb(239, 247, 243);
    private static final int MUTED = Color.rgb(160, 180, 171);
    private static final int MINT = Color.rgb(129, 232, 181);
    private static final int INK = Color.rgb(13, 41, 29);

    private final EnumMap<Slot, TextView> statuses = new EnumMap<>(Slot.class);
    private final List<Button> actionButtons = new ArrayList<>();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private OverrideRepository repository;
    private Slot pendingSlot;
    private boolean importing;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new OverrideRepository(this);
        if (state != null) {
            String name = state.getString("pending_slot");
            if (name != null) {
                try { pendingSlot = Slot.valueOf(name); } catch (IllegalArgumentException ignored) { }
            }
        }
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setContentView(createScreen());
        refreshStatuses();
    }

    @Override protected void onResume() {
        super.onResume();
        if (repository != null) refreshStatuses();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (pendingSlot != null) state.putString("pending_slot", pendingSlot.name());
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        ioExecutor.shutdown();
        super.onDestroy();
    }

    private View createScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        // Android 15 enforces edge-to-edge for target SDK 35.
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(28), dp(22), dp(40));
        scroll.addView(content);

        TextView eyebrow = text("SYSTEMUI  /  LSPOSED", 11, MINT, true);
        eyebrow.setLetterSpacing(0.13f);
        content.addView(eyebrow);
        add(content, text("Make it yours.", 32, TEXT, true), 12);
        TextView intro = text("Choose your own fingerprint icon and animations. Nothing is changed until you add a file.", 15, MUTED, false);
        intro.setLineSpacing(dp(4), 1f);
        add(content, intro, 8);

        LinearLayout steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.VERTICAL);
        steps.setPadding(dp(16), dp(15), dp(16), dp(15));
        steps.setBackground(rounded(Color.rgb(23, 43, 34), Color.rgb(53, 91, 68), 16));
        TextView stepsTitle = text("GET STARTED", 11, MINT, true);
        stepsTitle.setLetterSpacing(0.1f);
        steps.addView(stepsTitle);
        TextView instructions = text("1  Choose your PNG or complete Lottie JSON below.\n2  Enable this module in LSPosed and scope SystemUI.\n3  Reboot after enabling or changing a file.", 13, TEXT, false);
        instructions.setLineSpacing(dp(6), 1f);
        add(steps, instructions, 9);
        add(content, steps, 24);

        TextView heading = text("YOUR FILES", 11, MUTED, true);
        heading.setLetterSpacing(0.1f);
        add(content, heading, 27);
        for (Slot slot : Slot.values()) add(content, makeSlotCard(slot), 12);

        TextView noteTitle = text("A note about your device", 16, TEXT, true);
        add(content, noteTitle, 27);
        TextView note = text("Lock-screen and always-on FOD slots target common AOSP under-display resource names; your ROM may use different ones. SFPS usually refers to a side sensor. Keep a way to disable LSPosed if SystemUI fails.", 13, MUTED, false);
        note.setLineSpacing(dp(4), 1f);
        add(content, note, 8);
        return scroll;
    }

    private View makeSlotCard(Slot slot) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(15));
        card.setBackground(rounded(CARD, BORDER, 16));
        card.addView(text(slot.label, 17, TEXT, true));

        TextView description = text(slot.description, 11, MUTED, false);
        description.setTypeface(Typeface.MONOSPACE);
        add(card, description, 5);
        TextView status = text("SystemUI default", 13, MUTED, false);
        statuses.put(slot, status);
        add(card, status, 12);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button choose = button(slot.kind == Slot.Kind.PNG ? "Choose PNG" : "Choose JSON", true);
        choose.setOnClickListener(v -> chooseFile(slot));
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        actions.addView(choose, chooseParams);
        Button clear = button("Clear", false);
        clear.setOnClickListener(v -> clearFile(slot));
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(dp(86), dp(44));
        clearParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(clear, clearParams);
        add(card, actions, 14);
        return card;
    }

    private void chooseFile(Slot slot) {
        if (importing) return;
        pendingSlot = slot;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Some file managers label JSON as text/plain or octet-stream, so validate after selection.
        intent.setType("*/*");
        try {
            startActivityForResult(intent, PICK_FILE);
        } catch (ActivityNotFoundException e) {
            pendingSlot = null;
            showError("No file picker found", "Install or enable an Android document picker.");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FILE) return;
        Slot slot = pendingSlot;
        pendingSlot = null;
        if (resultCode != RESULT_OK || slot == null || data == null || data.getData() == null) return;
        importFile(slot, data.getData());
    }

    private void importFile(Slot slot, Uri uri) {
        if (importing) return;
        setImporting(true);
        ioExecutor.execute(() -> {
            try {
                String name = originalName(uri);
                byte[] data;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    data = BoundedInput.readFully(input, BoundedInput.MAX_BYTES);
                }
                String details;
                if (slot.kind == Slot.Kind.LOTTIE) {
                    LottieValidator.Info info = LottieValidator.validate(data);
                    details = String.format(Locale.US, "%d × %d · %.1f fps · %.2f sec%s",
                            info.width, info.height, info.fps, info.durationSeconds(),
                            info.layers == 0 ? " · 0 layers (blank)" : "");
                } else {
                    PngValidator.validate(data);
                    details = "PNG verified";
                }
                repository.save(slot, data, name);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setImporting(false);
                    refreshStatuses();
                    new AlertDialog.Builder(this)
                            .setTitle("File saved")
                            .setMessage(details + "\n\nReboot to apply it in SystemUI."
                                    + (slot.kind == Slot.Kind.LOTTIE ? " JSON is checked for basic Lottie structure, not render compatibility." : ""))
                            .setPositiveButton("OK", null)
                            .show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setImporting(false);
                    String message = error.getMessage();
                    if (message == null || message.trim().isEmpty()) message = "Could not read the selected file.";
                    if (message.length() > 220) message = message.substring(0, 220);
                    showError("File not imported", message + "\n\nThe previous selection was kept.");
                });
            }
        });
    }

    private void clearFile(Slot slot) {
        if (importing) return;
        try {
            repository.clear(slot);
            refreshStatuses();
            Toast.makeText(this, "Removed. Reboot to restore the SystemUI default.", Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            showError("Could not remove file", "Please try again.");
        }
    }

    private String originalName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0 && cursor.getString(column) != null) {
                    return cursor.getString(column).replaceAll("[\\p{Cntrl}]", " ");
                }
            }
        } catch (RuntimeException ignored) { }
        return "Custom file";
    }

    private void refreshStatuses() {
        for (Slot slot : Slot.values()) {
            TextView status = statuses.get(slot);
            if (status == null) continue;
            if (repository.hasOverride(slot)) {
                status.setText("Custom · " + repository.displayName(slot));
                status.setTextColor(MINT);
            } else {
                status.setText("SystemUI default · no file selected");
                status.setTextColor(MUTED);
            }
        }
    }

    private void setImporting(boolean value) {
        importing = value;
        for (Button button : actionButtons) button.setEnabled(!value);
        if (value) Toast.makeText(this, "Checking your file…", Toast.LENGTH_SHORT).show();
    }

    private void showError(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return text;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(primary ? INK : TEXT);
        button.setBackground(rounded(primary ? MINT : Color.rgb(34, 46, 47), primary ? MINT : BORDER, 10));
        button.setPadding(dp(8), 0, dp(8), 0);
        actionButtons.add(button);
        return button;
    }

    private GradientDrawable rounded(int color, int border, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radiusDp));
        background.setStroke(dp(1), border);
        return background;
    }

    private void add(LinearLayout parent, View child, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topDp);
        parent.addView(child, params);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
