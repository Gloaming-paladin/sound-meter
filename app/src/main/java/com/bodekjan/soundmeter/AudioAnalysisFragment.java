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

public class AudioAnalysisFragment extends Fragment {
    private SpectrumView spectrumView;
    private FFTAnalyzer fftAnalyzer;
    private AtomicBoolean isSpectrumAnalysisEnabled = new AtomicBoolean(false);
    private volatile boolean isAnalyzingFile = false;
    private Thread spectrumAnalysisThread;
    private Button exportCsvButton;
    private Button generatePdfButton;
    private Button shareButton;
    private Button analyzeLocalFileButton;
    private TextView thdValue;
    private TextView snrValue;
    private TextView centroidValue;
    private TextView bandwidthValue;
    private Button startStopSpectrumButton;
    private NoiseDatabaseHelper dbHelper;
    private Handler handler = new Handler(Looper.getMainLooper());

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
                    if (!isGranted) {
                        Toast.makeText(requireContext(), getString(R.string.msg_recording_permission_needed), Toast.LENGTH_SHORT).show();
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
        if (!checkPermissions()) {
            requestPermissions();
        }
    }

    private void initializeViews(View view) {
        spectrumView = view.findViewById(R.id.spectrum_view);
        exportCsvButton = view.findViewById(R.id.export_csv_button);
        generatePdfButton = view.findViewById(R.id.generate_pdf_button);
        shareButton = view.findViewById(R.id.share_button);
        analyzeLocalFileButton = view.findViewById(R.id.analyze_local_file_button);
        thdValue = view.findViewById(R.id.thd_value);
        snrValue = view.findViewById(R.id.snr_value);
        centroidValue = view.findViewById(R.id.centroid_value);
        bandwidthValue = view.findViewById(R.id.bandwidth_value);
        startStopSpectrumButton = view.findViewById(R.id.start_stop_spectrum_button);

        exportCsvButton.setOnClickListener(v -> exportToCSV());
        generatePdfButton.setOnClickListener(v -> showPdfNotAvailable());
        shareButton.setOnClickListener(v -> shareReport());
        startStopSpectrumButton.setOnClickListener(v -> toggleSpectrumAnalysis());
        analyzeLocalFileButton.setOnClickListener(v -> openFilePicker());

        resetMetrics();
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

    private void toggleSpectrumAnalysis() {
        if (isAnalyzingFile) {
            Toast.makeText(requireContext(), "Cannot start real-time analysis while analyzing a file.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isSpectrumAnalysisEnabled.get()) {
            stopSpectrumAnalysis();
        } else {
            startSpectrumAnalysis();
        }
    }

    private void startSpectrumAnalysis() {
        if (!checkPermissions()) {
            Toast.makeText(requireContext(), getString(R.string.msg_recording_permission_needed), Toast.LENGTH_SHORT).show();
            return;
        }

        fftAnalyzer = new FFTAnalyzer();
        if (!fftAnalyzer.startRecording()) {
            Toast.makeText(requireContext(), "Failed to start recording. Please check microphone permissions or if another app is using the microphone.", Toast.LENGTH_LONG).show();
            fftAnalyzer.release();
            fftAnalyzer = null;
            return;
        }
        isSpectrumAnalysisEnabled.set(true);
        startStopSpectrumButton.setText(getString(R.string.btn_stop_spectrum));

        spectrumAnalysisThread = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            while (isSpectrumAnalysisEnabled.get()) {
                double[] spectrum = fftAnalyzer.getFrequencySpectrum();
                handler.post(() -> {
                    if (isAdded()) {
                        if (spectrum != null) {
                            spectrumView.setSpectrumData(spectrum);
                            updateMetricsFromSpectrum(spectrum);
                        } else {
                            resetMetrics();
                        }
                    }
                });
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        spectrumAnalysisThread.start();
    }

    private void stopSpectrumAnalysis() {
        isSpectrumAnalysisEnabled.set(false);
        if (spectrumAnalysisThread != null) {
            spectrumAnalysisThread.interrupt();
        }
        if (fftAnalyzer != null) {
            fftAnalyzer.release();
            fftAnalyzer = null;
        }
        handler.post(() -> {
            if (isAdded()) {
                startStopSpectrumButton.setText(getString(R.string.btn_start_spectrum));
                resetMetrics();
            }
        });
    }

    private void analyzeAudioFile(Uri audioFileUri) {
        isAnalyzingFile = true;
        spectrumAnalysisThread = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            FFTAnalyzer fileAnalyzer = null; // Use a local analyzer for this task
            try {
                fileAnalyzer = new FFTAnalyzer(requireContext(), audioFileUri);
                while (isAnalyzingFile) { // Loop can be stopped externally
                    double[] spectrum = fileAnalyzer.getFrequencySpectrum();
                    if (spectrum == null) {
                        break; // End of file
                    }
                    handler.post(() -> {
                        if (isAdded()) {
                            spectrumView.setSpectrumData(spectrum);
                            updateMetricsFromSpectrum(spectrum);
                        }
                    });
                    try {
                        Thread.sleep(50); // Shorter delay for file analysis
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                // If we reached here, analysis is "complete" (either by finishing file or interruption)
                handler.post(() -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Analysis complete", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                handler.post(() -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Failed to analyze audio file", Toast.LENGTH_SHORT).show();
                    }
                });
            } finally {
                if (fileAnalyzer != null) {
                    fileAnalyzer.release();
                }
                // Reset state and UI in all cases (success, failure, interruption)
                handler.post(() -> {
                    isAnalyzingFile = false;
                    if (isAdded()) {
                        resetMetrics();
                    }
                });
            }
        });
        spectrumAnalysisThread.start();
    }

    private void updateMetricsFromSpectrum(double[] spectrum) {
        double[] frequencies = FFTAnalyzer.getFrequencyBins(44100, FFTAnalyzer.FFT_SIZE);
        double thd = AudioQualityAnalyzer.calculateTHD(spectrum, frequencies);
        double snr = AudioQualityAnalyzer.calculateSNR(spectrum, frequencies);
        double centroid = AudioQualityAnalyzer.calculateSpectralCentroid(spectrum, frequencies);
        double bandwidth = AudioQualityAnalyzer.calculateSpectralBandwidth(spectrum, frequencies, centroid);
        updateQualityMetricsUI(thd, snr, centroid, bandwidth);
    }

    private void updateQualityMetricsUI(double thd, double snr, double centroid, double bandwidth) {
        thdValue.setText(String.format("总谐波失真: %.2f%%", thd * 100));
        snrValue.setText(String.format("信噪比: %.2f dB", snr));
        centroidValue.setText(String.format("质心: %.0f Hz", centroid));
        bandwidthValue.setText(String.format("带宽: %.0f Hz", bandwidth));
    }

    private void resetMetrics() {
        thdValue.setText("总谐波失真: --");
        snrValue.setText("信噪比: --");
        centroidValue.setText("质心: --");
        bandwidthValue.setText("带宽: --");
        if (spectrumView != null) {
            spectrumView.clearSpectrum();
        }
    }

    private void exportToCSV() {
        // ... (rest of the code is unchanged)
    }

    private void showPdfNotAvailable() {
        Toast.makeText(requireContext(), getString(R.string.msg_pdf_unavailable), Toast.LENGTH_LONG).show();
    }

    private void shareReport() {
        // ... (rest of the code is unchanged)
    }

    @Override
    public void onDestroy() {
        isSpectrumAnalysisEnabled.set(false);
        isAnalyzingFile = false;
        if (spectrumAnalysisThread != null) {
            spectrumAnalysisThread.interrupt();
        }
        if (fftAnalyzer != null) {
            fftAnalyzer.release();
            fftAnalyzer = null;
        }
        super.onDestroy();
    }


}
