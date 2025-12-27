package com.bodekjan.soundmeter;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private FusedLocationProviderClient fusedLocationClient;
    private double currentLatitude = 0.0;
    private double currentLongitude = 0.0;
    private SharedPreferences sharedPreferences;
    private final Fragment decibelMeterFragment = new DecibelMeterFragment();
    private final Fragment audioAnalysisFragment = new AudioAnalysisFragment();
    private final Fragment decibelCameraFragment = new DecibelCameraFragment();
    private final Fragment profileFragment = new ProfileFragment();
    private final Fragment loginFragment = new LoginFragment();
    private Fragment activeFragment = decibelMeterFragment;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE);

        // 初始化 FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 检查权限
        if (!checkPermissions()) {
            requestPermissions();
        }

        getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, loginFragment, "5").hide(loginFragment).commit();
        getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, profileFragment, "4").hide(profileFragment).commit();
        getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, decibelCameraFragment, "3").hide(decibelCameraFragment).commit();
        getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, audioAnalysisFragment, "2").hide(audioAnalysisFragment).commit();
        getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, decibelMeterFragment, "1").commit();

        if (isLoggedIn()) {
            showMainContent();
        } else {
            showLogin();
        }

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_decibel_meter) {
                getSupportFragmentManager().beginTransaction().hide(activeFragment).show(decibelMeterFragment).commit();
                activeFragment = decibelMeterFragment;
                return true;
            } else if (itemId == R.id.nav_audio_analysis) {
                getSupportFragmentManager().beginTransaction().hide(activeFragment).show(audioAnalysisFragment).commit();
                activeFragment = audioAnalysisFragment;
                return true;
            } else if (itemId == R.id.navigation_decibel_camera) {
                getSupportFragmentManager().beginTransaction().hide(activeFragment).show(decibelCameraFragment).commit();
                activeFragment = decibelCameraFragment;
                return true;
            } else if (itemId == R.id.nav_profile) {
                getSupportFragmentManager().beginTransaction().hide(activeFragment).show(profileFragment).commit();
                activeFragment = profileFragment;
                return true;
            }
            return false;
        });
    }

    private boolean isLoggedIn() {
        return sharedPreferences.getBoolean("isLoggedIn", false);
    }

    public void showMainContent() {
        bottomNavigationView.setVisibility(View.VISIBLE);
        getSupportFragmentManager().beginTransaction().hide(activeFragment).show(decibelMeterFragment).commit();
        activeFragment = decibelMeterFragment;
    }

    public void showLogin() {
        bottomNavigationView.setVisibility(View.GONE);
        getSupportFragmentManager().beginTransaction().hide(activeFragment).show(loginFragment).commit();
        activeFragment = loginFragment;
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.RECORD_AUDIO
                },
                PERMISSION_REQUEST_CODE);
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
                Toast.makeText(this, getString(R.string.msg_all_permissions_needed), Toast.LENGTH_SHORT).show();
            }
        }

        // 处理定位权限请求
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdate();
        }
    }

    private void startLocationUpdate() {
        // 使用 FusedLocationProviderClient 获取位置
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentLatitude = location.getLatitude();
                        currentLongitude = location.getLongitude();
                        Toast.makeText(this, getString(R.string.msg_location_success, String.valueOf(currentLatitude), String.valueOf(currentLongitude)), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
