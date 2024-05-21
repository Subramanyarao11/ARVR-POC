package com.example.draw;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.PixelCopy;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Camera;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import com.example.draw.LineRenderer;

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
            MaterialFactory.makeOpaqueWithColor(context, new Color(android.graphics.Color.RED))
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

                return; // Return if the material is not ready
            }

            Vector3 point1 = node1.getWorldPosition();
            Vector3 point2 = node2.getWorldPosition();
            Vector3 difference = Vector3.subtract(point1, point2);
            Vector3 direction = difference.normalized();
            Quaternion rotation = Quaternion.lookRotation(direction, Vector3.up());

            lineNode.setWorldPosition(Vector3.add(point1, point2).scaled(0.5f));
            lineNode.setWorldRotation(rotation);
            lineNode.setRenderable(ShapeFactory.makeCube(
                    new Vector3(.01f, .01f, difference.length()),
                    Vector3.zero(),
                    lineMaterial));
        }


        public void hideLine() {
            lineNode.setEnabled(false);
        }

        public void showLine() {
            lineNode.setEnabled(true);
        }
    }

    private ArFragment arFragment;


    private ConnectingLine connectingLine;


    private static final String TAG = MainActivity.class.getSimpleName();
    private final List<AnchorNode> anchorNodes = new ArrayList<>();
    private List<LineRenderer> lines = new ArrayList<>();


    private ModelRenderable andyRenderable;

    private static final double MIN_OPENGL_VERSION = 3.0;

    private Button captureButton;

    private Node currentRectangleNode = null;

    private SeekBar seekBarHeight;
    private Button btnDecreaseHeight, btnIncreaseHeight;
    private float height = 0.1f; // Initial height value

    private float width;
    private float depth;

    private LinearLayout heightControls;

    private Button startRecordingButton, stopRecordingButton;

    private VideoRecorder videoRecorder;
    private boolean isRecording = false;
    private String videoFilePath;

    private static final int REQUEST_WRITE_STORAGE_PERMISSION = 1;
    private static final int REQUEST_CAMERA_PERMISSION = 2;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 3;

    private Button returnButton;


    @Override
    protected void onResume() {
        super.onResume();
        arFragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
            if (connectingLine != null) {
                connectingLine.update(this);
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: ");

        Log.d(TAG, "Checking if device is supported");
        if (!checkIsSupportedDeviceOrFinish(this)) {
            Log.d(TAG, "Device not supported");
            return;
        }
        try {
            Log.d(TAG, "Calling setupArFragment()");
            this.getActionBar().hide();
        } catch (NullPointerException e) {
            Log.d(TAG, "onCreate: " + e);
        }

        setContentView(R.layout.activity_main);

        seekBarHeight = findViewById(R.id.seekBarHeight);
        btnDecreaseHeight = findViewById(R.id.btnDecreaseHeight);
        btnIncreaseHeight = findViewById(R.id.btnIncreaseHeight);
        heightControls = findViewById(R.id.heightControls);

        startRecordingButton = findViewById(R.id.startRecordingButton);
        stopRecordingButton = findViewById(R.id.stopRecordingButton);

        startRecordingButton.setOnClickListener(v -> startRecording());
        stopRecordingButton.setOnClickListener(v -> stopRecording());

        // Set the initial height value to the SeekBar
        seekBarHeight.setProgress((int) (height * 10));

        seekBarHeight.setMax(100);
        seekBarHeight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                height = progress / 100f;  // Smaller increment
                if (anchorNodes.size() == 3) {
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


        captureButton = findViewById(R.id.captureButton);
        captureButton.setOnClickListener(v -> {
            Log.d(TAG, "Capture button clicked");
            capturePhoto();
        });

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arFragment);
        assert arFragment != null;
        ArSceneView arSceneView = arFragment.getArSceneView();

        ModelRenderable.builder()
                .setSource(this, R.raw.cube)
//                    .setIsFilamentGltf(true)
                .build()
                .thenAccept(renderable -> andyRenderable = renderable)
                .exceptionally(throwable -> {
                    Log.e(TAG, "Unable to load ModelRenderable", throwable);
                    Toast.makeText(MainActivity.this, "Unable to load model", Toast.LENGTH_SHORT).show();
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

            Anchor anchor = hitResult.createAnchor();
            AnchorNode anchorNode = new AnchorNode(anchor);
            anchorNode.setParent(arFragment.getArSceneView().getScene());
            anchorNodes.add(anchorNode);

            TransformableNode andy = new TransformableNode(arFragment.getTransformationSystem());
            andy.setParent(anchorNode);
            andy.setRenderable(andyRenderable);
            // change this vector to alter size of the model
            // For pole
//            andy.setLocalScale(new Vector3(0.3f, 0.05f, 0.3f));
            // for cube
            andy.setLocalScale(new Vector3(0.05f, 0.05f, 0.05f));
            andy.select();
            andy.getScaleController().setEnabled(false);

            andy.addTransformChangedListener((node, node1) -> {
                if (anchorNodes.size() == 3) {
                    calculateRectangle();
                }
            });

        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> permissionsNeeded = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.CAMERA);
            }
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
            }
            if (!permissionsNeeded.isEmpty()) {
                requestPermissions(permissionsNeeded.toArray(new String[0]), REQUEST_WRITE_STORAGE_PERMISSION);
            }
        }

        videoRecorder = new VideoRecorder();
        int orientation = getResources().getConfiguration().orientation;
        videoRecorder.setVideoQuality(CamcorderProfile.QUALITY_720P, orientation);
        videoRecorder.setSceneView(arFragment.getArSceneView());


        returnButton = findViewById(R.id.returnButton);
        returnButton.setOnClickListener(v -> restoreInitialUI());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_STORAGE_PERMISSION) {
            boolean allPermissionsGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (!allPermissionsGranted) {
                Toast.makeText(this, "All permissions are required to proceed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isRecording) {
            stopRecording();
        }
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
        showCaptureButton();

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

    private Point worldToScreenPoint(Vector3 worldPoint) {
        Camera camera = arFragment.getArSceneView().getScene().getCamera();
        Log.d(TAG, "camera" + camera);
        Vector3 screenPoint = camera.worldToScreenPoint(worldPoint);
        Log.d(TAG, "screenPoint" + screenPoint);
        return new Point((int) screenPoint.x, (int) screenPoint.y);
    }


    private void capturePhoto() {
        hideARFeatures();
        disableCaptureButton();
        returnButton.setVisibility(View.VISIBLE);
        // Delay the capture to ensure visibility changes have time to take effect
        new Handler(Looper.getMainLooper()).postDelayed(this::captureAndCropWithBoundsCalculation, 100);
    }
    private void disableCaptureButton() {
        captureButton.setEnabled(false);
    }

    private void enableCaptureButton() {
        captureButton.setEnabled(true);
    }


    private Rect computeBoundingBox() {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;

        // Assume currentRectangleNode is the node containing the rectangle
        if (currentRectangleNode != null) {
            Vector3[] corners = new Vector3[]{
                    Vector3.add(currentRectangleNode.getWorldPosition(), new Vector3(-width / 2, -height / 2, -depth / 2)),
                    Vector3.add(currentRectangleNode.getWorldPosition(), new Vector3(width / 2, -height / 2, -depth / 2)),
                    Vector3.add(currentRectangleNode.getWorldPosition(), new Vector3(width / 2, height / 2, -depth / 2)),
                    Vector3.add(currentRectangleNode.getWorldPosition(), new Vector3(-width / 2, height / 2, -depth / 2)),
                    Vector3.add(currentRectangleNode.getWorldPosition(), new Vector3(-width / 2, -height / 2, depth / 2)),
                    Vector3.add(currentRectangleNode.getWorldPosition(), new Vector3(width / 2, -height / 2, depth / 2)),
                    Vector3.add(currentRectangleNode.getWorldPosition(), new Vector3(width / 2, height / 2, depth / 2)),
                    Vector3.add(currentRectangleNode.getWorldPosition(), new Vector3(-width / 2, height / 2, depth / 2))
            };

            for (Vector3 corner : corners) {
                Point screenPoint = worldToScreenPoint(corner);
                minX = Math.min(minX, screenPoint.x);
                minY = Math.min(minY, screenPoint.y);
                maxX = Math.max(maxX, screenPoint.x);
                maxY = Math.max(maxY, screenPoint.y);
            }
        }

        return new Rect(minX, minY, maxX, maxY);
    }


    private void captureAndCropWithBoundsCalculation() {
        Rect boundingBox = computeBoundingBox();

        // Ensure the coordinates are within the screen bounds
        int minX = Math.max(boundingBox.left, 0);
        int minY = Math.max(boundingBox.top, 0);
        int width = Math.min(boundingBox.width(), arFragment.getArSceneView().getWidth() - minX);
        int height = Math.min(boundingBox.height(), arFragment.getArSceneView().getHeight() - minY);

        captureAndCrop(minX, minY, width, height);
    }


    private void captureAndCrop(int x, int y, int width, int height) {
        ArSceneView view = arFragment.getArSceneView();
        final Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);

        PixelCopy.request(view, bitmap, (copyResult) -> {
            restoreARFeatures();
            if (copyResult == PixelCopy.SUCCESS) {
                // Crop the bitmap
                Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, x, y, width, height);
                saveBitmapToFile(croppedBitmap); // Implement this method to save the cropped bitmap
            } else {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to capture photo", Toast.LENGTH_SHORT).show());
            }
            enableCaptureButton();
        }, new Handler(Looper.getMainLooper()));
    }

    private void saveBitmapToFile(Bitmap bitmap) {
        // Saving in PNG
        String displayName = "ARScene_" + System.currentTimeMillis() + ".png";
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

        // Insert the metadata to the MediaStore and get the Uri
        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri != null) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                Log.d(TAG, "Photo saved to " + uri.toString());
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Photo saved to " + uri.toString(), Toast.LENGTH_LONG).show());
            } catch (IOException e) {
                Log.e(TAG, "Unable to save image to file.", e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to save photo", Toast.LENGTH_SHORT).show());
            }
        }
    }


    private void hideARFeatures() {
        arFragment.getArSceneView().getPlaneRenderer().setVisible(false);
        for (AnchorNode anchorNode : anchorNodes) {
            anchorNode.setEnabled(false);
        }
        if (connectingLine != null) {
            connectingLine.hideLine();
        }
        hideRectangle();
        startRecordingButton.setVisibility(View.GONE);
    }


    private void restoreARFeatures() {
        runOnUiThread(() -> {
            arFragment.getArSceneView().getPlaneRenderer().setVisible(true);
            for (AnchorNode anchorNode : anchorNodes) {
                anchorNode.setEnabled(true);
            }
            if (connectingLine != null) {
                connectingLine.showLine();
            }
            showRectangle();
        });
    }


    private void hideRectangle() {
        if (currentRectangleNode != null) {
            runOnUiThread(() -> currentRectangleNode.setEnabled(false));
        }
    }

    private void showRectangle() {
        if (currentRectangleNode != null) {
            runOnUiThread(() -> currentRectangleNode.setEnabled(true));
        }
    }

    private void showStartRecordingButton() {
        runOnUiThread(() -> {
            startRecordingButton.setVisibility(View.VISIBLE);
            stopRecordingButton.setVisibility(View.GONE);
        });
    }

    private void showStopRecordingButton() {
        runOnUiThread(() -> {
            startRecordingButton.setVisibility(View.GONE);
            stopRecordingButton.setVisibility(View.VISIBLE);
        });
    }

    private void showControls() {
        runOnUiThread(() -> {
            if (!heightControls.isShown()) {
                heightControls.setVisibility(View.VISIBLE);
                heightControls.setAlpha(0f);
                heightControls.animate().alpha(1.0f).setDuration(200);
            }
        });
    }

    private void showCaptureButton() {
        runOnUiThread(() -> captureButton.setVisibility(View.VISIBLE));
    }

    private void startRecording() {
        Log.d(TAG, "Start recoeding called");
        if (isRecording) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> permissionsNeeded = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.CAMERA);
            }
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
            }
            if (!permissionsNeeded.isEmpty()) {
                requestPermissions(permissionsNeeded.toArray(new String[0]), REQUEST_WRITE_STORAGE_PERMISSION);
                return;
            }
        }
        hideARFeatures();
        Log.d(TAG, "Hide AR Features called");
        isRecording = videoRecorder.onToggleRecord();
        Log.d(TAG, "isRecording" + isRecording);
        showStopRecordingButton();
        Log.d(TAG, " showStopRecordingButton called");
    }

    private void stopRecording() {
        Log.d(TAG, " Stop recording called");
        if (!isRecording) {
            return;
        }

        isRecording = !videoRecorder.onToggleRecord();
        Log.d(TAG, "isRecording" + isRecording);
        restoreARFeatures();
        Log.d(TAG, "restore AR Features called");
        showStartRecordingButton();
        Log.d(TAG, "showStartRecordingButton called");
        String videoPath = videoRecorder.getVideoPath().getAbsolutePath();
        Toast.makeText(this, "Video saved: " + videoPath, Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Video saved: " + videoPath);

        // Send notification of updated content.
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.TITLE, "Sceneform Video");
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.DATA, videoPath);
        getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
    }

    private void restoreInitialUI() {
        captureButton.setVisibility(View.VISIBLE);
        startRecordingButton.setVisibility(View.VISIBLE);
        stopRecordingButton.setVisibility(View.GONE);
        returnButton.setVisibility(View.GONE);
        restoreARFeatures();
    }
}
