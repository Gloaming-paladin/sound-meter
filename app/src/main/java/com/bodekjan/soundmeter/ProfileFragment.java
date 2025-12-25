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

public class ProfileFragment extends Fragment {
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int STORAGE_PERMISSION_CODE = 101;

    private Button loginButton, registerButton, logoutButton, historyButton;
    private LinearLayout loggedOutButtons, loggedInSection;
    private TextView usernameTextView;

    private SharedPreferences sharedPreferences;
    private NoiseDatabaseHelper dbHelper;

    private ActivityResultLauncher<String> requestPermissionLauncher;



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
        loginButton = view.findViewById(R.id.login_button);
        registerButton = view.findViewById(R.id.register_button);
        logoutButton = view.findViewById(R.id.logout_button);
        historyButton = view.findViewById(R.id.history_button);
        loggedOutButtons = view.findViewById(R.id.logged_out_buttons);
        loggedInSection = view.findViewById(R.id.logged_in_section);
        usernameTextView = view.findViewById(R.id.username_text);


        if (loginButton != null) {
            loginButton.setOnClickListener(v -> goToLogin());
        }
        if (registerButton != null) {
            registerButton.setOnClickListener(v -> goToRegister());
        }
        if (logoutButton != null) {
            logoutButton.setOnClickListener(v -> handleLogout());
        }
        if (historyButton != null) {
            historyButton.setOnClickListener(v -> openHistoryActivity());
        }

    }



    private void handleLogout() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).showLogin();
        }
    }

    private void openHistoryActivity() {
        Intent intent = new Intent(requireContext(), HistoryActivity.class);
        startActivity(intent);
    }

    private void updateUI() {
        if (sharedPreferences == null || dbHelper == null || loggedOutButtons == null || loggedInSection == null || usernameTextView == null) {
            return;
        }
        boolean isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false);
        if (isLoggedIn) {
            loggedOutButtons.setVisibility(View.GONE);
            loggedInSection.setVisibility(View.VISIBLE);
            String username = sharedPreferences.getString("username", "User");
            usernameTextView.setText("欢迎, " + username);


        } else {
            loggedOutButtons.setVisibility(View.VISIBLE);
            loggedInSection.setVisibility(View.GONE);

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
