package com.bodekjan.soundmeter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import com.bodekjan.soundmeter.view.ProgressButton;

public class RegisterFragment extends Fragment {
    private EditText usernameEditText;
    private EditText passwordEditText;
    private EditText confirmPasswordEditText;
    private ProgressButton registerButton;
    private TextView loginLink;
    private TextView passwordStrengthFeedback;
    private NoiseDatabaseHelper dbHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dbHelper = new NoiseDatabaseHelper(requireContext());

        usernameEditText = view.findViewById(R.id.register_username);
        passwordEditText = view.findViewById(R.id.register_password);
        confirmPasswordEditText = view.findViewById(R.id.register_confirm_password);
        registerButton = view.findViewById(R.id.register_button);
        loginLink = view.findViewById(R.id.login_link);
        passwordStrengthFeedback = view.findViewById(R.id.password_strength_feedback);

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

        registerButton.setOnClickListener(v -> registerUser());
        loginLink.setOnClickListener(v -> goToLogin());
    }

    private void registerUser() {
        registerButton.startLoading();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            String username = usernameEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();
            String confirmPassword = confirmPasswordEditText.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(requireContext(), R.string.toast_register_fill_all_fields, Toast.LENGTH_SHORT).show();
                registerButton.stopMorphAnimation();
                return;
            }

            if (!password.equals(confirmPassword)) {
                Toast.makeText(requireContext(), R.string.toast_password_mismatch, Toast.LENGTH_SHORT).show();
                registerButton.stopMorphAnimation();
                return;
            }

            if (dbHelper.checkUserExists(username)) {
                Toast.makeText(requireContext(), R.string.toast_username_exists, Toast.LENGTH_SHORT).show();
                registerButton.stopMorphAnimation();
                return;
            }

            if (isPasswordWeak(password)) {
                Toast.makeText(requireContext(), R.string.toast_password_too_weak, Toast.LENGTH_SHORT).show();
                registerButton.stopMorphAnimation();
                return;
            }

            if (dbHelper.addUser(username, password)) {
                Toast.makeText(requireContext(), R.string.toast_register_success, Toast.LENGTH_SHORT).show();
                goToLogin();
            } else {
                Toast.makeText(requireContext(), R.string.toast_register_failed, Toast.LENGTH_SHORT).show();
                registerButton.stopMorphAnimation();
            }
        }, 2000);
    }

    private void checkPasswordStrength(String password) {
        if (password.isEmpty()) {
            passwordStrengthFeedback.setVisibility(TextView.GONE);
            return;
        }
        passwordStrengthFeedback.setVisibility(TextView.VISIBLE);
        Animation fadeIn = AnimationUtils.loadAnimation(getContext(), R.anim.fade_in);
        passwordStrengthFeedback.startAnimation(fadeIn);

        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasLetter = password.matches(".*[a-zA-Z].*");
        boolean hasSpecial = password.matches(".*[^a-zA-Z0-9].*");

        if (hasDigit && hasLetter && hasSpecial) {
            passwordStrengthFeedback.setText(R.string.password_strength_strong);
            passwordStrengthFeedback.setTextColor(Color.GREEN);
        } else if (hasDigit && hasLetter) {
            passwordStrengthFeedback.setText(R.string.password_strength_medium);
            passwordStrengthFeedback.setTextColor(Color.YELLOW);
        } else {
            passwordStrengthFeedback.setText(R.string.password_strength_weak);
            passwordStrengthFeedback.setTextColor(Color.RED);
        }
    }

    private boolean isPasswordWeak(String password) {
        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasLetter = password.matches(".*[a-zA-Z].*");
        return !(hasDigit && hasLetter);
    }

    private void goToLogin() {
        FragmentTransaction transaction = getParentFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, new LoginFragment());
        transaction.commit();
    }
}
