package com.bodekjan.soundmeter;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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
    private Thread spectrumAnalysisThread;
    private Button exportCsvButton;
    private Button generatePdfButton; // 保留按钮引用但禁用功能
    private Button shareButton;
    private TextView thdValue;
    private TextView snrValue;
    private Button startStopSpectrumButton;
    private NoiseDatabaseHelper dbHelper;

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
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
        spectrumView.setVisibility(View.VISIBLE);

        exportCsvButton = view.findViewById(R.id.export_csv_button);
        generatePdfButton = view.findViewById(R.id.generate_pdf_button);
        shareButton = view.findViewById(R.id.share_button);
        thdValue = view.findViewById(R.id.thd_value);
        snrValue = view.findViewById(R.id.snr_value);
        startStopSpectrumButton = view.findViewById(R.id.start_stop_spectrum_button);

        exportCsvButton.setOnClickListener(v -> exportToCSV());
        generatePdfButton.setOnClickListener(v -> showPdfNotAvailable());
        shareButton.setOnClickListener(v -> shareReport());
        startStopSpectrumButton.setOnClickListener(v -> toggleSpectrumAnalysis());

        // 初始化FFT分析器
        fftAnalyzer = new FFTAnalyzer();
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(requireActivity(),
                new String[]{
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                PERMISSION_REQUEST_CODE);
    }

    private void toggleSpectrumAnalysis() {
        if (isSpectrumAnalysisEnabled.get()) {
            stopSpectrumAnalysis();
            startStopSpectrumButton.setText("开始频谱分析");
        } else {
            startSpectrumAnalysis();
            startStopSpectrumButton.setText("停止频谱分析");
        }
    }

    private void startSpectrumAnalysis() {
        if (!checkPermissions()) {
            Toast.makeText(requireContext(), "需要录音和存储权限", Toast.LENGTH_SHORT).show();
            return;
        }

        fftAnalyzer.startRecording();
        isSpectrumAnalysisEnabled.set(true);

        // 启动频谱分析线程
        spectrumAnalysisThread = new Thread(() -> {
            while (isSpectrumAnalysisEnabled.get()) {
                try {
                    double[] spectrum = fftAnalyzer.getFrequencySpectrum();
                    if (spectrum != null && spectrumView != null && isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            if (isAdded() && spectrumView != null) {
                                spectrumView.setSpectrumData(spectrum);

                                // 计算并显示音频质量指标
                                double[] frequencies = FFTAnalyzer.getFrequencyBins(44100, 512);
                                double thd = AudioQualityAnalyzer.calculateTHD(spectrum, frequencies);
                                double snr = AudioQualityAnalyzer.calculateSNR(spectrum, frequencies);

                                updateQualityMetrics(thd, snr);
                            }
                        });
                    }
                    Thread.sleep(100); // 每100ms更新一次
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
        if (fftAnalyzer != null) {
            fftAnalyzer.stopRecording();
        }
    }

    private void updateQualityMetrics(double thd, double snr) {
        if (thdValue != null && isAdded()) {
            thdValue.setText(String.format("THD: %.2f%%", thd * 100));
        }
        if (snrValue != null && isAdded()) {
            snrValue.setText(String.format("SNR: %.2f dB", snr));
        }
    }

    private void exportToCSV() {
        if (!checkPermissions()) {
            Toast.makeText(requireContext(), "需要存储权限", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                List<NoiseData> noiseDataList = dbHelper.getAllNoiseData();

                // 创建CSV文件
                String fileName = "noise_data_" + System.currentTimeMillis() + ".csv";
                File csvFile = new File(requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName);

                try (FileWriter writer = new FileWriter(csvFile)) {
                    // 写入CSV头部
                    writer.append("ID,DB Value,Latitude,Longitude,Timestamp,Path\n");

                    // 写入数据
                    for (NoiseData data : noiseDataList) {
                        writer.append(String.valueOf(data.getId())).append(",");
                        writer.append(String.valueOf(data.getDbValue())).append(",");
                        writer.append(String.valueOf(data.getLatitude())).append(",");
                        writer.append(String.valueOf(data.getLongitude())).append(",");
                        writer.append(String.valueOf(data.getTimestamp())).append(",");
                        writer.append(data.getPath() != null ? data.getPath() : "").append("\n");
                    }
                }

                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "CSV导出成功: " + csvFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                });
            } catch (IOException e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "CSV导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showPdfNotAvailable() {
        Toast.makeText(requireContext(), "PDF功能需要额外依赖库，当前不可用", Toast.LENGTH_LONG).show();
    }

    private void shareReport() {
        // 这里可以实现分享功能，例如打开分享对话框
        Toast.makeText(requireContext(), "分享功能正在开发中", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() {
        // 停止频谱分析
        stopSpectrumAnalysis();

        // 等待分析线程结束
        if (spectrumAnalysisThread != null && spectrumAnalysisThread.isAlive()) {
            spectrumAnalysisThread.interrupt();
            try {
                spectrumAnalysisThread.join(2000); // 等待最多2秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 释放FFT分析器资源
        if (fftAnalyzer != null) {
            fftAnalyzer.release();
        }

        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(requireContext(), "需要所有权限才能正常工作", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
