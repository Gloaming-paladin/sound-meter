// file: app/src/main/java/com/bodekjan/soundmeter/HistoryActivity.java
package com.bodekjan.soundmeter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bodekjan.soundmeter.NoiseDatabaseHelper;
import com.bodekjan.soundmeter.NoiseData;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        initViews();
        dbHelper = new NoiseDatabaseHelper(this);
        loadHistoryData();
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
        // 使用AlertDialog确认删除操作
        new AlertDialog.Builder(this)
                .setTitle("清空历史数据")
                .setMessage("确定要清空所有历史数据吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    // 清空数据库
                    dbHelper.clearAllNoiseData();
                    // 重新加载数据
                    loadHistoryData();
                    Toast.makeText(HistoryActivity.this, "历史数据已清空", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 历史记录适配器
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

            // 格式化时间显示
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String formattedTime = sdf.format(new Date(data.getTimestamp()));

            holder.dbValueTextView.setText(String.format("%.2f dB", data.getDbValue()));
            holder.timeTextView.setText(formattedTime);
            holder.locationTextView.setText(String.format("位置: %.4f, %.4f",
                    data.getLatitude(), data.getLongitude()));

            // 根据分贝值设置颜色
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
                // 修改为与布局文件中一致的ID
                dbValueTextView = itemView.findViewById(R.id.db_value);
                timeTextView = itemView.findViewById(R.id.timestamp);
                locationTextView = itemView.findViewById(R.id.location);
            }
        }
    }
}
