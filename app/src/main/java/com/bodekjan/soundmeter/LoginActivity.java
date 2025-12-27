package com.bodekjan.soundmeter;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.bodekjan.soundmeter.view.ProgressButton;

public class LoginActivity extends AppCompatActivity {
    private EditText usernameEditText, passwordEditText;
    private ProgressButton loginButton;
    private TextView registerLink;
    private ImageView logo;
    private CardView loginCard;
    private NoiseDatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        dbHelper = new NoiseDatabaseHelper(this);
        logo = findViewById(R.id.login_logo);
        loginCard = findViewById(R.id.login_card);
        usernameEditText = findViewById(R.id.login_username);
        passwordEditText = findViewById(R.id.login_password);
        loginButton = findViewById(R.id.login_submit_button);
        registerLink = findViewById(R.id.login_register_link);

        Animation slideInFromTop = AnimationUtils.loadAnimation(this, R.anim.slide_in_from_top);
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        Animation slideInFromBottom = AnimationUtils.loadAnimation(this, R.anim.slide_in_from_bottom);

        logo.startAnimation(slideInFromTop);
        loginCard.startAnimation(fadeIn);
        loginButton.startAnimation(slideInFromBottom);
        registerLink.startAnimation(slideInFromBottom);

        loginButton.setOnClickListener(v -> {
            loginButton.startLoading();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                String username = usernameEditText.getText().toString().trim();
                String password = passwordEditText.getText().toString().trim();

                if (username.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show();
                    loginButton.stopMorphAnimation();
                    return;
                }

                if (dbHelper.checkUser(username, password)) {
                    Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show();
                    getSharedPreferences("user_prefs", MODE_PRIVATE).edit()
                            .putBoolean("isLoggedIn", true)
                            .putString("username", username)
                            .apply();
                    finish();
                } else {
                    Toast.makeText(this, "Invalid username or password", Toast.LENGTH_SHORT).show();
                    loginButton.stopMorphAnimation();
                }
            }, 2000);
        });

        registerLink.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }
}