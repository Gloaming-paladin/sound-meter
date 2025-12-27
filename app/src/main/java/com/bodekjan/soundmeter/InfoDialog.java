package com.bodekjan.soundmeter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AlertDialog;

public class InfoDialog {

    public static void show(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.Dialog);
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_info, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        Button negativeButton = dialogView.findViewById(R.id.negativeButton);
        negativeButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
