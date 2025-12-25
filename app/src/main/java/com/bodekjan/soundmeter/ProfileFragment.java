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
    private ImageView avatarImageView;
    private RelativeLayout avatarLayout;
    private SharedPreferences sharedPreferences;
    private NoiseDatabaseHelper dbHelper;

    private ActivityResultLauncher<Intent> pickImageLauncher;
    private ActivityResultLauncher<String[]> requestPermissionsLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        avatarImageView.setImageURI(imageUri);
                        updateUserAvatarInDb(imageUri.toString());
                    }
                }
        );

        requestPermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    boolean allPermissionsGranted = true;
                    for (Boolean isGranted : permissions.values()) {
                        if (!isGranted) {
                            allPermissionsGranted = false;
                            break;
                        }
                    }

                    if (allPermissionsGranted) {
                        openGallery();
                    } else {
                        Toast.makeText(requireContext(), "需要存储权限才能选择头像", Toast.LENGTH_SHORT).show();
                    }
                }
        );

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
        avatarImageView = view.findViewById(R.id.profile_avatar);
        avatarLayout = view.findViewById(R.id.avatar_layout);

        loginButton.setOnClickListener(v -> goToLogin());
        registerButton.setOnClickListener(v -> goToRegister());
        logoutButton.setOnClickListener(v -> handleLogout());
        historyButton.setOnClickListener(v -> openHistoryActivity());
        avatarLayout.setOnClickListener(v -> {
            String username = sharedPreferences.getString("username", null);
            if (username != null) {
                String avatarPath = dbHelper.getUserAvatar(username);
                if (avatarPath != null && !avatarPath.isEmpty()) {
                    showAvatarOptions();
                } else {
                    checkPermissionAndOpenGallery();
                }
            } else {
                Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkPermissionAndOpenGallery() {
        requestPermissionsLauncher.launch(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE});
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }



    private void updateUserAvatarInDb(String avatarPath) {
        String username = sharedPreferences.getString("username", null);
        if (username != null) {
            dbHelper.updateUserAvatar(username, avatarPath);
        }
    }

    private void handleLogout() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
        ((MainActivity) requireActivity()).showLogin();
    }

    private void openHistoryActivity() {
        Intent intent = new Intent(requireContext(), HistoryActivity.class);
        startActivity(intent);
    }

    private void updateUI() {
        boolean isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false);
        if (isLoggedIn) {
            loggedOutButtons.setVisibility(View.GONE);
            loggedInSection.setVisibility(View.VISIBLE);
            String username = sharedPreferences.getString("username", "User");
            usernameTextView.setText("欢迎, " + username);

            String avatarPath = dbHelper.getUserAvatar(username);
            if (avatarPath != null && !avatarPath.isEmpty()) {
                avatarImageView.setImageURI(Uri.parse(avatarPath));
            } else {
                avatarImageView.setImageResource(R.drawable.bg_avatar_placeholder);
            }
        } else {
            loggedOutButtons.setVisibility(View.VISIBLE);
            loggedInSection.setVisibility(View.GONE);
            avatarImageView.setImageResource(R.drawable.bg_avatar_placeholder);
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

    private void showAvatarOptions() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_avatar_options, null);
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(dialogView);

        TextView saveAvatarButton = dialogView.findViewById(R.id.save_avatar_button);
        TextView changeAvatarButton = dialogView.findViewById(R.id.change_avatar_button);

        saveAvatarButton.setOnClickListener(v -> {
            saveAvatarToDevice();
            dialog.dismiss();
        });

        changeAvatarButton.setOnClickListener(v -> {
            checkPermissionAndOpenGallery();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void saveAvatarToDevice() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissionsLauncher.launch(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE});
            return;
        }

        BitmapDrawable drawable = (BitmapDrawable) avatarImageView.getDrawable();
        Bitmap bitmap = drawable.getBitmap();

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "avatar_" + System.currentTimeMillis() + ".png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);

        Uri uri = requireContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        if (uri != null) {
            try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (IOException e) {
                e.printStackTrace();
            }

            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            requireContext().getContentResolver().update(uri, values, null, null);

            Toast.makeText(requireContext(), "头像已保存到相册", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "保存失败", Toast.LENGTH_SHORT).show();
        }
    }


}
