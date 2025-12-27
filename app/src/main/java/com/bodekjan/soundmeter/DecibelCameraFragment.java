package com.bodekjan.soundmeter;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.os.Handler;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.os.Environment;
import java.io.InputStream;
import android.os.Build;
import android.provider.MediaStore;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;

import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.util.Consumer;
import androidx.lifecycle.LifecycleOwner;
import androidx.camera.core.ImageCaptureException;
import androidx.fragment.app.FragmentTransaction;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import androidx.camera.core.ImageProxy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.FileOutputStream;
import androidx.core.content.FileProvider;
import java.io.File;
import java.nio.ByteBuffer;
import android.net.Uri;

import java.text.SimpleDateFormat;
import android.os.Looper;

import android.location.LocationManager;
import android.content.Intent;
import android.provider.Settings;
import androidx.appcompat.app.AlertDialog;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.common.util.concurrent.ListenableFuture;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.VideoRecordEvent;

import java.util.concurrent.ExecutionException;

public class DecibelCameraFragment extends Fragment {

    private static final int PERMISSIONS_REQUEST_CODE = 10;
    private String[] permissionsRequired;

    private PreviewView previewView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private Recording activeRecording;

    private boolean isRecording = false;

    private TextView decibelText;
    private TextView avgDecibelText;
    private TextView minDecibelText;
    private TextView maxDecibelText;
    private TextView locationText;

    private MyMediaRecorder mRecorder;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private final Handler mHandler = new Handler();
    private Runnable mUpdateDecibelTask;

    private double minDecibel = 100.0;
    private double maxDecibel = 0.0;
    private double totalDecibel = 0.0;
    private int decibelCount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_decibel_camera, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        previewView = view.findViewById(R.id.preview_view);
        decibelText = view.findViewById(R.id.decibel_text);
        avgDecibelText = view.findViewById(R.id.avg_decibel_text);
        minDecibelText = view.findViewById(R.id.min_decibel_text);
        maxDecibelText = view.findViewById(R.id.max_decibel_text);
        locationText = view.findViewById(R.id.location_text);

        mRecorder = new MyMediaRecorder(requireContext());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissionsRequired = new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        } else {
            permissionsRequired = new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION};
        }

        if (allPermissionsGranted()) {
            startCamera();
            startLocationUpdates();
            startDecibelUpdates();
        } else {
            requestPermissions(permissionsRequired, PERMISSIONS_REQUEST_CODE);
        }

        ImageButton captureButton = view.findViewById(R.id.capture_button);
        captureButton.setOnClickListener(v -> takePhoto());

        ImageButton recordButton = view.findViewById(R.id.record_button);
        recordButton.setOnClickListener(v -> toggleVideoRecording());

        ImageButton flipCameraButton = view.findViewById(R.id.flip_camera_button);
        flipCameraButton.setOnClickListener(v -> flipCamera());
    }

    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll();

        Preview preview = new Preview.Builder().build();

        Recorder recorder = new Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build();
        videoCapture = VideoCapture.withOutput(recorder);

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, preview, imageCapture, videoCapture);
    }

    private void takePhoto() {
        if (imageCapture == null) {
            Toast.makeText(getContext(), R.string.camera_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }

        File photoFile = new File(requireContext().getCacheDir(), System.currentTimeMillis() + ".jpg");

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Uri savedUri = outputFileResults.getSavedUri();
                        if (savedUri == null) {
                            savedUri = Uri.fromFile(photoFile);
                        }
                        Bundle bundle = new Bundle();
                        bundle.putParcelable("file_uri", savedUri);
                        bundle.putString("temp_file_path", photoFile.getAbsolutePath());
                        bundle.putBoolean("is_video", false);
                        bundle.putString("decibel_text", decibelText.getText().toString());
                        bundle.putString("avg_decibel_text", avgDecibelText.getText().toString());
                        bundle.putString("min_decibel_text", minDecibelText.getText().toString());
                        bundle.putString("max_decibel_text", maxDecibelText.getText().toString());
                        bundle.putString("location_text", locationText.getText().toString());

                        PreviewFragment previewFragment = new PreviewFragment();
                        previewFragment.setArguments(bundle);

                        FragmentTransaction transaction = getParentFragmentManager().beginTransaction();
                        transaction.replace(R.id.fragment_container, previewFragment);
                        transaction.addToBackStack(null);
                        transaction.commit();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Toast.makeText(getContext(), getString(R.string.photo_capture_failed, exception.getMessage()), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private Bitmap imageToBitmap(ImageProxy image) {
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 100, out);
        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private void saveBitmapAsPng(Bitmap bitmap) {
        File cacheDir = requireContext().getCacheDir();
        File tempFile = new File(cacheDir, System.currentTimeMillis() + ".png");

        try (OutputStream out = new FileOutputStream(tempFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            Uri tempUri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".provider", tempFile);

            Bundle bundle = new Bundle();
            bundle.putParcelable("file_uri", tempUri);
            bundle.putBoolean("is_video", false);
            bundle.putString("temp_file_path", tempFile.getAbsolutePath());

            PreviewFragment previewFragment = new PreviewFragment();
            previewFragment.setArguments(bundle);

            FragmentTransaction transaction = getParentFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, previewFragment);
            transaction.addToBackStack(null);
            transaction.commit();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), R.string.image_save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void saveVideoToGallery(Uri videoUri) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, System.currentTimeMillis() + ".mp4");
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES);
        } else {
            values.put(MediaStore.Video.Media.DATA, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getPath());
        }

        ContentResolver resolver = requireContext().getContentResolver();
        Uri uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

        if (uri != null) {
            try (InputStream inputStream = resolver.openInputStream(videoUri);
                 OutputStream outputStream = resolver.openOutputStream(uri)) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = inputStream.read(buf)) > 0) {
                    outputStream.write(buf, 0, len);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void startVideoRecording() {
        if (videoCapture == null) {
            Toast.makeText(getContext(), R.string.camera_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }

        File videoFile = new File(requireContext().getCacheDir(), System.currentTimeMillis() + ".mp4");

        FileOutputOptions fileOutputOptions = new FileOutputOptions.Builder(videoFile).build();

        PendingRecording pendingRecording = videoCapture.getOutput()
                .prepareRecording(requireContext(), fileOutputOptions);

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pendingRecording.withAudioEnabled();
        }

        activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(requireContext()), videoRecordEvent -> {
            if (videoRecordEvent instanceof VideoRecordEvent.Start) {
                Toast.makeText(getContext(), R.string.recording_started, Toast.LENGTH_SHORT).show();
            } else if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                isRecording = false;
                ImageButton recordButton = getView().findViewById(R.id.record_button);
                recordButton.setImageResource(R.drawable.record);
                VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) videoRecordEvent;
                if (!finalizeEvent.hasError()) {
                    Uri outputUri = finalizeEvent.getOutputResults().getOutputUri();
                    Bundle bundle = new Bundle();
                    bundle.putParcelable("file_uri", outputUri);
                    bundle.putString("temp_file_path", videoFile.getAbsolutePath());
                    bundle.putBoolean("is_video", true);
                    bundle.putString("decibel_text", decibelText.getText().toString());
                    bundle.putString("avg_decibel_text", avgDecibelText.getText().toString());
                    bundle.putString("min_decibel_text", minDecibelText.getText().toString());
                    bundle.putString("max_decibel_text", maxDecibelText.getText().toString());
                    bundle.putString("location_text", locationText.getText().toString());

                    PreviewFragment previewFragment = new PreviewFragment();
                    previewFragment.setArguments(bundle);

                    FragmentTransaction transaction = getParentFragmentManager().beginTransaction();
                    transaction.replace(R.id.fragment_container, previewFragment);
                    transaction.addToBackStack(null);
                    transaction.commit();
                } else {
                    Toast.makeText(getContext(), getString(R.string.video_recording_failed, finalizeEvent.getError()), Toast.LENGTH_SHORT).show();
                }
                activeRecording = null;
            }
        });
    }

    private void toggleVideoRecording() {
        if (isRecording) {
            if (activeRecording != null) {
                activeRecording.stop();
            }
        } else {
            ImageButton recordButton = getView().findViewById(R.id.record_button);
            recordButton.setImageResource(R.drawable.pause);
            isRecording = true;
            startVideoRecording();
        }
    }

    private void flipCamera() {
        if (cameraSelector.getLensFacing() == CameraSelector.LENS_FACING_BACK) {
            cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build();
        } else {
            cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build();
        }
        startCamera();
    }

    private void startDecibelUpdates() {
        minDecibel = 100.0;
        maxDecibel = 0.0;
        totalDecibel = 0.0;
        decibelCount = 0;

        File tempAudioFile = new File(requireContext().getCacheDir(), "temp_audio.3gp");
        mRecorder.setMyRecAudioFile(tempAudioFile);

        if (!mRecorder.startRecorder()) {
            Toast.makeText(getContext(), R.string.mic_occupied, Toast.LENGTH_SHORT).show();
            return;
        }

        mUpdateDecibelTask = new Runnable() {
            @Override
            public void run() {
                double amplitude = mRecorder.getMaxAmplitude();
                double db = 20 * Math.log10(amplitude);
                if (Double.isInfinite(db) || Double.isNaN(db)) {
                    db = 0.0;
                }
                decibelText.setText(String.format("%.1f dB", db));

                if (db < minDecibel) minDecibel = db;
                if (db > maxDecibel) maxDecibel = db;
                totalDecibel += db;
                decibelCount++;
                double avgDecibel = totalDecibel / decibelCount;

                minDecibelText.setText(getString(R.string.decibel_camera_min_decibel, minDecibel));
                maxDecibelText.setText(getString(R.string.decibel_camera_max_decibel, maxDecibel));
                avgDecibelText.setText(getString(R.string.decibel_camera_avg_decibel, avgDecibel));

                mHandler.postDelayed(this, 100);
            }
        };
        mHandler.post(mUpdateDecibelTask);
    }

    private void stopDecibelUpdates() {
        if (mHandler != null && mUpdateDecibelTask != null) {
            mHandler.removeCallbacks(mUpdateDecibelTask);
        }
        if (mRecorder != null) {
            mRecorder.stopRecording();
        }
    }

    private void startLocationUpdates() {
        LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER) && !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            new AlertDialog.Builder(requireContext())
                .setMessage(R.string.location_service_required)
                .setPositiveButton(R.string.enable_location_service, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
            locationText.setText(R.string.location_unavailable);
            return;
        }

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationText.setText(R.string.location_unavailable);
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(requireActivity(), location -> {
                    if (location != null) {
                        locationText.setText(getString(R.string.location_format, location.getLatitude(), location.getLongitude()));
                    } else {
                        locationText.setText(R.string.location_fetch_failed);
                    }
                });
    }

    private void stopLocationUpdates() {
        // No longer needed for getLastLocation
    }

    private boolean allPermissionsGranted() {
        for (String permission : permissionsRequired) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                startCamera();
                startLocationUpdates();
                startDecibelUpdates();
            } else {
                Toast.makeText(getContext(), "Permissions not granted. Please enable them in app settings.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (allPermissionsGranted()) {
            startLocationUpdates();
            startDecibelUpdates();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopDecibelUpdates();
        stopLocationUpdates();
    }
}
