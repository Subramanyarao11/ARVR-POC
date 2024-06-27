package com.example.draw;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.ar.core.Session;
import com.google.ar.sceneform.Scene;

public class ManualMode extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_mode);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        try {
            Session arSession = ArSessionManager.getInstance().getArSession();
            Scene arScene = ArSessionManager.getInstance().getArScene();

            if (arSession == null || arScene == null) {
                Log.e(TAG, "Session or Scene is null");
                finish();
                return;
            }

            // Continue setup...
        } catch (Exception e) {
            Log.e(TAG, "Error accessing AR session: " + e.getMessage());
            finish(); // Consider handling more gracefully
        }
        SimpleInstructionOverlay simpleInstructionOverlay = findViewById(R.id.instructionOverlay);
        simpleInstructionOverlay.setText("You can switch between auto and manual capturing mode");
        TextView progressText = findViewById(R.id.progressText);
        progressText.setVisibility(View.GONE);
        simpleInstructionOverlay.setVisibility(View.VISIBLE);
        ImageButton closeButton = findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> {
            finish();
        });
        new Handler().postDelayed(() -> {
            simpleInstructionOverlay.setVisibility(View.GONE);
            progressText.setVisibility(View.VISIBLE);
        }, 3000);
    }
}