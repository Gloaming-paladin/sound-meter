package com.bodekjan.soundmeter;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private List<NoiseData> noiseDataList;
    private NoiseDatabaseHelper dbHelper;
    private Button refreshButton;
    private Button clearAllButton;

    private ActivityResultLauncher<String> requestPermissionLauncher;
    private String csvContentToSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        initViews();
        dbHelper = new NoiseDatabaseHelper(this);
        loadHistoryData();

        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                if (csvContentToSave != null) {
                    saveCsvToFile(csvContentToSave);
                }
            } else {
                Toast.makeText(this, "存储权限被拒绝，无法导出文件", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.history_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_export) {
            exportHistoryToCsv();
            return true;
        } else if (itemId == R.id.action_open_folder) {
            openDownloadsFolder();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openDownloadsFolder() {
        // First, try the recommended MediaStore approach for viewing the downloads collection
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(MediaStore.Downloads.EXTERNAL_CONTENT_URI);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
            return;
        }

        // As a fallback, open a general file picker. This is not ideal, but is widely supported.
        // It will allow the user to navigate to the Downloads folder.
        Intent fallbackIntent = new Intent(Intent.ACTION_GET_CONTENT);
        fallbackIntent.setType("*/*");
        fallbackIntent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivity(Intent.createChooser(fallbackIntent, "请选择文件管理器以查看下载内容"));
        } catch (android.content.ActivityNotFoundException ex) {
            // If even this fails, then there is likely no file manager installed.
            Toast.makeText(this, "未找到文件管理器，无法打开下载文件夹。", Toast.LENGTH_LONG).show();
        }
    }

    private void initViews() {
        recyclerView = findViewById(R.id.history_recycler_view);
        refreshButton = findViewById(R.id.refresh_button);
        clearAllButton = findViewById(R.id.clear_all_button);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        refreshButton.setOnClickListener(v -> loadHistoryData());

        clearAllButton.setOnClickListener(v -> clearAllHistory());
    }

    private void loadHistoryData() {
        noiseDataList = dbHelper.getAllNoiseData();
        adapter = new HistoryAdapter(this, noiseDataList);
        recyclerView.setAdapter(adapter);

        if (noiseDataList.isEmpty()) {
            Toast.makeText(this, "暂无历史数据", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearAllHistory() {
        new AlertDialog.Builder(this)
                .setTitle("清空历史数据")
                .setMessage("确定要清空所有历史数据吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    dbHelper.clearAllNoiseData();
                    loadHistoryData();
                    Toast.makeText(HistoryActivity.this, "历史数据已清空", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void exportHistoryToCsv() {
        List<NoiseData> dataToExport = dbHelper.getAllNoiseData();
        if (dataToExport.isEmpty()) {
            Toast.makeText(this, "没有历史记录可导出", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder csvData = new StringBuilder();
        csvData.append("Timestamp,Value (dB),Latitude,Longitude\n");

        for (NoiseData data : dataToExport) {
            csvData.append(String.format(Locale.US, "%d,%.2f,%.6f,%.6f\n",
                    data.getTimestamp(), data.getDbValue(), data.getLatitude(), data.getLongitude()));
        }

        csvContentToSave = csvData.toString();
        saveCsvToFile(csvContentToSave);
    }

    private void saveCsvToFile(String csvData) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveFileUsingMediaStore(csvData);
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                saveFileLegacy(csvData);
            } else {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
    }

    private void saveFileUsingMediaStore(String csvData) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "sound_meter_history_" + System.currentTimeMillis() + ".csv");
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri != null) {
            try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                outputStream.write(csvData.getBytes());
                Toast.makeText(this, "历史记录已导出到下载文件夹", Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "创建文件失败", Toast.LENGTH_LONG).show();
        }
    }

    private void saveFileLegacy(String csvData) {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs();
        }
        File file = new File(downloadsDir, "sound_meter_history_" + System.currentTimeMillis() + ".csv");

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(csvData.getBytes());
            Toast.makeText(this, "历史记录已导出到下载文件夹", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // HistoryAdapter and ViewHolder classes remain the same
    public static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private android.content.Context context;
        private List<NoiseData> noiseDataList;

        public HistoryAdapter(android.content.Context context, List<NoiseData> noiseDataList) {
            this.context = context;
            this.noiseDataList = noiseDataList;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            NoiseData data = noiseDataList.get(position);

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String formattedTime = sdf.format(new Date(data.getTimestamp()));

            holder.dbValueTextView.setText(String.format("%.2f dB", data.getDbValue()));
            holder.timeTextView.setText(formattedTime);
            holder.locationTextView.setText(context.getString(R.string.msg_location_format,
                    data.getLatitude(), data.getLongitude()));

            if (data.getDbValue() > 80) {
                holder.dbValueTextView.setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_light));
            } else if (data.getDbValue() > 60) {
                holder.dbValueTextView.setTextColor(ContextCompat.getColor(context, android.R.color.holo_orange_light));
            } else {
                holder.dbValueTextView.setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_light));
            }
        }

        @Override
        public int getItemCount() {
            return noiseDataList.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView dbValueTextView;
            TextView timeTextView;
            TextView locationTextView;

            public ViewHolder(View itemView) {
                super(itemView);
                dbValueTextView = itemView.findViewById(R.id.db_value);
                timeTextView = itemView.findViewById(R.id.timestamp);
                locationTextView = itemView.findViewById(R.id.location);
            }
        }
    }
}
