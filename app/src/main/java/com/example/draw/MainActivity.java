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
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Camera;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import com.google.ar.sceneform.ArSceneView;

public class MainActivity extends AppCompatActivity {

    private ArFragment arFragment;

    private AnchorNode lastAnchorNode;

    private static final String TAG = MainActivity.class.getSimpleName();
    private final List<AnchorNode> anchorNodes = new ArrayList<>();


    private ModelRenderable andyRenderable;

    private static final double MIN_OPENGL_VERSION = 3.0;

    private Button captureButton;

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

            if(anchorNodes.size() == 2){
                addLineBetweenHits(hitResult, plane, motionEvent);
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

        });
    }

    private void addLineBetweenHits(HitResult hitResult, Plane plane, MotionEvent motionEvent) {
        Log.d(TAG, "Inside addLineBetweenHits");
        Anchor anchor = hitResult.createAnchor();
        AnchorNode anchorNode = new AnchorNode(anchor);

        if (lastAnchorNode != null) {
            anchorNode.setParent(arFragment.getArSceneView().getScene());
            Vector3 point1, point2;
            point1 = anchorNodes.get(0).getWorldPosition();
            point2 = anchorNodes.get(1).getWorldPosition();

            final Vector3 difference = Vector3.subtract(point1, point2);
            final Vector3 directionFromTopToBottom = difference.normalized();
            final Quaternion rotationFromAToB =
                    Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());
            Color color;
            color = new Color(android.graphics.Color.RED);
            MaterialFactory.makeOpaqueWithColor(getApplicationContext(), color)
                    .thenAccept(
                            material -> {
/* Then, create a rectangular prism, using ShapeFactory.makeCube() and use the difference vector to extend to the necessary length.  */
                                ModelRenderable model = ShapeFactory.makeCube(
                                        new Vector3(.01f, .01f, difference.length()),
                                        Vector3.zero(), material);
/* Last, set the world rotation of the node to the rotation calculated earlier and set the world position to the midpoint between the given points . */
                                Node node = new Node();
                                node.setParent(anchorNode);
                                node.setRenderable(model);
                                node.setWorldPosition(Vector3.add(point1, point2).scaled(.5f));
                                node.setWorldRotation(rotationFromAToB);
                            }
                    );
            lastAnchorNode = anchorNode;
        }
        else{
            Log.d(TAG, "addLineBetweenHits: ", null);
        }
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