package com.bodekjan.soundmeter;

import android.content.Intent;
import android.os.Bundle;
<<<<<<< HEAD
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
=======
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
>>>>>>> fb927574d484da88caea343597c9cd7ff3c66095
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

<<<<<<< HEAD
        ImageView splashIcon = findViewById(R.id.splash_icon);
        TextView splashTitle = findViewById(R.id.splash_title);

        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        splashIcon.startAnimation(fadeIn);
        splashTitle.startAnimation(fadeIn);

        new Handler().postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }, SPLASH_DELAY);
=======
        View content = findViewById(R.id.splash_content);
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);

        fadeIn.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });

        content.startAnimation(fadeIn);
>>>>>>> fb927574d484da88caea343597c9cd7ff3c66095
    }
}
