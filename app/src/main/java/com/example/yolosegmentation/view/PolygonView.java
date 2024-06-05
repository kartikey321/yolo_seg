package com.example.yolosegmentation.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PolygonView extends View {
    private List<Map<String, Object>> detections = new ArrayList<>();
    private Paint paint;
    private Paint textPaint;
    private Paint boxPaint;
    private int previewWidth;
    private int previewHeight;
    private Bitmap imageBitmap;

    public PolygonView(Context context) {
        super(context);
        init();
    }

    public PolygonView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PolygonView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(0x4DFF0000); // Red color
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setStrokeWidth(5f);

        textPaint = new Paint();
        textPaint.setColor(0xFFFFFFFF); // White color
        textPaint.setTextSize(40f);
        textPaint.setStyle(Paint.Style.FILL);

        boxPaint = new Paint();
        boxPaint.setColor(0xFFFF00FF); // Pink color
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(5f);
    }

    public void setDetections(List<Map<String, Object>> detections) {
        this.detections = detections;
        invalidate(); // Request a redraw
    }

    public void setPreviewSize(int width, int height) {
        this.previewWidth = width;
        this.previewHeight = height;
    }

    public void setImageBitmap(Bitmap bitmap) {
        this.imageBitmap = bitmap;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (imageBitmap != null) {
            // Draw the bitmap as the background
            canvas.drawBitmap(imageBitmap, null, new android.graphics.Rect(0, 0, getWidth(), getHeight()), null);
        }

        if (detections == null || detections.isEmpty()) {
            return;
        }

        // Scale factors to adapt the preview size to the view size
        double factorX = (double) getWidth() / previewWidth;
        double imgRatio = (double) previewWidth / previewHeight;
        double newWidth = previewWidth * factorX;
        double newHeight = newWidth / imgRatio;
        double factorY = (double)(getHeight() / previewHeight);
        double pady = (getHeight() - newHeight) / 2;
        for (Map<String, Object> detection : detections) {
            switch((String)( detection.get("tag"))){
    /*
    mode_connected
mode_disconnected
power_connected
power_disconnected
speaker
usb_connected
usb_disconnected
     */
                case "mode_connected":textPaint.setColor(0xFF00FF00);
                paint.setColor(0xFF00FF0);

                    break;
                case "mode_disconnected":textPaint.setColor(0xFFFF0000);
                    paint.setColor(0x80FF0000);
                    break;
                case "power_connected":textPaint.setColor(0xFF00FF00);
                    paint.setColor(0xFF00FF0);
                    break;
                case "power_disconnected":textPaint.setColor(0xFFFF0000);
                    paint.setColor(0x80FF0000);
                    break;
                case "speaker":textPaint.setColor(0xFF00FF00);
                    paint.setColor(0xFF00FF0);
                    break;
                case "usb_connected":textPaint.setColor(0xFF00FF00);
                    paint.setColor(0xFF00FF0);
                    break;
                case "usb_disconnected":textPaint.setColor(0xFFFF0000);
                    paint.setColor(0x80FF0000);
                    break;
                default:textPaint.setColor(0xFF00FF00);
                    paint.setColor(0xFF00FF0);
                    break;

            }
            // Handle polygons
            List<Map<String, Double>> polygons = (List<Map<String, Double>>) detection.get("polygons");
            if (polygons != null && !polygons.isEmpty()) {
                Path path = new Path();
                boolean firstPoint = true;
                for (Map<String, Double> point : polygons) {
                    float x = (float) (point.get("x") * factorX+pady);
                    float y = (float) (point.get("y") * factorY+pady+pady);

                    if (firstPoint) {
                        path.moveTo(x, y);
                        firstPoint = false;
                    } else {
                        path.lineTo(x, y);
                    }
                }
                path.close();
                canvas.drawPath(path, paint);
            }

            // Handle boxes
            float[] box = (float[]) detection.get("box");
            if (box != null && box.length == 5) {
                float left = box[0] * (float) factorX;
                float top = box[1] * (float) factorY ;
                float right = box[2] * (float) factorX;
                float bottom = box[3] * (float) factorY ;
                float confidence = box[4] * 100;

              //  canvas.drawRect(left, top, right, bottom, boxPaint);
                canvas.drawText(String.format("%s %.0f%%", detection.get("tag"), confidence), left, top - 10, textPaint);
            }
        }
    }
}
