package com.example.draw;


import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;
import com.google.ar.core.Anchor;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Camera;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.ux.TransformableNode;
import java.util.ArrayList;
import java.util.List;
import com.google.ar.sceneform.ArSceneView;
public class MainActivity extends AppCompatActivity {
    static class ConnectingLine {
        private final Node lineNode;
        private final TransformableNode node1, node2;

        private Material lineMaterial;

        public void update(Context context) {
            updateLine();
        }


        public ConnectingLine(TransformableNode node1, TransformableNode node2, Context context) {
            this.node1 = node1;
            this.node2 = node2;
            lineNode = new Node();
            lineNode.setParent(node1.getScene());

            // Asynchronously create the material
            MaterialFactory.makeOpaqueWithColor(context, new Color(android.graphics.Color.BLUE))
                    .thenAccept(material -> {
                        lineMaterial = material;
                        updateLine();
                    });

            // Add update listeners to the TransformableNodes
            node1.addTransformChangedListener(this::onTransformChanged);
            node2.addTransformChangedListener(this::onTransformChanged);
        }

        private void onTransformChanged(Node node, Node node1) {
            updateLine();
        }

        private void updateLine() {
            if (lineMaterial == null) {
                Log.d(TAG, "Material not ready, skipping line update.");

                return;
            }

            Vector3 point1 = node1.getWorldPosition();
            Vector3 point2 = node2.getWorldPosition();
            Vector3 difference = Vector3.subtract(point1, point2);
            Vector3 direction = difference.normalized();
            Quaternion rotation = Quaternion.lookRotation(direction, Vector3.up());

            lineNode.setWorldPosition(Vector3.add(point1, point2).scaled(0.5f));
            lineNode.setWorldRotation(rotation);
            lineNode.setRenderable(ShapeFactory.makeCube(
                    new Vector3(0.003f, 0.003f, difference.length()),
                    Vector3.zero(),
                    lineMaterial));
        }

    }

    private WritingArFragment arFragment;

    private Button btnProceed;


    private ConnectingLine connectingLine;


    private static final String TAG = MainActivity.class.getSimpleName();
    private final List<AnchorNode> anchorNodes = new ArrayList<>();
    private List<LineRenderer> lines = new ArrayList<>();


    private ModelRenderable andyRenderable;

    private static final double MIN_OPENGL_VERSION = 3.0;


    private Node currentRectangleNode = null;

    private SeekBar seekBarHeight;
    private Button btnDecreaseHeight, btnIncreaseHeight;
    private float height = 0.1f; // Initial height value

    private float width;
    private float depth;

    private LinearLayout heightControls;
    private boolean updatePlaneDetectionOverlay = true;




    @Override
    protected void onResume() {
        super.onResume();
        if (arFragment != null && arFragment.getArSceneView() != null) {
            // Obtain the current scene and session
            Scene scene = arFragment.getArSceneView().getScene();
            Session session = arFragment.getArSceneView().getSession();

            if (scene != null && session != null) {
                // Initialize ArSessionManager
                ArSessionManager.getInstance().initialize(session, scene);

                // Add your onUpdateListener
                scene.addOnUpdateListener(frameTime -> {
                    if (session != null) {
                        for (Plane plane : session.getAllTrackables(Plane.class)) {
                            if (plane.getTrackingState() == TrackingState.TRACKING) {
                                if (updatePlaneDetectionOverlay) {
                                    updateInstructionOverlay("Plane Detected!", "Walk to the corner of the product and tap to set", R.drawable.ic_tap);
                                }
                                break;
                            }
                        }
                    }
                    if (connectingLine != null) {
                        connectingLine.update(this);
                    }
                });
            } else {
                Log.e(TAG, "Scene or Session is null, cannot initialize ArSessionManager");
            }
        } else {
            Log.e(TAG, "AR Fragment or AR Scene View is not initialized");
        }
    }




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: ");
        try
        {
            Log.d(TAG, "Calling setupArFragment()");
            this.getActionBar().hide();
        }
        catch (NullPointerException e){
            Log.d(TAG, "onCreate: " + e);
        }
        Log.d(TAG, "Checking if device is supported");
        if (!checkIsSupportedDeviceOrFinish(this)) {
            Log.d(TAG, "Device not supported");
            return;
        }
        setContentView(R.layout.activity_main);
        findViewById(R.id.instructionOverlay).setVisibility(View.VISIBLE);
        updateInstructionOverlay("Detect the floor", "Move camera until the white points marking the floor is detected.", R.drawable.ic_instruction);
        seekBarHeight = findViewById(R.id.seekBarHeight);
        btnDecreaseHeight = findViewById(R.id.btnDecreaseHeight);
        btnIncreaseHeight = findViewById(R.id.btnIncreaseHeight);
        heightControls = findViewById(R.id.heightControls);
        btnProceed = findViewById(R.id.btnProceed);
        btnProceed.setOnClickListener(this::onProceedClicked);


        ImageButton btnReset = findViewById(R.id.btnReset);
        btnReset.setOnClickListener(view -> {
            Intent restartIntent = new Intent(getApplicationContext(), MainActivity.class);
            startActivity(restartIntent);
            finish();
        });

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            Log.d(TAG, "Return button clicked");
            finish();
        });


        // Set the initial height value to the SeekBar
        seekBarHeight.setProgress((int) (height * 10));

        seekBarHeight.setMax(100);
        seekBarHeight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                height = progress / 100f;  // Smaller increment
                if (anchorNodes.size() == 3) {
                    hidePointCloud();
                    calculateRectangle();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        btnDecreaseHeight.setOnClickListener(v -> {
            height = Math.max(0.1f, height - 0.1f);
            seekBarHeight.setProgress((int) (height * 100));
            if (anchorNodes.size() == 3) {
                calculateRectangle();
            }
        });

        btnIncreaseHeight.setOnClickListener(v -> {
            height = Math.min(1f, height + 0.1f);
            seekBarHeight.setProgress((int) (height * 100));
            if (anchorNodes.size() == 3) {
                calculateRectangle();
            }
        });


        arFragment = (WritingArFragment) getSupportFragmentManager().findFragmentById(R.id.arFragment);
        assert arFragment != null;
        ArSceneView arSceneView = arFragment.getArSceneView();

        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.BLUE))
                .thenAccept(material -> {
                    ModelRenderable sphereRenderable = ShapeFactory.makeSphere(0.05f, Vector3.zero(), material);
                    andyRenderable = sphereRenderable;
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Unable to load sphere renderable", throwable);
                    Toast.makeText(MainActivity.this, "Unable to load sphere", Toast.LENGTH_SHORT).show();
                    return null;
                });


//
        arFragment.setOnTapArPlaneListener((hitResult, plane, motionEvent) -> {
            if (andyRenderable == null) {
                return;
            }

            if (anchorNodes.size() == 2 && connectingLine == null) {
                connectingLine = new ConnectingLine(
                        (TransformableNode) anchorNodes.get(0).getChildren().get(0),
                        (TransformableNode) anchorNodes.get(1).getChildren().get(0),
                        this
                );
            } else if (anchorNodes.size() == 3) {
                calculateRectangle();
            }

            if (anchorNodes.size() >= 3) {
                Toast.makeText(this, "Only 3 nodes are allowed", Toast.LENGTH_SHORT).show();
                return;
            }

            updatePlaneDetectionOverlay = false;
            Anchor anchor = hitResult.createAnchor();
            AnchorNode anchorNode = new AnchorNode(anchor);
            anchorNode.setParent(arFragment.getArSceneView().getScene());
            anchorNodes.add(anchorNode);

            TransformableNode andy = new TransformableNode(arFragment.getTransformationSystem());
            andy.setParent(anchorNode);
            andy.setRenderable(andyRenderable);
            andy.setLocalScale(new Vector3(0.5f, 0.5f, 0.5f));
            andy.select();
            andy.getScaleController().setEnabled(false);
            int anchorCount = anchorNodes.size();
            Log.d(TAG, "anchorcount" + anchorCount);


            runOnUiThread(() -> {
                switch (anchorCount) {
                    case 1:
                        updateInstructionOverlay("First Point Set", "Place the second point at another corner.", R.drawable.ic_second_anchor);
                        break;
                    case 2:
                        updateInstructionOverlay("Second Point Set", "Place the third point to complete the area.", R.drawable.ic_second_anchor);
                        break;
                    case 3:
                        updateInstructionOverlay("All Points Set", "Calculating area...", R.drawable.ic_completed);
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            updatePlaneDetectionOverlay = true;
                            findViewById(R.id.instructionOverlay).setVisibility(View.GONE);
                        }, 3000);
                        break;
                }
            });

            andy.addTransformChangedListener((node, node1) -> {
                if (anchorNodes.size() == 3) {
                    calculateRectangle();
                }
            });

        });
    }

    public void onProceedClicked(View view) {
        Intent intent = new Intent(this, ManualMode.class);
        intent.putExtra("width", width);
        intent.putExtra("height", height);
        intent.putExtra("depth", depth);
        Vector3 pt1 = anchorNodes.get(0).getWorldPosition();
        Vector3 pt2 = anchorNodes.get(1).getWorldPosition();
        Vector3 pt3 = anchorNodes.get(2).getWorldPosition();
        intent.putExtra("pt1X", pt1.x);
        intent.putExtra("pt1Y", pt1.y);
        intent.putExtra("pt1Z", pt1.z);
        intent.putExtra("pt2X", pt2.x);
        intent.putExtra("pt2Y", pt2.y);
        intent.putExtra("pt2Z", pt2.z);
        intent.putExtra("pt3X", pt3.x);
        intent.putExtra("pt3Y", pt3.y);
        intent.putExtra("pt3Z", pt3.z);
        startActivity(intent);
    }



    @Override
    protected void onPause() {
        super.onPause();

    }

    private void calculateRectangle() {
        if (anchorNodes.size() < 3) {
            Log.d(TAG, "Not enough anchor nodes to calculate rectangle");
            return;
        }

        Vector3 pt1 = anchorNodes.get(0).getWorldPosition();
        Vector3 pt2 = anchorNodes.get(1).getWorldPosition();
        Vector3 pt3 = anchorNodes.get(2).getWorldPosition();

        // Calculate the edge vector from pt1 to pt2
        Vector3 edgeVector = Vector3.subtract(pt2, pt1);
        this.width = edgeVector.length();
        Vector3 edgeDirection = edgeVector.normalized();

        // Vector from pt1 to pt3
        Vector3 fromPt1ToPt3 = Vector3.subtract(pt3, pt1);

        // Calculate the projection of fromPt1ToPt3 onto edgeVector
        float projectionLength = Vector3.dot(fromPt1ToPt3, edgeDirection);
        Vector3 projection = edgeDirection.scaled(projectionLength);

        // Calculate the perpendicular vector from the projection point on the edge to pt3
        Vector3 projectionPoint = Vector3.add(pt1, projection);
        Vector3 perpendicularVector = Vector3.subtract(pt3, projectionPoint);
        this.depth = perpendicularVector.length();

        // Determine the direction of the depth to ensure it extends perpendicular to the edge
        Vector3 crossProduct = Vector3.cross(edgeDirection, Vector3.up());
        if (Vector3.dot(perpendicularVector, crossProduct) < 0) {
            this.depth = -this.depth;
        }

        // Calculate the center of the rectangle
        Vector3 baseCenter = Vector3.add(pt1, pt2).scaled(0.5f);
        Vector3 depthOffset = crossProduct.normalized().scaled(depth / 2.0f);
        Vector3 rectangleCenter = Vector3.add(baseCenter, depthOffset);

        Quaternion rotation = Quaternion.lookRotation(edgeDirection, Vector3.up());
//        float height = 0.1f;
        updateRectangle(rectangleCenter, width, height, Math.abs(depth), rotation);
        showControls();
        btnProceed.setVisibility(View.VISIBLE);
    }

    private void showControls() {
        runOnUiThread(() -> {
            if (!heightControls.isShown()) {
                heightControls.setVisibility(View.VISIBLE);
                heightControls.setAlpha(0f);
                heightControls.animate().alpha(1.0f).setDuration(200);
                heightControls.requestLayout();
            }
        });
    }

    private void updateRectangle(Vector3 center, float width, float height, float depth, Quaternion rotation) {
        Vector3 dimensions = new Vector3(width, height, depth);  // Create a new Vector3 for dimensions

        MaterialFactory.makeTransparentWithColor(this, new Color(android.graphics.Color.argb(128, 0, 0, 255)))
                .thenAccept(material -> {
                    ModelRenderable rectangle = ShapeFactory.makeCube(dimensions, Vector3.zero(), material);

                    if (currentRectangleNode != null) {
                        currentRectangleNode.setParent(null); // Remove the old rectangle
                    }

                    currentRectangleNode = new Node();
                    currentRectangleNode.setRenderable(rectangle);

                    // Adjust the center vertically so the bottom remains fixed
                    float verticalOffset = height / 2.0f; // Adjust center to be half height above the base
                    Vector3 adjustedCenter = new Vector3(center.x, center.y + verticalOffset, center.z);

                    currentRectangleNode.setWorldPosition(adjustedCenter);
                    currentRectangleNode.setWorldRotation(rotation);

                    arFragment.getArSceneView().getScene().addChild(currentRectangleNode);
                });
    }


    private boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        Log.d(TAG, "Inside checkIsSupportedDeviceOrFinish()");
        String openGlVersionString =
                ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();
        Log.d(TAG, "OpenGL ES Version: " + openGlVersionString);
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show();
            activity.finish();
            return false;
        }
        return true;
    }

    private void hidePointCloud() {
        arFragment.getArSceneView().getPlaneRenderer().setEnabled(false);
    }

    private void updateInstructionOverlay(String heading, String text, int iconResId) {
        Log.d(TAG, "Updating overlay to: " + heading + " / " + text);
        InstructionOverlay instructionOverlay = findViewById(R.id.instructionOverlay);
        if (instructionOverlay != null) {
            instructionOverlay.setHeadingText(heading);
            instructionOverlay.setText(text);
            instructionOverlay.setIcon(getResources().getDrawable(iconResId, getTheme()));
            instructionOverlay.invalidate();
        } else {
            Log.e(TAG, "InstructionOverlay is null");
        }
    }
}