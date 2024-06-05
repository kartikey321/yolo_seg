package com.example.yolosegmentation;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.media.Image;
import android.os.Bundle;
import android.util.Size;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.yolosegmentation.databinding.ActivityLiveCameraBinding;
import com.example.yolosegmentation.databinding.ActivityMainBinding;
import com.example.yolosegmentation.models.Yolo;
import com.example.yolosegmentation.models.Yolov8Seg;
import com.example.yolosegmentation.view.PolygonView;
import com.example.yolosegmentation.utils.utils;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LiveCameraActivity extends AppCompatActivity {
    private Context context;
    private PolygonView polygonView;

    private Yolo yolo_model;
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {android.Manifest.permission.CAMERA};

    private ExecutorService cameraExecutor;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    private ExecutorService executor;

    private boolean isDetecting = false;
    List<Map<String, Object>> detections;
    private static ArrayList<Map<String, Object>> empty = new ArrayList<>();
    ActivityLiveCameraBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLiveCameraBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        polygonView = findViewById(R.id.polygonView);

        this.executor = Executors.newSingleThreadExecutor();
        this.context = binding.getRoot().getContext();

        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(this, "Unable to load OpenCV", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.textView.setText("Detecting");

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
        try {
            load_yolo_model();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(640, 640))  // Adjust as needed
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
            @OptIn(markerClass = ExperimentalGetImage.class) @Override
            public void analyze(@NonNull ImageProxy imageProxy) {
                int rotation = imageProxy.getImageInfo().getRotationDegrees();
                int imageHeight = imageProxy.getHeight();
                int imageWidth = imageProxy.getWidth();

                // Set preview size for PolygonView
                runOnUiThread(() -> polygonView.setPreviewSize(imageWidth, imageHeight));

                // Get image format
                int format = imageProxy.getFormat();
                List<byte[]> frameBytes = new ArrayList<>();

                if (format == ImageFormat.YUV_420_888 || format == ImageFormat.YUV_422_888 || format == ImageFormat.YUV_444_888) {
                    // YUV formats: multiple planes
                    Image image = imageProxy.getImage();
                    if (image != null) {
                        Image.Plane[] planes = image.getPlanes();
                        for (Image.Plane plane : planes) {
                            ByteBuffer buffer = plane.getBuffer();
                            byte[] bytes = new byte[buffer.remaining()];
                            buffer.get(bytes);
                            frameBytes.add(bytes);
                        }
                    }
                } else {
                    // Other formats (e.g., RGBA_8888): single plane
                    ByteBuffer buffer = imageProxy.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    frameBytes.add(bytes);
                }

                // Call yolo_on_frame with the extracted bytes
                if (!frameBytes.isEmpty()) {
                    yolo_on_frame(frameBytes, imageHeight, imageWidth, 0.4, 0.5, 0.5);
                }

                imageProxy.close();
            }
        });

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

        cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                imageAnalysis,
                preview
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    private void load_yolo_model() throws Exception {
        yolo_model = new Yolov8Seg(
                context,
                "model2.tflite",
                true,
                1,
                false,
                false,
                "labels2.txt",
                90
        );
        yolo_model.initialize_model();
    }

    class DetectionTask implements Runnable {
        private Yolo yolo;
        private List<byte[]> frame;
        private int image_height;
        private int image_width;
        private float iou_threshold;
        private float conf_threshold;
        private float class_threshold;

        public DetectionTask(Yolo yolo, List<byte[]> frame, double image_height, double image_width, double iou_threshold, double conf_threshold, double class_threshold) {
            this.yolo = yolo;
            this.frame = frame;
            this.image_height = (int) image_height;
            this.image_width = (int) image_width;
            this.iou_threshold = (float) (double) iou_threshold;
            this.conf_threshold = (float) (double) conf_threshold;
            this.class_threshold = (float) (double) class_threshold;
        }

        @Override
        public void run() {
            try {
                Bitmap bitmap = utils.feedInputToBitmap(context, frame, image_height, image_width, 90);
                int[] shape = yolo.getInputTensor().shape();
                int src_width = bitmap.getWidth();
                int src_height = bitmap.getHeight();
                ByteBuffer byteBuffer = utils.feedInputTensor(bitmap, shape[1], shape[2], src_width, src_height, 0, 255);
                detections = yolo.detect_task(byteBuffer, src_height, src_width, iou_threshold, conf_threshold, class_threshold);
                isDetecting = false;
                List<String> tags = new ArrayList<>();
                for (Map<String, Object> output : detections) {
                    if (output.containsKey("tag")) {
                        tags.add((String) output.get("tag"));
                    }
                }

                runOnUiThread(() -> {
                    binding.textView.setText(tags.toString());
                    polygonView.setDetections(detections);
                });
            } catch (Exception e) {
                System.out.println(e);
            }
        }
    }

    private void yolo_on_frame(List<byte[]> frame, double imageHeight, double imageWidth, double iouThreshold, double confThreshold, double classThreshold) {
        if (yolo_model != null && !isDetecting) {
            isDetecting = true;
            DetectionTask detectionTask = new DetectionTask(yolo_model, frame, imageHeight, imageWidth, iouThreshold, confThreshold, classThreshold);
            executor.execute(detectionTask);
        }
    }
}