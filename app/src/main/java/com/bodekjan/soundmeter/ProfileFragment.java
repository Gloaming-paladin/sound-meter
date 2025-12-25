// ProfileFragment.java
package com.bodekjan.soundmeter;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ProfileFragment extends Fragment {
    private Button loginButton;
    private Button historyButton;
    private TextView userStatusText;
    private boolean isLoggedIn = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        updateUI();
    }

    private void initializeViews(View view) {
        loginButton = view.findViewById(R.id.login_button);
        historyButton = view.findViewById(R.id.history_button);
        userStatusText = view.findViewById(R.id.user_status_text);

        loginButton.setOnClickListener(v -> handleLogin());
        historyButton.setOnClickListener(v -> openHistoryActivity());
    }

    private void handleLogin() {
        if (isLoggedIn) {
            // 登出
            isLoggedIn = false;
            Toast.makeText(requireContext(), "已退出登录", Toast.LENGTH_SHORT).show();
        } else {
            // 模拟登录
            isLoggedIn = true;
            Toast.makeText(requireContext(), "登录成功", Toast.LENGTH_SHORT).show();
        }
        updateUI();
    }

    private void openHistoryActivity() {
        Intent intent = new Intent(requireContext(), HistoryActivity.class);
        startActivity(intent);
    }

    private void updateUI() {
        if (isLoggedIn) {
            loginButton.setText("退出登录");
            userStatusText.setText("已登录");
        } else {
            loginButton.setText("登录");
            userStatusText.setText("未登录");
        }
    }
}
