package com.example.yolosegmentation;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.lifecycle.ProcessCameraProvider;

import com.example.yolosegmentation.databinding.ActivityImagePickerBinding;
import com.example.yolosegmentation.models.Yolo;
import com.example.yolosegmentation.models.Yolov8Seg;
import com.example.yolosegmentation.utils.utils;
import com.example.yolosegmentation.view.PolygonView;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImagePickerActivity extends AppCompatActivity {
    private static final int PICK_IMAGE = 1;
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
    ActivityImagePickerBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityImagePickerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        polygonView = findViewById(R.id.polygonView);
        this.executor = Executors.newSingleThreadExecutor();
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Unable to load OpenCV");
        } else {
            Log.d("OpenCV", "OpenCV loaded successfully");
        }
        yolo_model = new Yolov8Seg(this, "model2.tflite", true, 4, false, true, "labels2.txt", 0);
        try {
            yolo_model.initialize_model();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        binding.button.setOnClickListener(v -> {
            Intent i = new Intent();
            i.setType("image/*");
            i.setAction(Intent.ACTION_GET_CONTENT);

            // pass the constant to compare it
            // with the returned requestCode
            startActivityForResult(Intent.createChooser(i, "Select Picture"), PICK_IMAGE);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                    polygonView.setPreviewSize(bitmap.getWidth(), bitmap.getHeight());
                    int size = bitmap.getRowBytes() * bitmap.getHeight();

                    ByteBuffer byteBuffer = ByteBuffer.allocate(size);
                    bitmap.copyPixelsToBuffer(byteBuffer);
                    byte[] byteArray = byteBuffer.array();
                    binding.imageView.setImageBitmap(bitmap);
                    yolo_on_image(bitmap,bitmap.getHeight(), bitmap.getWidth(), 0.4, 0.5, 0.5);




                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class DetectionTask implements Runnable {
        private Yolo yolo;
        Bitmap image;
        private int image_height;
        private int image_width;
        private float iou_threshold;
        private float conf_threshold;
        private float class_threshold;

        public DetectionTask(Yolo yolo, Bitmap image, double image_height, double image_width, double iou_threshold, double conf_threshold, double class_threshold) {
            this.yolo = yolo;
            this.image = image;
            this.image_height = (int) image_height;
            this.image_width = (int) image_width;
            this.iou_threshold = (float) (double) iou_threshold;
            this.conf_threshold = (float) (double) conf_threshold;
            this.class_threshold = (float) (double) class_threshold;
        }
        public void print(String s) {
            System.out.println(s);
        }

        @Override
        public void run() {
            try {
               Bitmap bitmap = image;
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

    private void yolo_on_image(Bitmap image, double imageHeight, double imageWidth, double iouThreshold, double confThreshold, double classThreshold) {
        if (yolo_model != null ) {
            isDetecting = true;
            DetectionTask detectionTask = new DetectionTask(yolo_model, image, imageHeight, imageWidth, iouThreshold, confThreshold, classThreshold);
            executor.execute(detectionTask);
        }
    }
}
