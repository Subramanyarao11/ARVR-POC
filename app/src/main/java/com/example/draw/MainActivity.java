package com.example.draw;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.PixelCopy;
import android.widget.Button;
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
        try
        {
            Log.d(TAG, "Calling setupArFragment()");
            this.getActionBar().hide();
        }
        catch (NullPointerException e){
            Log.d(TAG, "onCreate: " + e);
        }

        setContentView(R.layout.activity_main);

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
        float width = edgeVector.length();
        Vector3 edgeDirection = edgeVector.normalized();

        // Vector from pt1 to pt3
        Vector3 fromPt1ToPt3 = Vector3.subtract(pt3, pt1);

        // Calculate the projection of fromPt1ToPt3 onto edgeVector
        float projectionLength = Vector3.dot(fromPt1ToPt3, edgeDirection);
        Vector3 projection = edgeDirection.scaled(projectionLength);

        // Calculate the perpendicular vector from the projection point on the edge to pt3
        Vector3 projectionPoint = Vector3.add(pt1, projection);
        Vector3 perpendicularVector = Vector3.subtract(pt3, projectionPoint);
        float depth = perpendicularVector.length();  // This is the depth

        // Determine the direction of the depth to ensure it extends perpendicular to the edge
        Vector3 crossProduct = Vector3.cross(edgeDirection, Vector3.up());
        if (Vector3.dot(perpendicularVector, crossProduct) < 0) {
            depth = -depth;
        }

        // Calculate the center of the rectangle
        Vector3 baseCenter = Vector3.add(pt1, pt2).scaled(0.5f);
        Vector3 depthOffset = crossProduct.normalized().scaled(depth / 2.0f);
        Vector3 rectangleCenter = Vector3.add(baseCenter, depthOffset);

        Quaternion rotation = Quaternion.lookRotation(edgeDirection, Vector3.up());
        float height = 0.1f;
        updateRectangle(rectangleCenter, width, height, Math.abs(depth), rotation);

    }

    private void updateRectangle(Vector3 center, float width, float height, float depth, Quaternion rotation) {
        Vector3 dimensions = new Vector3(width, height, depth);  // Create a new Vector3 for dimensions

        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.BLUE))
                .thenAccept(material -> {
                    ModelRenderable rectangle = ShapeFactory.makeCube(dimensions, Vector3.zero(), material);

                    if (currentRectangleNode != null) {
                        currentRectangleNode.setParent(null);  // Remove the old rectangle from the scene
                    }

                    currentRectangleNode = new Node();
                    currentRectangleNode.setRenderable(rectangle);
                    currentRectangleNode.setWorldPosition(center);
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
        // Delay the capture to ensure visibility changes have time to take effect
        new Handler(Looper.getMainLooper()).postDelayed(this::captureAndCropWithBoundsCalculation, 100);
    }

    private void captureAndCropWithBoundsCalculation() {
        // Calculate bounds
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;

        for (AnchorNode anchorNode : anchorNodes) {
            Log.d(TAG, "anchorNode.getWorldPosition(): " + anchorNode.getWorldPosition());
            Point screenPoint = worldToScreenPoint(anchorNode.getWorldPosition());
            if (screenPoint.x < minX) minX = screenPoint.x;
            if (screenPoint.y < minY) minY = screenPoint.y;
            if (screenPoint.y < minY) minY = screenPoint.y;
            if (screenPoint.x > maxX) maxX = screenPoint.x;
            if (screenPoint.y > maxY) maxY = screenPoint.y;
        }

        minX = Math.max(minX, 0);
        minY = Math.max(minY, 0);
        maxX = Math.min(maxX, arFragment.getArSceneView().getWidth());
        maxY = Math.min(maxY, arFragment.getArSceneView().getHeight());

        // Proceed with capture and crop
        captureAndCrop(minX, minY, maxX - minX, maxY - minY);
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
    }

    private void restoreARFeatures() {
        runOnUiThread(() -> {
            arFragment.getArSceneView().getPlaneRenderer().setVisible(true);
            for (AnchorNode anchorNode : anchorNodes) {
                anchorNode.setEnabled(true);
            }
        });
    }
}