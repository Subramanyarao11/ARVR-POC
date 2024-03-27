package com.example.draw;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.PixelCopy;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.ar.core.Frame;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Camera;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import com.google.ar.sceneform.ArSceneView;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.ObjectDetectorOptionsBase;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;

public class MainActivity extends AppCompatActivity {

    private ArFragment arFragment;

    private static final String TAG = MainActivity.class.getSimpleName();
    private final List<AnchorNode> anchorNodes = new ArrayList<>();


    private ModelRenderable andyRenderable;

    private static final double MIN_OPENGL_VERSION = 3.0;

    private Button captureButton;

    // Object Detection
    private ObjectDetector objectDetector;
    private GraphicOverlay graphicOverlay; // For drawing bounding boxes
    private boolean needUpdateGraphicOverlayImageSourceInfo;


    private int rotationDegrees = 0;


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

//       setup orientation listener
        setupOrientationListener();


        captureButton = findViewById(R.id.captureButton);
        captureButton.setOnClickListener(v -> {
            Log.d(TAG, "Capture button clicked");
            capturePhoto();
        });

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arFragment);
        assert arFragment != null;
        ArSceneView arSceneView = arFragment.getArSceneView();

        // Object Detection Setup
        graphicOverlay = findViewById(R.id.graphic_overlay); // Initialize graphicOverlay
        createObjectDetector(); // Create the object detector

        arFragment.setOnTapArPlaneListener((hitResult, plane, motionEvent) -> {
            if (objectDetector == null) {
                return;
            }

            // Get the current frame from the AR Scene and check if it's not null
            Frame frame = arSceneView.getArFrame();
            if (frame != null && frame.getCamera().getTrackingState() == TrackingState.TRACKING) {
                try {
                    // Attempt to acquire the camera image
                    Image image = frame.acquireCameraImage();
                    if (image != null) {
                        InputImage inputImage = InputImage.fromMediaImage(image, rotationDegrees);

                        // Process the image with the object detector
                        objectDetector.process(inputImage)
                                .addOnSuccessListener(
                                        detectedObjects -> {
                                            // Clear previous bounding boxes
                                            graphicOverlay.clear();

                                            // Update graphicOverlay size if needed
                                            if (needUpdateGraphicOverlayImageSourceInfo) {
                                                graphicOverlay.setImageSourceInfo(
                                                        inputImage.getWidth(), inputImage.getHeight(), false);
                                                needUpdateGraphicOverlayImageSourceInfo = false;
                                            }

                                            // Draw bounding boxes for detected objects
                                            for (DetectedObject detectedObject : detectedObjects) {
                                                graphicOverlay.add(new ObjectGraphic(graphicOverlay, detectedObject));
                                            }

                                            // Show capture button if an object is detected
                                            if (detectedObjects.size() > 0) {
                                                DetectedObject firstObject = detectedObjects.get(0);
                                                captureAndCrop(firstObject.getBoundingBox());

                                                captureButton.setVisibility(View.VISIBLE);
                                            }

                                            graphicOverlay.postInvalidate();
                                        })
                                .addOnFailureListener(e -> Log.e(TAG, "Error running object detection", e))
                                .addOnCompleteListener(task -> image.close()); // Ensure to close the image
                    }
                } catch (NotYetAvailableException e) {
                    Log.e(TAG, "Camera image not yet available. Please try again.", e);
                }
            }
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
        Vector3 screenPoint = camera.worldToScreenPoint(worldPoint);
        return new Point((int) screenPoint.x, (int) screenPoint.y);
    }


    private void capturePhoto() {
//        hideARFeatures();
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
            Point screenPoint = worldToScreenPoint(anchorNode.getWorldPosition());
            if (screenPoint.x < minX) minX = screenPoint.x;
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

//    private void captureAndCrop(int x, int y, int width, int height) {
//        ArSceneView view = arFragment.getArSceneView();
//        final Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
//
//        PixelCopy.request(view, bitmap, (copyResult) -> {
////            restoreARFeatures();
//            if (copyResult == PixelCopy.SUCCESS) {
//                // Crop the bitmap
//                Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, x, y, width, height);
//                saveBitmapToFile(croppedBitmap); // Implement this method to save the cropped bitmap
//            } else {
//                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to capture photo", Toast.LENGTH_SHORT).show());
//            }
//        }, new Handler(Looper.getMainLooper()));
//    }

    private void captureAndCrop(Rect boundingBox) { // Pass the bounding box as a parameter
        ArSceneView view = arFragment.getArSceneView();
        final Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);

        PixelCopy.request(view, bitmap, (copyResult) -> {
            if (copyResult == PixelCopy.SUCCESS) {
                // Convert bounding box coordinates to screen coordinates
                int x = (int) translateX(boundingBox.left);
                int y = (int) translateY(boundingBox.top);
                int width = (int) (boundingBox.right - boundingBox.left);
                int height = (int) (boundingBox.bottom - boundingBox.top);

                // Crop the bitmap using the bounding box coordinates
                Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, x, y, width, height);
                saveBitmapToFile(croppedBitmap);
            } else {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to capture photo", Toast.LENGTH_SHORT).show());
            }
        }, new Handler(Looper.getMainLooper()));
    }

private void saveBitmapToFile(Bitmap bitmap) {
    // Define the file attributes
    String displayName = "ARScene_" + System.currentTimeMillis() + ".png";
    ContentValues values = new ContentValues();
    values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
    values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
    // For images saved to the Pictures directory
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

    private void setupOrientationListener() {
        OrientationEventListener orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation >= 45 && orientation < 135) {
                    rotationDegrees = 270; // Reverse landscape
                } else if (orientation >= 135 && orientation < 225) {
                    rotationDegrees = 180; // Reverse portrait
                } else if (orientation >= 225 && orientation < 315) {
                    rotationDegrees = 90; // Landscape
                } else {
                    rotationDegrees = 0; // Portrait
                }
            }
        };

        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable();
        } else {
            Log.d(TAG, "Orientation sensor not available.");
        }
    }


    private void createObjectDetector() {
        // Use the default options for object detection
        ObjectDetectorOptionsBase options =
                new ObjectDetectorOptions.Builder()
                        .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                        .enableMultipleObjects()
                        .enableClassification()
                        .build();
        objectDetector = ObjectDetection.getClient(options);
    }
}