package com.bodekjan.soundmeter;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.bodekjan.soundmeter.view.ProgressButton;

public class RegisterActivity extends AppCompatActivity {
    private EditText usernameEditText, passwordEditText, confirmPasswordEditText;
    private ProgressButton registerButton;
    private TextView loginLink, passwordStrengthFeedback;
    private ImageView logo;
    private CardView registerCard;
    private NoiseDatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        dbHelper = new NoiseDatabaseHelper(this);
        logo = findViewById(R.id.register_logo);
        registerCard = findViewById(R.id.register_card);
        usernameEditText = findViewById(R.id.register_username);
        passwordEditText = findViewById(R.id.register_password);
        confirmPasswordEditText = findViewById(R.id.register_confirm_password);
        registerButton = findViewById(R.id.register_submit_button);
        loginLink = findViewById(R.id.register_login_link);
        passwordStrengthFeedback = findViewById(R.id.password_strength_feedback);

        Animation slideInFromTop = AnimationUtils.loadAnimation(this, R.anim.slide_in_from_top);
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        Animation slideInFromBottom = AnimationUtils.loadAnimation(this, R.anim.slide_in_from_bottom);

        logo.startAnimation(slideInFromTop);
        registerCard.startAnimation(fadeIn);
        registerButton.startAnimation(slideInFromBottom);
        loginLink.startAnimation(slideInFromBottom);

        passwordEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkPasswordStrength(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        registerButton.setOnClickListener(v -> {
            registerButton.startLoading();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                String username = usernameEditText.getText().toString().trim();
                String password = passwordEditText.getText().toString().trim();
                String confirmPassword = confirmPasswordEditText.getText().toString().trim();

                if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                    registerButton.stopMorphAnimation();
                    return;
                }

                if (!password.equals(confirmPassword)) {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                    registerButton.stopMorphAnimation();
                    return;
                }

                boolean success = dbHelper.addUser(username, password);
                if (success) {
                    Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(this, "Username already exists", Toast.LENGTH_SHORT).show();
                    registerButton.stopMorphAnimation();
                }
            }, 2000);
        });

        loginLink.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
        });
    }

    private void checkPasswordStrength(String password) {
        if (password.isEmpty()) {
            passwordStrengthFeedback.setVisibility(TextView.GONE);
            return;
        }

        passwordStrengthFeedback.setVisibility(TextView.VISIBLE);
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        passwordStrengthFeedback.startAnimation(fadeIn);

        // Regex patterns for password strength
        boolean hasLower = password.matches(".*[a-z].*");
        boolean hasUpper = password.matches(".*[A-Z].*");
        boolean hasDigit = password.matches(".*[0-9].*");
        boolean hasSpecial = password.matches(".*[^a-zA-Z0-9].*");

        int strength = 0;
        if (hasLower || hasUpper) strength++;
        if (hasDigit) strength++;
        if (hasSpecial) strength++;

        if (password.length() < 8) {
            passwordStrengthFeedback.setText("Weak");
            passwordStrengthFeedback.setTextColor(Color.RED);
        } else {
            if (strength == 1) {
                passwordStrengthFeedback.setText("Medium");
                passwordStrengthFeedback.setTextColor(Color.YELLOW);
            } else if (strength >= 2) {
                passwordStrengthFeedback.setText("Strong");
                    passwordStrengthFeedback.setTextColor(Color.GREEN);
            } else {
                passwordStrengthFeedback.setText("Weak");
                passwordStrengthFeedback.setTextColor(Color.RED);
            }
        }
    }
}