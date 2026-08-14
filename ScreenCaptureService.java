package org.example.screenrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;

import android.content.Context;
import android.content.Intent;

import android.graphics.Bitmap;
import android.graphics.PixelFormat;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;

import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;

import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;

import android.os.Build;
import android.os.Environment;
import android.os.IBinder;

import android.util.DisplayMetrics;

import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;

import java.nio.ByteBuffer;

import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.Locale;

public class ScreenCaptureService extends Service {

    private static final String CHANNEL_ID =
            "screen_recorder_channel";

    private static final int NOTIFICATION_ID = 1001;

    private MediaProjection mediaProjection;

    private VirtualDisplay virtualDisplay;

    private MediaRecorder mediaRecorder;

    private ImageReader imageReader;

    private VirtualDisplay screenshotDisplay;

    private boolean recording = false;

    @Override
    public void onCreate() {

        super.onCreate();

        createNotificationChannel();

        Notification notification =
                new Notification.Builder(
                        this,
                        CHANNEL_ID
                )
                .setContentTitle("ضبط صفحه")
                .setContentText(
                        "ضبط صفحه در حال اجراست"
                )
                .setSmallIcon(
                        android.R.drawable.ic_menu_camera
                )
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q) {

            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo
                    .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            );

        } else {

            startForeground(
                    NOTIFICATION_ID,
                    notification
            );
        }
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        if ("STOP_RECORDING".equals(action)) {

            stopRecording();

            stopSelf();

            return START_NOT_STICKY;
        }

        if ("SCREENSHOT".equals(action)) {

            takeScreenshot();

            return START_NOT_STICKY;
        }

        if (!recording &&
                intent.hasExtra("resultCode") &&
                intent.hasExtra("data")) {

            int resultCode =
                    intent.getIntExtra(
                            "resultCode",
                            -1
                    );

            Intent data =
                    intent.getParcelableExtra(
                            "data"
                    );

            if (data != null &&
                    resultCode != -1) {

                startRecording(
                        resultCode,
                        data
                );
            }
        }

        return START_NOT_STICKY;
    }

    private void startRecording(
            int resultCode,
            Intent data
    ) {

        try {

            MediaProjectionManager manager =
                    (MediaProjectionManager)
                    getSystemService(
                            MEDIA_PROJECTION_SERVICE
                    );

            mediaProjection =
                    manager.getMediaProjection(
                            resultCode,
                            data
                    );

            if (mediaProjection == null) {
                return;
            }

            DisplayMetrics metrics =
                    new DisplayMetrics();

            WindowManager windowManager =
                    (WindowManager)
                    getSystemService(
                            WINDOW_SERVICE
                    );

            windowManager
                    .getDefaultDisplay()
                    .getRealMetrics(metrics);

            int width =
                    metrics.widthPixels;

            int height =
                    metrics.heightPixels;

            int density =
                    metrics.densityDpi;

            if (width > 1920) {
                width = 1920;
            }

            if (height > 1080) {
                height = 1080;
            }

            mediaRecorder =
                    new MediaRecorder();

            File moviesDir =
                    new File(
                            getExternalFilesDir(
                                    Environment
                                    .DIRECTORY_MOVIES
                            ),
                            "ScreenRecorder"
                    );

            if (!moviesDir.exists()) {
                moviesDir.mkdirs();
            }

            String time =
                    new SimpleDateFormat(
                            "yyyyMMdd_HHmmss",
                            Locale.US
                    )
                    .format(
                            new Date()
                    );

            File output =
                    new File(
                            moviesDir,
                            "screen_" +
                            time +
                            ".mp4"
                    );

            mediaRecorder.setVideoSource(
                    MediaRecorder.VideoSource.SURFACE
            );

            mediaRecorder.setOutputFormat(
                    MediaRecorder.OutputFormat.MPEG_4
            );

            mediaRecorder.setVideoEncoder(
                    MediaRecorder.VideoEncoder.H264
            );

            mediaRecorder.setVideoEncodingBitRate(
                    8 * 1000 * 1000
            );

            mediaRecorder.setVideoFrameRate(
                    30
            );

            mediaRecorder.setVideoSize(
                    width,
                    height
            );

            mediaRecorder.setOutputFile(
                    output.getAbsolutePath()
            );

            mediaRecorder.prepare();

            virtualDisplay =
                    mediaProjection
                    .createVirtualDisplay(
                            "ScreenRecorder",
                            width,
                            height,
                            density,
                            DisplayManager
                            .VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                            mediaRecorder.getSurface(),
                            null,
                            null
                    );

            mediaRecorder.start();

            recording = true;

        }

        catch (Exception e) {

            e.printStackTrace();

            stopRecording();
        }
    }

    private void takeScreenshot() {

        if (mediaProjection == null) {
            return;
        }

        try {

            DisplayMetrics metrics =
                    new DisplayMetrics();

            WindowManager windowManager =
                    (WindowManager)
                    getSystemService(
                            WINDOW_SERVICE
                    );

            windowManager
                    .getDefaultDisplay()
                    .getRealMetrics(metrics);

            int width =
                    metrics.widthPixels;

            int height =
                    metrics.heightPixels;

            int density =
                    metrics.densityDpi;

            imageReader =
                    ImageReader.newInstance(
                            width,
                            height,
                            PixelFormat.RGBA_8888,
                            2
                    );

            imageReader.setOnImageAvailableListener(
                    reader -> {

                        Image image = null;

                        try {

                            image =
                                    reader
                                    .acquireLatestImage();

                            if (image == null) {
                                return;
                            }

                            Image.Plane plane =
                                    image.getPlanes()[0];

                            ByteBuffer buffer =
                                    plane.getBuffer();

                            int pixelStride =
                                    plane.getPixelStride();

                            int rowStride =
                                    plane.getRowStride();

                            int rowPadding =
                                    rowStride -
                                    pixelStride * width;

                            int bitmapWidth =
                                    width +
                                    rowPadding /
                                    pixelStride;

                            Bitmap bitmap =
                                    Bitmap.createBitmap(
                                            bitmapWidth,
                                            height,
                                            Bitmap.Config
                                            .ARGB_8888
                                    );

                            buffer.rewind();

                            bitmap.copyPixelsFromBuffer(
                                    buffer
                            );

                            Bitmap cropped =
                                    Bitmap.createBitmap(
                                            bitmap,
                                            0,
                                            0,
                                            width,
                                            height
                                    );

                            File picturesDir =
                                    new File(
                                            getExternalFilesDir(
                                                    Environment
                                                    .DIRECTORY_PICTURES
                                            ),
                                            "ScreenRecorder"
                                    );

                            if (!picturesDir.exists()) {
                                picturesDir.mkdirs();
                            }

                            String time =
                                    new SimpleDateFormat(
                                            "yyyyMMdd_HHmmss",
                                            Locale.US
                                    )
                                    .format(
                                            new Date()
                                    );

                            File output =
                                    new File(
                                            picturesDir,
                                            "screenshot_" +
                                            time +
                                            ".png"
                                    );

                            FileOutputStream fos =
                                    new FileOutputStream(
                                            output
                                    );

                            cropped.compress(
                                    Bitmap.CompressFormat.PNG,
                                    100,
                                    fos
                            );

                            fos.flush();

                            fos.close();

                            cropped.recycle();

                            bitmap.recycle();

                        }

                        catch (Exception e) {

                            e.printStackTrace();

                        }

                        finally {

                            if (image != null) {
                                image.close();
                            }

                            stopScreenshot();
                        }

                    },
                    null
            );

            screenshotDisplay =
                    mediaProjection
                    .createVirtualDisplay(
                            "Screenshot",
                            width,
                            height,
                            density,
                            DisplayManager
                            .VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                            imageReader.getSurface(),
                            null,
                            null
                    );

        }

        catch (Exception e) {

            e.printStackTrace();

            stopScreenshot();
        }
    }

    private void stopScreenshot() {

        try {

            if (screenshotDisplay != null) {

                screenshotDisplay.release();

                screenshotDisplay = null;
            }

            if (imageReader != null) {

                imageReader.close();

                imageReader = null;
            }

        }

        catch (Exception e) {

            e.printStackTrace();
        }
    }

    private void stopRecording() {

        try {

            if (mediaRecorder != null) {

                if (recording) {

                    try {

                        mediaRecorder.stop();

                    }

                    catch (Exception ignored) {
                    }
                }

                mediaRecorder.reset();

                mediaRecorder.release();

                mediaRecorder = null;
            }

            if (virtualDisplay != null) {

                virtualDisplay.release();

                virtualDisplay = null;
            }

            recording = false;

        }

        catch (Exception e) {

            e.printStackTrace();
        }
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "ضبط صفحه",
                            NotificationManager
                            .IMPORTANCE_LOW
                    );

            NotificationManager manager =
                    (NotificationManager)
                    getSystemService(
                            Context.NOTIFICATION_SERVICE
                    );

            manager.createNotificationChannel(
                    channel
            );
        }
    }

    @Override
    public void onDestroy() {

        stopRecording();

        stopScreenshot();

        if (mediaProjection != null) {

            mediaProjection.stop();

            mediaProjection = null;
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(
            Intent intent
    ) {

        return null;
    }
}
