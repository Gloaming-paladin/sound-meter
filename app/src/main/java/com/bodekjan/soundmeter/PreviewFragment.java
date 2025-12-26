package com.bodekjan.soundmeter;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.VideoView;
import android.content.ContentValues;
import android.os.Build;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Environment;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;

public class PreviewFragment extends Fragment {

    private Uri fileUri;
    private boolean isVideo;
    private String tempFilePath;

    public static PreviewFragment newInstance(Uri fileUri) {
        PreviewFragment fragment = new PreviewFragment();
        Bundle args = new Bundle();
        args.putParcelable("file_uri", fileUri);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            fileUri = getArguments().getParcelable("file_uri");
            isVideo = getArguments().getBoolean("is_video");
            tempFilePath = getArguments().getString("temp_file_path");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_preview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String decibelText = getArguments().getString("decibel_text");
        String avgDecibelText = getArguments().getString("avg_decibel_text");
        String minDecibelText = getArguments().getString("min_decibel_text");
        String maxDecibelText = getArguments().getString("max_decibel_text");
        String locationText = getArguments().getString("location_text");

        TextView decibelTextView = view.findViewById(R.id.decibel_text_preview);
        TextView avgDecibelTextView = view.findViewById(R.id.avg_decibel_text_preview);
        TextView minDecibelTextView = view.findViewById(R.id.min_decibel_text_preview);
        TextView maxDecibelTextView = view.findViewById(R.id.max_decibel_text_preview);
        TextView locationTextView = view.findViewById(R.id.location_text_preview);

        if (decibelTextView != null) decibelTextView.setText(decibelText);
        if (avgDecibelTextView != null) avgDecibelTextView.setText(avgDecibelText);
        if (minDecibelTextView != null) minDecibelTextView.setText(minDecibelText);
        if (maxDecibelTextView != null) maxDecibelTextView.setText(maxDecibelText);
        if (locationTextView != null) locationTextView.setText(locationText);

        ImageView imageView = view.findViewById(R.id.preview_image);
        VideoView videoView = view.findViewById(R.id.preview_video);

        if (!isVideo) {
            imageView.setVisibility(View.VISIBLE);
            imageView.setImageURI(fileUri);
        } else {
            videoView.setVisibility(View.VISIBLE);
            videoView.setVideoURI(fileUri);
            videoView.start();
        }

        Button saveButton = view.findViewById(R.id.button_save);
        saveButton.setOnClickListener(v -> {
            if (tempFilePath != null) {
                if (isVideo) {
                    saveVideoToGallery();
                } else {
                    saveImageToGallery();
                }
            }
            getParentFragmentManager().popBackStack();
        });

        Button dontSaveButton = view.findViewById(R.id.button_dont_save);
        dontSaveButton.setOnClickListener(v -> {
            if (tempFilePath != null) {
                new File(tempFilePath).delete();
            }
            getParentFragmentManager().popBackStack();
        });
    }

    private void saveImageToGallery() {
        // The following imports will be needed:
        // import android.graphics.Bitmap;
        // import android.graphics.Canvas;
        // import android.graphics.Color;
        // import android.graphics.Paint;
        // import android.os.Environment;
        // import android.widget.Toast;
        // import android.content.ContentResolver;
        // import android.widget.TextView;
        try {
            Bitmap originalBitmap = MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), fileUri);
            Bitmap mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(mutableBitmap);
            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            paint.setTextSize(36);
            paint.setAntiAlias(true);

            String decibelText = getArguments().getString("decibel_text");
            String avgDecibelText = getArguments().getString("avg_decibel_text");
            String minDecibelText = getArguments().getString("min_decibel_text");
            String maxDecibelText = getArguments().getString("max_decibel_text");
            String locationText = getArguments().getString("location_text");

            canvas.drawText(decibelText, 50, 100, paint);
            canvas.drawText(avgDecibelText, 50, 150, paint);
            canvas.drawText(minDecibelText, 50, 200, paint);
            canvas.drawText(maxDecibelText, 50, 250, paint);
            canvas.drawText(locationText, 50, 300, paint);

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
            } else {
                values.put(MediaStore.Images.Media.DATA, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getPath());
            }

            ContentResolver resolver = requireContext().getContentResolver();
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (uri != null) {
                try (OutputStream outputStream = resolver.openOutputStream(uri)) {
                    mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                    Toast.makeText(getContext(), "图片已保存至相册", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveVideoToGallery() {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, System.currentTimeMillis() + ".mp4");
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SoundMeter");
        }

        Uri videoUri = requireContext().getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues);
        try (InputStream in = requireContext().getContentResolver().openInputStream(fileUri);
             OutputStream out = requireContext().getContentResolver().openOutputStream(videoUri)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            Toast.makeText(getContext(), "视频已保存至相册", Toast.LENGTH_SHORT).show();
            savePreviewScreenshot();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void savePreviewScreenshot() {
        View view = getView();
        if (view == null) return;

        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "preview_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
        } else {
            values.put(MediaStore.Images.Media.DATA, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getPath());
        }

        ContentResolver resolver = requireContext().getContentResolver();
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        if (uri != null) {
            try (OutputStream outputStream = resolver.openOutputStream(uri)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
                Toast.makeText(getContext(), "预览截图已保存", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
