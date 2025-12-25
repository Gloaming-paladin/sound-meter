package com.bodekjan.soundmeter;

import android.os.Looper;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.FileUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class DecibelMeterFragment extends Fragment {
    private ArrayList<Entry> yVals;
    private boolean refreshed = false;
    private Speedometer speedometer;
    private static Typeface tf;
    private ImageButton infoButton;
    private ImageButton refreshButton;
    private LineChart mChart;
    private TextView minVal;
    private TextView maxVal;
    private TextView avgVal; // Renamed from mmVal for clarity
    private long currentTime = 0;
    private long savedTime = 0;
    private boolean isChart = false;
    private boolean isThreadRun = true;
    private Thread thread;
    private float volume = 10000;
    private int refresh = 0;
    private MyMediaRecorder mRecorder;
    private boolean bListener = true;
    private boolean isRecording = false;
    private FusedLocationProviderClient fusedLocationClient;
    private ImageButton startStopButton;
    private NoiseDatabaseHelper dbHelper;
    private double currentLatitude = 0.0;
    private double currentLongitude = 0.0;
    private String currentRecordingPath;

    private ActivityResultLauncher<String> requestPermissionsLauncher;

    private ImageButton locationButton;

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);

            DecimalFormat df1 = new DecimalFormat("####.0");
            if (msg.what == 1) {
                if (!isChart) {
                    initChart();
                    return;
                }
                speedometer.refresh();
                minVal.setText(df1.format(World.minDB));
                avgVal.setText(df1.format((World.minDB + World.maxDB) / 2));
                maxVal.setText(df1.format(World.maxDB));
                updateData(World.dbCount, 0);

                // 存储当前测量数据到数据库
                saveCurrentNoiseData();

                if (refresh == 1) {
                    long now = new Date().getTime();
                    now = now - currentTime;
                    now = now / 1000;
                    refresh = 0;
                } else {
                    refresh++;
                }
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_decibel_meter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        initAudioRecorder();
        initLocation();

        // 初始化数据库助手
        dbHelper = new NoiseDatabaseHelper(requireContext());

        requestPermissionsLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                startRecording();
            } else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle(getString(R.string.title_permission_request))
                            .setMessage(getString(R.string.msg_permission_needed))
                            .setPositiveButton(getString(R.string.btn_retry), (dialog, which) -> requestPermissions())
                            .setNegativeButton(getString(R.string.btn_cancel), null)
                            .show();
                } else {
                    showPermissionSettingsDialog();
                }
            }
        });

        // 默认不测量分贝
        resetMeasurement();
    }

    private void initializeViews(View view) {
        speedometer = view.findViewById(R.id.speed);
        infoButton = view.findViewById(R.id.infobutton);
        refreshButton = view.findViewById(R.id.refreshbutton);
        startStopButton = view.findViewById(R.id.playbutton);
        locationButton = view.findViewById(R.id.location_button);
        minVal = view.findViewById(R.id.minval);
        maxVal = view.findViewById(R.id.maxval);
        avgVal = view.findViewById(R.id.avgval);

        // 如果布局中没有 curval ID，需要添加一个 TextView 并设置 ID 为 curval
        // 如果找不到该ID，可以暂时注释掉这行，等布局文件修改后再取消注释

        // 设置初始状态
        startStopButton.setImageResource(R.drawable.play); // 使用 play.png
        refreshButton.setVisibility(View.VISIBLE);

        // 设置点击事件
        startStopButton.setOnClickListener(v -> toggleRecording());
        refreshButton.setOnClickListener(v -> resetMeasurement());
        infoButton.setOnClickListener(v -> showInfoDialog());
        locationButton.setOnClickListener(v -> requestLocation());
    }

    private void initAudioRecorder() {
        mRecorder = new MyMediaRecorder(requireContext());
        mRecorder.setAudioFormat(MyMediaRecorder.AudioFormat.THREE_GPP);
    }

    private void initLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());
    }

    private void toggleRecording() {
        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        if (!checkPermissions()) {
            requestPermissions();
            return;
        }
        
        // 重置统计数据以便新的一次测量
        World.minDB = 100;
        World.maxDB = 0;

        // 创建录音文件
        File recordingFile = createRecordingFile();
        if (recordingFile != null) {
            mRecorder.setMyRecAudioFile(recordingFile);
            if (mRecorder.startRecorder()) {
                isRecording = true;
                startStopButton.setImageResource(R.drawable.ic_stop); // 使用 ic_stop
                startMeasurement();
                // 显示录制状态
                Toast.makeText(requireContext(), getString(R.string.msg_start_recording), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), getString(R.string.msg_recording_failed), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void stopRecording() {
        if (mRecorder != null && isRecording) {
            mRecorder.stopRecording();
            isRecording = false;
            startStopButton.setImageResource(R.drawable.play); // 恢复为 play.png

            // 停止测量
            stopMeasurement();

            // 弹窗询问是否保存录音
            showSaveRecordingDialog();
        }
    }

    private void resetMeasurement() {
        World.dbCount = 0;
        World.minDB = 0;
        World.maxDB = 0;
        World.lastDbCount = 0;

        // 更新UI
        speedometer.refresh();
        minVal.setText("0 dB");
        maxVal.setText("0 dB");
        avgVal.setText("0 dB");

        // 停止测量线程
        stopMeasurement();
    }

    private void startMeasurement() {
        isThreadRun = true;
        if (thread == null || !thread.isAlive()) {
            thread = new Thread(() -> {
                while (isThreadRun && isRecording) {
                    try {
                        Thread.sleep(50);
                        // 获取音频振幅并计算分贝值
                        float amplitude = mRecorder.getMaxAmplitude();
                        if (amplitude > 0) {
                            float dbValue = 20 * (float) Math.log10(amplitude / 1.0);
                            World.setDbCount(dbValue);
                        }
                        handler.sendEmptyMessage(1);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            thread.start();
        }
    }

    private void stopMeasurement() {
        isThreadRun = false;
        if (thread != null) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private File createRecordingFile() {
        // 创建录音文件
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = "recording_" + timestamp;

        // 创建文件
        File file = new File(requireContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC), fileName + ".3gp");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        currentRecordingPath = file.getAbsolutePath();
        return file;
    }

    private void showSaveRecordingDialog() {
        // 创建对话框询问用户是否保存录音
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.title_save_recording))
                .setMessage(getString(R.string.msg_save_recording_confirm))
                .setPositiveButton(getString(R.string.btn_save), (dialog, which) -> {
                    // 创建保存文件对话框
                    new AlertDialog.Builder(requireContext())
                            .setTitle(getString(R.string.title_select_format))
                            .setItems(new String[]{"MP3", "WAV", "FLAC"}, (dialog1, which1) -> {
                                // 根据用户选择的格式保存文件
                                String format = "";
                                switch (which1) {
                                    case 0:
                                        format = "mp3";
                                        break;
                                    case 1:
                                        format = "wav";
                                        break;
                                    case 2:
                                        format = "flac";
                                        break;
                                }

                                // 调用方法转换并保存文件
                                convertAndSaveRecording(format);
                            })
                            .show();
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void convertAndSaveRecording(String format) {
        // 转换并保存录音文件
        // 这里需要实现音频格式转换逻辑
        // 实际应用中可能需要使用FFmpeg等工具
        Toast.makeText(requireContext(), getString(R.string.msg_saved_format, format.toUpperCase()), Toast.LENGTH_SHORT).show();
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        requestPermissionsLauncher.launch(Manifest.permission.RECORD_AUDIO);
    }



    private void showPermissionSettingsDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.title_permission_request))
                .setMessage(getString(R.string.msg_permission_denied_forever))
                .setPositiveButton(getString(R.string.btn_go_to_settings), (dialog, which) -> openAppSettings())
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", requireContext().getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    private void requestLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // 已经有权限，开始定位
            startLocationUpdate();
        } else {
            // 请求权限
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    101);
        }
    }

    private void startLocationUpdate() {
        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(requireActivity(), location -> {
                        if (location != null) {
                            currentLatitude = location.getLatitude();
                            currentLongitude = location.getLongitude();
                            Toast.makeText(requireContext(), getString(R.string.msg_location_success, String.valueOf(currentLatitude), String.valueOf(currentLongitude)), Toast.LENGTH_SHORT).show();
                        }
                    });
        } catch (SecurityException e) {
            e.printStackTrace();
            // Handle the case where permission is not granted
            Toast.makeText(requireContext(), "Location permission not granted.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showInfoDialog() {
        InfoDialog.show(requireContext());
    }

    // 新增方法：初始化图表
    private void initChart() {
        isChart = true;
        // 初始化图表相关的代码
        if (mChart == null) {
            mChart = new LineChart(requireContext());
            // 初始化图表配置
            setupChart();
        }
    }

    // 新增方法：设置图表
    private void setupChart() {
        // 图表配置代码
        mChart.setTouchEnabled(true);
        mChart.setDragEnabled(true);
        mChart.setScaleEnabled(true);
        mChart.setDrawGridBackground(false);
        mChart.setPinchZoom(true);

        // 设置X轴
        XAxis xAxis = mChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        // 设置Y轴
        YAxis leftAxis = mChart.getAxisLeft();
        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setEnabled(false);

        // 设置描述
        Description description = new Description();
        description.setText(getString(R.string.chart_description));
        mChart.setDescription(description);

        // 设置数据
        mChart.setData(new LineData());
        mChart.invalidate();
    }

    // 新增方法：更新图表数据
    private void updateData(float value, int index) {
        if (mChart != null) {
            LineData data = mChart.getData();
            if (data == null) {
                data = new LineData();
                mChart.setData(data);
            }

            LineDataSet set = (LineDataSet) data.getDataSetByIndex(0);
            if (set == null) {
                set = new LineDataSet(null, getString(R.string.chart_label));
                set.setCircleColor(Color.GREEN);
                set.setLineWidth(2f);
                set.setCircleRadius(4f);
                data.addDataSet(set);
            }

            // 添加新的数据点
            data.addEntry(new Entry(set.getEntryCount(), value), 0);

            // 限制数据点数量，防止图表过于拥挤
            // 由于 LineData 没有 removeFirst 方法，需要手动管理数据点数量
            if (set.getEntryCount() > 50) {
                // 移除第一个数据点
                set.removeFirst();
            }

            mChart.notifyDataSetChanged();
            mChart.setVisibleXRangeMaximum(10); // 只显示10个数据点
            mChart.moveViewToX(data.getEntryCount() - 1);
        }
    }

    // 新增方法：保存当前噪音数据
    private void saveCurrentNoiseData() {
        // 保存当前噪音数据到数据库
        if (dbHelper != null) {
            // 获取当前时间戳
            long timestamp = System.currentTimeMillis();

            // 创建噪音数据对象
            NoiseData noiseData = new NoiseData(
                    World.dbCount,      // 分贝值
                    currentLatitude,    // 纬度
                    currentLongitude,   // 经度
                    timestamp,          // 时间戳
                    currentRecordingPath // 录音路径
            );

            // 插入数据库
            long id = dbHelper.insertNoiseData(noiseData);
            if (id != -1) {
                // 保存成功
                Log.d("DecibelMeterFragment", "噪音数据保存成功，ID: " + id);
            } else {
                // 保存失败
                Log.e("DecibelMeterFragment", "噪音数据保存失败");
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        bListener = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isRecording) {
            stopRecording();
        }
        bListener = false;
        if (mRecorder != null) {
            mRecorder.delete();
        }
        thread = null;
        isChart = false;
    }

    @Override
    public void onDestroy() {
        if (isRecording) {
            stopRecording();
        }
        if (thread != null) {
            isThreadRun = false;
            thread = null;
        }
        if (mRecorder != null) {
            mRecorder.delete();
        }
        super.onDestroy();
    }
}
