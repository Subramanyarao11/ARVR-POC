package com.example.draw;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.ar.core.Session;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.ux.ArFragment;

public class ManualMode extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_mode);
        Intent intent = getIntent();
        recreateRectangleNode(intent);
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

    private void recreateRectangleNode(Intent intent) {
        float width = intent.getFloatExtra("width", 0);
        float height = intent.getFloatExtra("height", 0);
        float depth = intent.getFloatExtra("depth", 0);

        Vector3 pt1 = new Vector3(intent.getFloatExtra("pt1X", 0), intent.getFloatExtra("pt1Y", 0), intent.getFloatExtra("pt1Z", 0));
        Vector3 pt2 = new Vector3(intent.getFloatExtra("pt2X", 0), intent.getFloatExtra("pt2Y", 0), intent.getFloatExtra("pt2Z", 0));
        Vector3 pt3 = new Vector3(intent.getFloatExtra("pt3X", 0), intent.getFloatExtra("pt3Y", 0), intent.getFloatExtra("pt3Z", 0));

        Vector3 edgeVector = Vector3.subtract(pt2, pt1);
        Vector3 edgeDirection = edgeVector.normalized();
        Vector3 fromPt1ToPt3 = Vector3.subtract(pt3, pt1);
        float projectionLength = Vector3.dot(fromPt1ToPt3, edgeDirection);
        Vector3 projection = edgeDirection.scaled(projectionLength);
        Vector3 projectionPoint = Vector3.add(pt1, projection);
        Vector3 perpendicularVector = Vector3.subtract(pt3, projectionPoint);
        Vector3 crossProduct = Vector3.cross(edgeDirection, Vector3.up());
        float computedDepth = perpendicularVector.length() * (Vector3.dot(perpendicularVector, crossProduct) < 0 ? -1 : 1);
        Vector3 baseCenter = Vector3.add(pt1, pt2).scaled(0.5f);
        Vector3 depthOffset = crossProduct.normalized().scaled(computedDepth / 2.0f);
        Vector3 rectangleCenter = Vector3.add(baseCenter, depthOffset);

        Quaternion rotation = Quaternion.lookRotation(edgeDirection, Vector3.up());

        MaterialFactory.makeTransparentWithColor(this, new Color(android.graphics.Color.argb(128, 0, 0, 255)))
                .thenAccept(material -> {
                    ModelRenderable rectangle = ShapeFactory.makeCube(new Vector3(width, height, depth), Vector3.zero(), material);
                    Node rectangleNode = new Node();
                    rectangleNode.setRenderable(rectangle);
                    rectangleNode.setWorldPosition(rectangleCenter);
                    rectangleNode.setWorldRotation(rotation);

                    ArFragment arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arFragment);
                    assert arFragment != null;
                    arFragment.getArSceneView().getScene().addChild(rectangleNode);
                });
    }

}