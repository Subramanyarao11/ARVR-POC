package com.example.draw;


import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.CamcorderProfile;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.PixelCopy;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.airbnb.lottie.LottieAnimationView;
import com.google.ar.core.Anchor;
import com.google.ar.core.Plane;
import com.google.ar.core.TrackingState;
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
import com.google.ar.sceneform.ux.TransformableNode;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.ar.sceneform.ArSceneView;
import android.media.MediaScannerConnection;
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
//                  new Vector3(.01f, .01f, difference.length()),
                    new Vector3(0.003f, 0.003f, difference.length()),
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

    private WritingArFragment arFragment;



    private ConnectingLine connectingLine;


    private static final String TAG = MainActivity.class.getSimpleName();
    private final List<AnchorNode> anchorNodes = new ArrayList<>();
    private List<LineRenderer> lines = new ArrayList<>();


    private ModelRenderable andyRenderable;

    private static final double MIN_OPENGL_VERSION = 3.0;

    private ImageButton captureButton;

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

    private boolean updatePlaneDetectionOverlay = true;

    private boolean isAutoCapturing = false;
    private Handler autoCaptureHandler = new Handler(Looper.getMainLooper());
    private Runnable autoCaptureRunnable;

    private LottieAnimationView lottieArrowLeft, lottieArrowRight;
    private ImageView imgPhones;

    private boolean visualsShown = false;

    private ArrayList<Bitmap> capturedImages = new ArrayList<>();

    private RecyclerView recyclerView;
    private ImageAdapter imageAdapter;

    private Button ProceedButton;

    private LinearLayout bottomBar;

    private LinearLayout topBar;

    private ImageButton closeButton;

    private TextView progressText;

    // Auto Manual Function trigger, here 1 is for manual & 2 is for auto capture
    private int isCheckedType = 1;
    // used for identifying video or camera type, here 1 is for camera & 2 is for video rec
    private int isVideoPlayBtn = 1;



    @Override
    protected void onResume() {
        super.onResume();
        arFragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
            if (arFragment.getArSceneView().getSession() != null) {
                for (Plane plane : arFragment.getArSceneView().getSession().getAllTrackables(Plane.class)) {
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
        progressText = findViewById(R.id.progressText);

//        startRecordingButton = findViewById(R.id.startRecordingButton);
//        stopRecordingButton = findViewById(R.id.stopRecordingButton);
        ProceedButton = findViewById(R.id.buttonContinue);
        bottomBar = findViewById(R.id.bottomBar);
        topBar = findViewById(R.id.topBar);
        closeButton = findViewById(R.id.closeButton);

        lottieArrowLeft = findViewById(R.id.lottieArrowLeft);
        lottieArrowRight = findViewById(R.id.lottieArrowRight);
        imgPhones = findViewById(R.id.imgPhones);

        recyclerView = findViewById(R.id.imageCarousel);
        imageAdapter = new ImageAdapter(this, capturedImages);
        recyclerView.setAdapter(imageAdapter);


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

//        startRecordingButton.setOnClickListener(v -> {
//            if (!arFragment.hasWritePermission()) {
//                Toast.makeText(this, "Permission required to start recording", Toast.LENGTH_SHORT).show();
//                arFragment.launchPermissionSettings();
//                return;
//            }
//            startRecording();
//        });

//        stopRecordingButton.setOnClickListener(v -> stopRecording());

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

        ProceedButton.setOnClickListener(v-> {
            ProceedButton.setVisibility(View.GONE);
            topBar.setVisibility(View.VISIBLE);
            lockHeightAdjustment();
            lockNodeTransformations();
            bottomBar.setVisibility(View.VISIBLE);
             captureButton.setEnabled(false);
        RadioButton radioCamera = findViewById(R.id.radioCamera);
        RadioButton radioVideo = findViewById(R.id.radioVideo);
        RadioGroup radioGroup = findViewById(R.id.radioGroup);
        RadioGroup topButtons = findViewById(R.id.topButtons);
        RadioButton radioAuto = findViewById(R.id.radioAuto);
        RadioButton radioManual = findViewById(R.id.radioManual);
        SimpleInstructionOverlay captureSimpleInstructionOverlay = findViewById(R.id.captureSimpleInstructionOverlay);
        btnBack.setVisibility(View.GONE);
        btnReset.setVisibility(View.GONE);
        heightControls.setVisibility(View.GONE);
        radioCamera.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.camera, 0, 0);
        radioCamera.setBackgroundResource(R.drawable.bg_selector);
        radioVideo.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.video_gray, 0, 0);
        radioVideo.setBackgroundResource(R.drawable.bg_selector);

        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.radioCamera) {
                    isCheckedType = 1;
                    progressText.setVisibility(View.VISIBLE);
                    capturedImages.clear();
                    progressText.setText("0");
                    radioCamera.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.camera, 0, 0);
                    radioCamera.setBackgroundResource(R.drawable.bg_selector);
                    radioVideo.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.video_gray, 0, 0);
                    radioVideo.setBackgroundResource(R.drawable.bg_selector);
                } else if (checkedId == R.id.radioVideo) {
                    isCheckedType = 3;
                    progressText.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.GONE);
                    capturedImages.clear();
                    radioVideo.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.video, 0, 0);
                    radioVideo.setBackgroundResource(R.drawable.bg_selector);
                    radioCamera.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.camera_gray, 0, 0);
                    radioCamera.setBackgroundResource(R.drawable.bg_selector);
                }
            }
        });

            topButtons.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.radioAuto) {
                    isCheckedType = 2;
                    radioAuto.setTextColor(getResources().getColor(android.R.color.white));
                    radioManual.setTextColor(getResources().getColor(R.color.bg_blue));
                } else if (checkedId == R.id.radioManual) {
                    isCheckedType = 1;
                    radioManual.setTextColor(getResources().getColor(android.R.color.white));
                    radioAuto.setTextColor(getResources().getColor(R.color.bg_blue));
                }
            });


            captureSimpleInstructionOverlay.setVisibility(View.VISIBLE);
            captureSimpleInstructionOverlay.setText("You can switch between auto and manual capturing mode");
            progressText.setVisibility(View.GONE);

            new Handler().postDelayed(() -> {
                captureSimpleInstructionOverlay.setVisibility(View.GONE);
                progressText.setVisibility(View.VISIBLE);
                showVisualsForShortDuration();

                new Handler().postDelayed(() -> {
                    captureButton.setEnabled(true);
                }, 3000);
            }, 3000);
        });




        closeButton.setOnClickListener(v -> {
            ProceedButton.setVisibility(View.VISIBLE);
            unlockHeightAdjustment();
            unlockNodeTransformations();
            topBar.setVisibility(View.GONE);
            bottomBar.setVisibility(View.GONE);
            btnBack.setVisibility(View.VISIBLE);
            btnReset.setVisibility(View.VISIBLE);
            progressText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.GONE);
            heightControls.setVisibility(View.VISIBLE);
        });

        captureButton = findViewById(R.id.shutter);
        captureButton.setOnClickListener(v -> {
            if(isCheckedType == 1){
                Log.d(TAG, "Manual Capture button clicked");
                capturePhoto();
            } else if(isCheckedType == 2) {
                Log.d(TAG, "Auto Capture button clicked");
                onAutoCaptureClicked();
            } else if(isCheckedType == 3) {
                if(isVideoPlayBtn == 1) {
                    startRecording();
                    isVideoPlayBtn = 2;
                    progressText.setVisibility(View.GONE);
                    captureButton.setImageResource(R.drawable.stop);
                } else if(isVideoPlayBtn == 2){
                    isVideoPlayBtn = 1;
                    stopRecording();
                    captureButton.setImageResource(R.drawable.shutter);
                }
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
                            showControls();
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

        videoRecorder = new VideoRecorder();
        int orientation = getResources().getConfiguration().orientation;
        videoRecorder.setVideoQuality(CamcorderProfile.QUALITY_720P, orientation);
        videoRecorder.setSceneView(arFragment.getArSceneView());
    }

//    @Override
//    protected void onPause() {
//        Toast.makeText(this, "onPause", Toast.LENGTH_SHORT).show();
//        super.onPause();
//        if (isRecording) {
//            stopRecording();
//        }
//    }

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
//        showControls();
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

    private void showForShortDuration() {
        captureButton.setEnabled(false);
        lottieArrowLeft.setVisibility(View.VISIBLE);
        imgPhones.setVisibility(View.VISIBLE);
        lottieArrowRight.setVisibility(View.VISIBLE);
        SimpleInstructionOverlay simpleOverlay = findViewById(R.id.simpleInstructionOverlay);
        simpleOverlay.setText("Got it, move your smartphone to the next box either to the left or right.");
        simpleOverlay.setVisibility(View.VISIBLE);

        // Hide after 3 seconds
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            lottieArrowLeft.setVisibility(View.GONE);
            imgPhones.setVisibility(View.GONE);
            lottieArrowRight.setVisibility(View.GONE);
            simpleOverlay.setVisibility(View.GONE);
            captureButton.setEnabled(true);
        }, 3000);
    }



    private void capturePhoto() {
        lockHeightAdjustment();
        hideARFeatures();
//        captureAndProcessImage();
        disableCaptureButton();
        new Handler(Looper.getMainLooper()).postDelayed(this::captureAndCropWithBoundsCalculation, 100);
    }

    private void captureAndProcessImage() {
        captureAndCropWithBoundsCalculation();
        enableCaptureButton();
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
                capturedImages.add(bitmap);
                progressText.setText(String.valueOf(capturedImages.size()));
                if (imageAdapter != null) {
                    runOnUiThread(() -> {
                        imageAdapter.notifyDataSetChanged();
                        recyclerView.scrollToPosition(capturedImages.size() - 1);
                        updateCarouselVisibility();
                    });
                }
                Log.d(TAG, "Photo saved to " + uri.toString());
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
//        startRecordingButton.setVisibility(View.GONE);
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

    private void showCaptureButton() {
        runOnUiThread(() -> captureButton.setVisibility(View.VISIBLE));
    }

    private void lockNodeTransformations() {
        for (AnchorNode anchorNode : anchorNodes) {
            if (!anchorNode.getChildren().isEmpty() && anchorNode.getChildren().get(0) instanceof TransformableNode) {
                TransformableNode transformableNode = (TransformableNode) anchorNode.getChildren().get(0);
                transformableNode.getTranslationController().setEnabled(false);
                transformableNode.getRotationController().setEnabled(false);
                transformableNode.getScaleController().setEnabled(false);
            }
        }
    }

    private void unlockNodeTransformations() {
        for (AnchorNode anchorNode : anchorNodes) {
            if (!anchorNode.getChildren().isEmpty() && anchorNode.getChildren().get(0) instanceof TransformableNode) {
                TransformableNode transformableNode = (TransformableNode) anchorNode.getChildren().get(0);
                transformableNode.getTranslationController().setEnabled(true);
                transformableNode.getRotationController().setEnabled(true);
                transformableNode.getScaleController().setEnabled(true);
            }
        }
    }

    private void hidePointCloud() {
        arFragment.getArSceneView().getPlaneRenderer().setEnabled(false);
    }

    private void showPointCloud() {
        arFragment.getArSceneView().getPlaneRenderer().setEnabled(true);
    }



    private void lockHeightAdjustment() {
        seekBarHeight.setEnabled(false);
        btnDecreaseHeight.setEnabled(false);
        btnIncreaseHeight.setEnabled(false);
        lockNodeTransformations();
    }

    private void unlockHeightAdjustment() {
        seekBarHeight.setEnabled(true);
        btnDecreaseHeight.setEnabled(true);
        btnIncreaseHeight.setEnabled(true);
        unlockNodeTransformations();
    }

    public void onAutoCaptureClicked() {
        if (!isAutoCapturing) {
//            showVisualsForShortDuration();
            startAutoCapture();
            captureButton.setImageResource(R.drawable.pause);
        } else {
            stopAutoCapture();
            captureButton.setImageResource(R.drawable.shutter);
        }
    }

    private void startAutoCapture() {
        isAutoCapturing = true;
        autoCaptureRunnable = new Runnable() {
            @Override
            public void run() {
                if (isAutoCapturing) {
                    capturePhoto();
                    autoCaptureHandler.postDelayed(this, 5000);
                }
            }
        };
        autoCaptureRunnable.run();
    }

    private void stopAutoCapture() {
        isAutoCapturing = false;
        autoCaptureHandler.removeCallbacks(autoCaptureRunnable);
        visualsShown=false;
    }



    private void startRecording() {
        if (!isRecording) {
//            showVisualsForShortDuration();
            hideARFeatures();
            lockHeightAdjustment();
            isRecording = videoRecorder.onToggleRecord();
        }
    }

    private void showVisualsForShortDuration() {
        if (!visualsShown) {
            showForShortDuration();
            visualsShown = true;
        }
    }



    private void stopRecording() {
        if (isRecording) {
            isRecording = !videoRecorder.onToggleRecord();
            restoreARFeatures();
            unlockHeightAdjustment();
            visualsShown = false;
            String videoPath = videoRecorder.getVideoPath().getAbsolutePath();
            File videoFile = new File(videoPath);
            if (videoFile.exists() && videoFile.length() > 0) {
                MediaScannerConnection.scanFile(MainActivity.this, new String[] { videoPath }, null, null);
            } else {
                Log.e(TAG, "Video file does not exist or is empty: " + videoPath);
            }
        }
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

    private void updateCarouselVisibility() {
        if (capturedImages.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

}