// ProfileFragment.java
package com.bodekjan.soundmeter;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.io.IOException;
import java.io.OutputStream;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ProfileFragment extends Fragment implements ProfileOptionAdapter.OnOptionClickListener {

    private ImageView avatarImageView;
    private TextView usernameTextView;
    private RecyclerView recyclerView;

    private SharedPreferences sharedPreferences;
    private NoiseDatabaseHelper dbHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sharedPreferences = requireActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        dbHelper = new NoiseDatabaseHelper(requireContext());
        initializeViews(view);
        updateUI();
    }

    private void initializeViews(View view) {
        avatarImageView = view.findViewById(R.id.avatar_image);
        usernameTextView = view.findViewById(R.id.username_text);
        recyclerView = view.findViewById(R.id.profile_options_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
    }

    private void updateUI() {
        boolean isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false);
        if (isLoggedIn) {
            String username = sharedPreferences.getString("username", "User");
            usernameTextView.setText("欢迎, " + username);
            String avatarPath = dbHelper.getUserAvatar(username);
            if (avatarPath != null) {
                // Here you would load the image from the path. For simplicity, we'll just set a default icon.
                // Glide.with(this).load(avatarPath).into(avatarImageView);
                avatarImageView.setImageResource(R.drawable.ic_default_avatar); // Placeholder
            } else {
                avatarImageView.setImageResource(R.drawable.ic_default_avatar);
            }
            setupLoggedInOptions();
        } else {
            usernameTextView.setText("登录 / 注册");
            avatarImageView.setImageResource(R.drawable.ic_default_avatar);
            setupLoggedOutOptions();
        }
    }

    private void handleLogout() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
        updateUI(); // Re-load the UI to show logged-out state
    }

    private void openHistoryActivity() {
        Intent intent = new Intent(requireContext(), HistoryActivity.class);
        startActivity(intent);
    }

    private void setupLoggedOutOptions() {
        List<ProfileOption> options = new ArrayList<>();
        options.add(new ProfileOption(R.drawable.ic_login, "登录", ProfileOption.Action.LOGIN));
        options.add(new ProfileOption(R.drawable.ic_register, "注册", ProfileOption.Action.REGISTER));
        recyclerView.setAdapter(new ProfileOptionAdapter(options, this));
    }

    private void setupLoggedInOptions() {
        List<ProfileOption> options = new ArrayList<>();
        options.add(new ProfileOption(R.drawable.ic_history, "查看历史记录", ProfileOption.Action.HISTORY));
        options.add(new ProfileOption(R.drawable.ic_logout, "退出登录", ProfileOption.Action.LOGOUT));
        recyclerView.setAdapter(new ProfileOptionAdapter(options, this));
    }

    @Override
    public void onOptionClick(ProfileOption option) {
        switch (option.getAction()) {
            case LOGIN:
                goToLogin();
                break;
            case REGISTER:
                goToRegister();
                break;
            case LOGOUT:
                handleLogout();
                break;
            case HISTORY:
                openHistoryActivity();
                break;
        }
    }

    private void goToLogin() {
        FragmentTransaction transaction = getParentFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, new LoginFragment());
        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void goToRegister() {
        FragmentTransaction transaction = getParentFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, new RegisterFragment());
        transaction.addToBackStack(null);
        transaction.commit();
    }




}
