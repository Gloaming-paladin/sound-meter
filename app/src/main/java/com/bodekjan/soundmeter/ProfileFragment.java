package com.bodekjan.soundmeter;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class ProfileFragment extends Fragment implements ProfileOptionAdapter.OnOptionClickListener {

    private ImageView avatarImageView;
    private TextView usernameTextView;
    private RecyclerView recyclerView;

    private SharedPreferences sharedPreferences;
    private NoiseDatabaseHelper dbHelper;

    private boolean isDefaultAvatar = true;

    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<String> requestReadPermissionLauncher;
    private ActivityResultLauncher<String> requestWritePermissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Launcher for picking an image from the gallery
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                try {
                    // Persist permission to access this URI
                    requireActivity().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    String username = sharedPreferences.getString("username", null);
                    if (username != null) {
                        dbHelper.updateUserAvatar(username, uri.toString());
                        updateUI(); // Refresh UI to show the new avatar
                    }
                } catch (Exception e) {
                    Toast.makeText(getContext(), "设置头像失败", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Launcher for requesting READ_EXTERNAL_STORAGE permission
        requestReadPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                pickImageLauncher.launch("image/*");
            } else {
                // Explain to the user why the permission is needed
                if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    Toast.makeText(getContext(), "需要相册读取权限才能选择头像", Toast.LENGTH_LONG).show();
                } else {
                    // User has selected "Don't ask again". Direct them to settings.
                    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("权限请求")
                        .setMessage("请授予读取相册的权限，以便选择头像。您需要到应用设置中手动开启。")
                        .setPositiveButton("去设置", (dialog, which) -> {
                            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri uri = Uri.fromParts("package", requireActivity().getPackageName(), null);
                            intent.setData(uri);
                            startActivity(intent);
                        })
                        .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                        .create()
                        .show();
                }
            }
        });

        // Launcher for requesting WRITE_EXTERNAL_STORAGE permission
        requestWritePermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                saveAvatarToGallery();
            } else {
                Toast.makeText(getContext(), "需要文件写入权限才能保存头像", Toast.LENGTH_SHORT).show();
            }
        });
    }


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

        avatarImageView.setOnClickListener(v -> {
            if (sharedPreferences.getBoolean("isLoggedIn", false)) {
                showAvatarOptionsDialog();
            } else {
                // If not logged in, clicking avatar should probably do nothing or prompt login.
                // For now, we do nothing.
            }
        });
    }

    private void updateUI() {
        boolean isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false);
        if (isLoggedIn) {
            String username = sharedPreferences.getString("username", "User");
            usernameTextView.setText("欢迎, " + username);
            String avatarPath = dbHelper.getUserAvatar(username);
            if (avatarPath != null && !avatarPath.isEmpty()) {
                try {
                    Uri avatarUri = Uri.parse(avatarPath);
                    avatarImageView.setImageURI(avatarUri);
                    isDefaultAvatar = false;
                } catch (Exception e) {
                    // Fallback to default if URI is invalid or permission is lost
                    avatarImageView.setImageResource(R.drawable.ic_default_avatar);
                    isDefaultAvatar = true;
                }
            } else {
                avatarImageView.setImageResource(R.drawable.ic_default_avatar);
                isDefaultAvatar = true;
            }
            setupLoggedInOptions();
        } else {
            usernameTextView.setText("登录 / 注册");
            avatarImageView.setImageResource(R.drawable.ic_default_avatar);
            isDefaultAvatar = true;
            setupLoggedOutOptions();
        }
    }

    private void showAvatarOptionsDialog() {
        final BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheetView = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_avatar_options, null);
        dialog.setContentView(sheetView);

        TextView selectFromAlbum = sheetView.findViewById(R.id.option_select_from_album);
        TextView saveToPhone = sheetView.findViewById(R.id.option_save_to_phone);
        TextView cancel = sheetView.findViewById(R.id.option_cancel);

        if (isDefaultAvatar) {
            saveToPhone.setVisibility(View.GONE);
        } else {
            saveToPhone.setVisibility(View.VISIBLE);
        }

        selectFromAlbum.setOnClickListener(v -> {
            selectImageFromGallery();
            dialog.dismiss();
        });

        saveToPhone.setOnClickListener(v -> {
            saveAvatarToGallery();
            dialog.dismiss();
        });

        cancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void selectImageFromGallery() {
        String permission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            pickImageLauncher.launch("image/*");
        } else {
            requestReadPermissionLauncher.launch(permission);
        }
    }

    private void saveAvatarToGallery() {
        // For Android Q (API 29) and above, no specific permission is needed to save to app's own directory or MediaStore.
        // For older versions, WRITE_EXTERNAL_STORAGE is required.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestWritePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }

        BitmapDrawable drawable = (BitmapDrawable) avatarImageView.getDrawable();
        if (drawable == null) {
            Toast.makeText(getContext(), "没有可保存的头像", Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap bitmap = drawable.getBitmap();

        OutputStream fos = null;
        String imageFileName = "Avatar_" + System.currentTimeMillis() + ".jpg";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, imageFileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        Uri imageUri = null;
        try {
            imageUri = requireActivity().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (imageUri != null) {
                fos = requireActivity().getContentResolver().openOutputStream(imageUri);
                if (fos != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    Toast.makeText(getContext(), "头像已保存到相册", Toast.LENGTH_SHORT).show();
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    requireActivity().getContentResolver().update(imageUri, values, null, null);
                }
            }
        } catch (IOException e) {
            if (imageUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requireActivity().getContentResolver().delete(imageUri, null, null);
            }
            Toast.makeText(getContext(), "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                // ignore
            }
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
        options.add(new ProfileOption(R.drawable.history_record, "查看历史记录", ProfileOption.Action.HISTORY));
        options.add(new ProfileOption(R.drawable.ic_settings, "设置", ProfileOption.Action.SETTINGS));
        options.add(new ProfileOption(R.drawable.ic_about, "关于", ProfileOption.Action.ABOUT));
        options.add(new ProfileOption(R.drawable.log_out, "退出登录", ProfileOption.Action.LOGOUT));
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
            case SETTINGS:
                goToSettings();
                break;
            case ABOUT:
                goToAbout();
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

    private void goToSettings() {
        FragmentTransaction transaction = getParentFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, new SettingsFragment());
        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void goToAbout() {
        FragmentTransaction transaction = getParentFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, new AboutFragment());
        transaction.addToBackStack(null);
        transaction.commit();
    }
}
