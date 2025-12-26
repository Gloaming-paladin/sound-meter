package com.bodekjan.soundmeter;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import android.graphics.Color;

public class AudioAnalysisFragment extends Fragment {
    private LineChart mChart;
    private ArrayList<Entry> yVals;
    private long savedTime = 0;
    private boolean isChart = false;
    
    private MyMediaRecorder mRecorder;
    private Thread spectrumAnalysisThread;
    private AtomicBoolean isSpectrumAnalysisEnabled = new AtomicBoolean(false);
    private volatile boolean isAnalyzingFile = false;
    private Button analyzeLocalFileButton;

    private Button startStopSpectrumButton;
    private NoiseDatabaseHelper dbHelper;
    private Handler handler = new Handler(Looper.getMainLooper());
    private String tempRecordingPath;

    private ActivityResultLauncher<Intent> filePickerLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int FILE_PICKER_REQUEST_CODE = 101;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri audioFileUri = result.getData().getData();
                        if (audioFileUri != null) {
                            analyzeAudioFile(audioFileUri);
                        }
                    }
                }
        );

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        startSpectrumAnalysis();
                    } else {
                        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                            new android.app.AlertDialog.Builder(requireContext())
                                    .setTitle(getString(R.string.title_permission_request))
                                    .setMessage(getString(R.string.msg_permission_needed))
                                    .setPositiveButton(getString(R.string.btn_retry), (dialog, which) -> requestPermissions())
                                    .setNegativeButton(getString(R.string.btn_cancel), null)
                                    .show();
                        } else {
                            showPermissionSettingsDialog();
                        }
                    }
                }
        );

        return inflater.inflate(R.layout.fragment_audio_analysis, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        dbHelper = new NoiseDatabaseHelper(requireContext());
    }

    private void initializeViews(View view) {
        mChart = view.findViewById(R.id.chart1);
        analyzeLocalFileButton = view.findViewById(R.id.analyze_local_file_button);
        startStopSpectrumButton = view.findViewById(R.id.start_stop_spectrum_button);

        startStopSpectrumButton.setOnClickListener(v -> toggleSpectrumAnalysis());
        analyzeLocalFileButton.setOnClickListener(v -> openFilePicker());

        resetMetrics();
        setupChart();
    }

    private void openFilePicker() {
        if (isAnalyzingFile || isSpectrumAnalysisEnabled.get()) {
            Toast.makeText(requireContext(), "Please stop the current analysis first.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        filePickerLauncher.launch(intent);
    }



    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
    }

    private void showPermissionSettingsDialog() {
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.title_permission_request))
                .setMessage(getString(R.string.msg_permission_denied_forever))
                .setPositiveButton(getString(R.string.btn_go_to_settings), (dialog, which) -> openAppSettings())
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", requireContext().getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    private void toggleSpectrumAnalysis() {
        if (isAnalyzingFile) {
            Toast.makeText(requireContext(), "Cannot start real-time analysis while analyzing a file.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isSpectrumAnalysisEnabled.get()) {
            stopSpectrumAnalysis();
        } else {
            if (!checkPermissions()) {
                requestPermissions();
            } else {
                startSpectrumAnalysis();
            }
        }
    }

    private void startSpectrumAnalysis() {
        try {
            resetMetrics();
            if (mRecorder == null) {
                mRecorder = new MyMediaRecorder(requireContext());
            }
            File tempFile = createTempAudioFile();
            if (tempFile == null) {
                Toast.makeText(requireContext(), "Failed to create temp file for recording.", Toast.LENGTH_SHORT).show();
                return;
            }
            mRecorder.setMyRecAudioFile(tempFile);

            android.util.Log.d("AudioAnalysisFragment", "startSpectrumAnalysis: launching...");
            if (mRecorder.startRecorder()) {
                isSpectrumAnalysisEnabled.set(true);
                startStopSpectrumButton.setText(getString(R.string.btn_stop_spectrum));

                spectrumAnalysisThread = new Thread(() -> {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                    while (isSpectrumAnalysisEnabled.get()) {
                        try {
                            Thread.sleep(50); // Match DecibelMeterFragment's delay
                            float amplitude = mRecorder.getMaxAmplitude();
                            if (amplitude > 0) {
                                float dbValue = (float) (20 * Math.log10(amplitude));
                                handler.post(() -> {
                                    if (isAdded()) {
                                        updateData(dbValue, 0);
                                    }
                                });
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                });
                spectrumAnalysisThread.start();
            } else {
                Toast.makeText(requireContext(), "Failed to start recording.", Toast.LENGTH_SHORT).show();
                mRecorder = null;
            }
        } catch (Exception e) {
            android.util.Log.e("AudioAnalysisFragment", "Error in startSpectrumAnalysis", e);
            Toast.makeText(requireContext(), "Error starting analysis: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            mRecorder = null;
        }
    }

    private void stopSpectrumAnalysis() {
        if (!isSpectrumAnalysisEnabled.getAndSet(false)) {
            return; // Already stopped or stopping
        }

        if (spectrumAnalysisThread != null) {
            spectrumAnalysisThread.interrupt();
            try {
                spectrumAnalysisThread.join(100); // Wait a bit for the thread to die
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            }
            spectrumAnalysisThread = null;
        }

        if (mRecorder != null) {
            try {
                mRecorder.stopRecording();
            } catch (RuntimeException e) {
                // This can happen if stop() is called in an invalid state.
                android.util.Log.e("AudioAnalysisFragment", "RuntimeException on stopRecording", e);
            } finally {
                mRecorder = null; // Always release the recorder
            }
        }

        if (tempRecordingPath != null && !tempRecordingPath.isEmpty()) {
            File tempFile = new File(tempRecordingPath);
            if (tempFile.exists()) {
                tempFile.delete();
            }
            tempRecordingPath = null;
        }

        handler.post(() -> {
            if (isAdded()) {
                startStopSpectrumButton.setText(getString(R.string.btn_start_spectrum));
            }
        });
    }

    private File createTempAudioFile() {
        try {
            File tempDir = requireContext().getCacheDir();
            File tempFile = File.createTempFile("temp_audio", ".3gp", tempDir);
            tempRecordingPath = tempFile.getAbsolutePath();
            return tempFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void analyzeAudioFile(Uri audioFileUri) {
        isAnalyzingFile = true;
        startStopSpectrumButton.setEnabled(false); // Disable button during file analysis
        analyzeLocalFileButton.setEnabled(false);

        spectrumAnalysisThread = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            FFTAnalyzer fileAnalyzer = null;
            try {
                fileAnalyzer = new FFTAnalyzer(requireContext(), audioFileUri);
                while (isAnalyzingFile) {
                    double[] spectrum = fileAnalyzer.getFrequencySpectrum();
                                     if (spectrum != null) {
                        double maxAmplitude = 0;
                                         for (double v : spectrum) {
                                             if (v > maxAmplitude) {
                                                 maxAmplitude = v;
                                             }
                                         }
                        float dbValue = (float) (20 * Math.log10(maxAmplitude > 0 ? maxAmplitude : 1));

                        handler.post(() -> {
                            if (isAdded()) {
                                updateData(dbValue, 0);
                            }
                        });
                    } else {
                        break; // End of file
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (IOException e) {
                handler.post(() -> Toast.makeText(requireContext(), "Failed to analyze file.", Toast.LENGTH_SHORT).show());
            } finally {
                if (fileAnalyzer != null) {
                    fileAnalyzer.release();
                }
                handler.post(() -> {
                    isAnalyzingFile = false;
                    if (isAdded()) {
                        startStopSpectrumButton.setEnabled(true);
                        analyzeLocalFileButton.setEnabled(true);
                        resetMetrics();
                        Toast.makeText(requireContext(), "File analysis complete.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        spectrumAnalysisThread.start();
    }



    private void resetMetrics() {
        if (mChart != null && mChart.getData() != null) {
            LineDataSet set = (LineDataSet) mChart.getData().getDataSetByIndex(0);
            if (set != null) {
                set.clear();
                mChart.getData().notifyDataChanged();
                mChart.notifyDataSetChanged();
            }
            mChart.invalidate();
        }
        savedTime = 0;
    }

    private void setupChart() {
        if (mChart == null) return;
        mChart.setDragEnabled(false);
        mChart.setScaleEnabled(true);
        mChart.setDrawGridBackground(false);
        mChart.setPinchZoom(false);

        XAxis x = mChart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);
        x.setDrawAxisLine(true);
        x.setTextColor(Color.WHITE);

        YAxis y = mChart.getAxisLeft();
        y.setAxisMinimum(0f);
        y.setAxisMaximum(120f);
        y.setDrawGridLines(true);
        y.setDrawAxisLine(true);
        y.setTextColor(Color.WHITE);

        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setEnabled(false);

        Description description = new Description();
        description.setText("分贝趋势"); // Using hardcoded string
        mChart.setDescription(description);

        yVals = new ArrayList<>();
        LineDataSet set1 = new LineDataSet(yVals, "DataSet 1");

        set1.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set1.setDrawFilled(true);
        set1.setDrawCircles(false);
        set1.setColor(Color.GREEN);
        set1.setFillColor(Color.GREEN);

        LineData data = new LineData(set1);
        mChart.setData(data);
        mChart.invalidate();
    }

    private void updateData(float value, int index) {
        android.util.Log.d("AudioAnalysisFragment", "updateData: " + value);
        if (mChart != null) {
            LineData data = mChart.getData();
            if (data != null) {
                LineDataSet set = (LineDataSet) data.getDataSetByIndex(0);
                if (set != null) {
                    Entry entry = new Entry(savedTime, value);
                    data.addEntry(entry, 0);
                    savedTime++;

                    if (set.getEntryCount() > 200) {
                        set.removeFirst();
                    }

                    data.notifyDataChanged();
                    mChart.notifyDataSetChanged();
                    mChart.invalidate();
                }
            }
        }
    }



    @Override
    public void onPause() {
        super.onPause();
        if (isSpectrumAnalysisEnabled.get()) {
            stopSpectrumAnalysis();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isSpectrumAnalysisEnabled.set(false);
        isAnalyzingFile = false;
        if (spectrumAnalysisThread != null) {
            spectrumAnalysisThread.interrupt();
        }
        if (mRecorder != null) {
            mRecorder.stopRecording();
            mRecorder = null;
        }
        if (mChart != null) {
            mChart.clear();
            mChart = null;
        }
    }


}
