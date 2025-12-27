package com.bodekjan.soundmeter;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioAnalysisFragment extends Fragment {
    private LineChart mChart;
    private TextView realTimeDbText, maxDbText, minDbText, avgDbText;
    private float maxDb = 0f, minDb = 120f, avgDb = 0f, dbSum = 0f;
    private int dbCount = 0;
    private ArrayList<Entry> yVals;
    private long savedTime = 0;

    private MyMediaRecorder mRecorder;
    private Thread spectrumAnalysisThread;
    private AtomicBoolean isSpectrumAnalysisEnabled = new AtomicBoolean(false);
    private Button startStopSpectrumButton;
    private Handler handler = new Handler(Looper.getMainLooper());
    private String tempRecordingPath;

    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
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
    }

    private void initializeViews(View view) {
        mChart = view.findViewById(R.id.chart1);
        realTimeDbText = view.findViewById(R.id.real_time_db_text);
        maxDbText = view.findViewById(R.id.max_db_text);
        minDbText = view.findViewById(R.id.min_db_text);
        avgDbText = view.findViewById(R.id.avg_db_text);
        startStopSpectrumButton = view.findViewById(R.id.start_stop_spectrum_button);
        startStopSpectrumButton.setOnClickListener(v -> toggleSpectrumAnalysis());
        resetMetrics();
        setupChart();
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

            if (mRecorder.startRecorder()) {
                isSpectrumAnalysisEnabled.set(true);
                startStopSpectrumButton.setText(getString(R.string.btn_stop_spectrum));

                spectrumAnalysisThread = new Thread(() -> {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                    while (isSpectrumAnalysisEnabled.get()) {
                        try {
                            Thread.sleep(50);
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
                spectrumAnalysisThread.join(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            spectrumAnalysisThread = null;
        }

        if (mRecorder != null) {
            try {
                mRecorder.stopRecording();
            } catch (RuntimeException e) {
                android.util.Log.e("AudioAnalysisFragment", "RuntimeException on stopRecording", e);
            } finally {
                mRecorder = null;
            }
        }

        File tempFile = new File(tempRecordingPath);
        if (tempFile.exists()) {
            tempFile.delete();
        }
        tempRecordingPath = null;

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

    private void resetMetrics() {
        maxDb = 0f;
        minDb = 120f;
        avgDb = 0f;
        dbSum = 0f;
        dbCount = 0;

        if (realTimeDbText != null) {
            realTimeDbText.setText(getString(R.string.db_initial_real_time));
            maxDbText.setText(getString(R.string.db_initial_max));
            minDbText.setText(getString(R.string.db_initial_min));
            avgDbText.setText(getString(R.string.db_initial_avg));
        }

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
        description.setText("分贝趋势");
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
        if (mChart != null) {
            realTimeDbText.setText(String.format(getString(R.string.db_real_time_format), value));

            if (value > maxDb) maxDb = value;
            if (value < minDb) minDb = value;
            dbSum += value;
            dbCount++;
            avgDb = dbSum / dbCount;

            maxDbText.setText(String.format(getString(R.string.db_max_format), maxDb));
            minDbText.setText(String.format(getString(R.string.db_min_format), minDb));
            avgDbText.setText(String.format(getString(R.string.db_avg_format), avgDb));

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
        if (isSpectrumAnalysisEnabled.get()) {
            stopSpectrumAnalysis();
        }
        if (mChart != null) {
            mChart.clear();
            mChart = null;
        }
    }
}
