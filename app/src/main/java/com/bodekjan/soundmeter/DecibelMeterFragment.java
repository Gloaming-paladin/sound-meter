// DecibelMeterFragment.java
package com.bodekjan.soundmeter;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
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
    private TextView mmVal;
    private TextView curVal;
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

    private final Handler handler = new Handler() {
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
                mmVal.setText(df1.format((World.minDB + World.maxDB) / 2));
                maxVal.setText(df1.format(World.maxDB));
                curVal.setText(df1.format(World.dbCount));
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

        // 初始化字体
        if (tf == null) {
            tf = Typeface.createFromAsset(requireActivity().getAssets(), "fonts/Let_s go Digital Regular.ttf");
        }

        // 初始化视图
        initializeViews(view);

        // 初始化数据库
        dbHelper = new NoiseDatabaseHelper(requireContext());

        // 检查录音权限
        if (!isRecordAudioPermissionGranted()) {
            return;
        }

        // 初始化位置服务
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // 启动录音
        File file = FileUtil.createFile(requireContext(), "temp.amr");
        if (file != null) {
            startRecord(file);
        } else {
            Toast.makeText(requireContext(), getString(R.string.activity_recFileErr), Toast.LENGTH_LONG).show();
        }
    }

    private void initializeViews(View view) {
        minVal = view.findViewById(R.id.minval);
        minVal.setTypeface(tf);
        mmVal = view.findViewById(R.id.mmval);
        mmVal.setTypeface(tf);
        maxVal = view.findViewById(R.id.maxval);
        maxVal.setTypeface(tf);
        curVal = view.findViewById(R.id.curval);
        curVal.setTypeface(tf);
        infoButton = view.findViewById(R.id.infobutton);
        infoButton.setOnClickListener(v -> showInfoDialog());
        refreshButton = view.findViewById(R.id.refreshbutton);
        refreshButton.setOnClickListener(v -> refreshData());
        speedometer = view.findViewById(R.id.speed);
        startStopButton = view.findViewById(R.id.playbutton);
        startStopButton.setOnClickListener(v -> toggleRecording());

        mRecorder = new MyMediaRecorder();
    }

    private void showInfoDialog() {
        InfoDialog.Builder builder = new InfoDialog.Builder(requireActivity());
        builder.setMessage(getString(R.string.activity_infobull));
        builder.setTitle(getString(R.string.activity_infotitle));
        builder.setNegativeButton(getString(R.string.activity_infobutton),
                (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    private void refreshData() {
        refreshed = true;
        World.minDB = 100;
        World.dbCount = 0;
        World.lastDbCount = 0;
        World.maxDB = 0;
        initChart();
    }

    private void toggleRecording() {
        if (isRecording) {
            stopRecording();
            startStopButton.setImageResource(R.drawable.play);
        } else {
            startRecording();
            startStopButton.setImageResource(R.drawable.pause);
        }
    }

    private void startRecording() {
        // 生成录音文件名
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = "recording_" + timestamp + ".3gp";

        // 创建录音文件
        File recordingFile = FileUtil.createFile(requireContext(), fileName);
        if (recordingFile == null) {
            Toast.makeText(requireContext(), "无法创建录音文件", Toast.LENGTH_SHORT).show();
            return;
        }

        mRecorder.setAudioFormat(MyMediaRecorder.AudioFormat.THREE_GPP);
        mRecorder.setMyRecAudioFile(recordingFile);

        if (mRecorder.startRecorder()) {
            isRecording = true;
            startStopButton.setImageResource(R.drawable.pause);
            startListenAudio();
        } else {
            Toast.makeText(requireContext(), getString(R.string.activity_recStartErr), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        mRecorder.stopRecording();
        isRecording = false;
        startStopButton.setImageResource(R.drawable.play);
    }

    private void startListenAudio() {
        thread = new Thread(() -> {
            while (isThreadRun) {
                try {
                    if (bListener) {
                        volume = mRecorder.getMaxAmplitude();
                        if (volume > 0 && volume < 1000000) {
                            World.setDbCount(20 * (float) (Math.log10(volume)));
                            Message message = new Message();
                            message.what = 1;
                            handler.sendMessage(message);
                        }
                    }
                    if (refreshed) {
                        Thread.sleep(1200);
                        refreshed = false;
                    } else {
                        Thread.sleep(200);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    bListener = false;
                }
            }
        });
        thread.start();
    }

    private void startRecord(File fFile) {
        try {
            mRecorder.setMyRecAudioFile(fFile);
            if (mRecorder.startRecorder()) {
                startListenAudio();
            } else {
                Toast.makeText(requireContext(), getString(R.string.activity_recStartErr), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), getString(R.string.activity_recBusyErr), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void saveCurrentNoiseData() {
        // 每5秒保存一次数据，避免数据库写入过于频繁
        if (System.currentTimeMillis() % 5000 < 200) {
            new Thread(() -> {
                NoiseData noiseData = new NoiseData(
                        World.dbCount,
                        currentLatitude,
                        currentLongitude,
                        System.currentTimeMillis(),
                        currentRecordingPath
                );
                if (dbHelper != null) {
                    dbHelper.insertNoiseData(noiseData);
                }
            }).start();
        }
    }

    private void updateData(float val, long time) {
        if (mChart == null) {
            return;
        }
        if (mChart.getData() != null &&
                mChart.getData().getDataSetCount() > 0) {
            LineDataSet set1 = (LineDataSet) mChart.getData().getDataSetByIndex(0);
            set1.setValues(yVals);
            Entry entry = new Entry(savedTime, val);
            set1.addEntry(entry);
            if (set1.getEntryCount() > 200) {
                set1.removeFirst();
                set1.setDrawFilled(false);
            }
            mChart.getData().notifyDataChanged();
            mChart.notifyDataSetChanged();
            mChart.invalidate();
            savedTime++;
        }
    }

    private void initChart() {
        if (mChart != null) {
            if (mChart.getData() != null &&
                    mChart.getData().getDataSetCount() > 0) {
                savedTime++;
                isChart = true;
            }
        } else {
            currentTime = new Date().getTime();
            mChart = requireView().findViewById(R.id.chart1);
            mChart.setViewPortOffsets(50, 20, 5, 60);
            Description desc = new Description();
            desc.setText("");
            mChart.setDescription(desc);
            mChart.setTouchEnabled(true);
            mChart.setDragEnabled(false);
            mChart.setScaleEnabled(true);
            mChart.setPinchZoom(false);
            mChart.setDrawGridBackground(false);
            XAxis x = mChart.getXAxis();
            x.setLabelCount(8, false);
            x.setEnabled(true);
            x.setTypeface(tf);
            x.setTextColor(Color.GREEN);
            x.setPosition(XAxis.XAxisPosition.BOTTOM);
            x.setDrawGridLines(true);
            x.setAxisLineColor(Color.GREEN);
            YAxis y = mChart.getAxisLeft();
            y.setLabelCount(6, false);
            y.setTextColor(Color.GREEN);
            y.setTypeface(tf);
            y.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);
            y.setDrawGridLines(false);
            y.setAxisLineColor(Color.GREEN);
            y.setAxisMinValue(0);
            y.setAxisMaxValue(120);
            mChart.getAxisRight().setEnabled(true);
            yVals = new ArrayList<>();
            yVals.add(new Entry(0, 0));
            LineDataSet set1 = new LineDataSet(yVals, "DataSet 1");
            set1.setValueTypeface(tf);
            set1.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            set1.setCubicIntensity(0.02f);
            set1.setDrawFilled(true);
            set1.setDrawCircles(false);
            set1.setCircleColor(Color.GREEN);
            set1.setHighLightColor(Color.rgb(244, 117, 117));
            set1.setColor(Color.GREEN);
            set1.setFillColor(Color.GREEN);
            set1.setFillAlpha(100);
            set1.setDrawHorizontalHighlightIndicator(false);
            set1.setFillFormatter((dataSet, dataProvider) -> -10);
            LineData data;
            if (mChart.getData() != null &&
                    mChart.getData().getDataSetCount() > 0) {
                data = mChart.getLineData();
                data.clearValues();
                data.removeDataSet(0);
                data.addDataSet(set1);
            } else {
                data = new LineData(set1);
            }

            data.setValueTextSize(9f);
            data.setDrawValues(false);
            mChart.setData(data);
            mChart.getLegend().setEnabled(false);
            mChart.animateXY(2000, 2000);
            mChart.invalidate();
            isChart = true;
        }
    }

    private boolean isRecordAudioPermissionGranted() {
        int AUDIO_RECORD_REQUEST_CODE = 1;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED) {
                return true;
            } else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    Toast.makeText(requireContext(),
                            "App required access to audio", Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_RECORD_REQUEST_CODE);
                return false;
            }
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            File file = FileUtil.createFile(requireContext(), "temp.amr");
            if (file != null) {
                startRecord(file);
            } else {
                Toast.makeText(requireContext(), getString(R.string.activity_recFileErr), Toast.LENGTH_LONG).show();
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
