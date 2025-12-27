package com.bodekjan.soundmeter;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
<<<<<<< HEAD
import androidx.appcompat.app.AlertDialog;
=======
import android.widget.TextView;
>>>>>>> fb927574d484da88caea343597c9cd7ff3c66095

public class InfoDialog {

    public static void show(Context context) {
<<<<<<< HEAD
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.Dialog);
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_info, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        Button negativeButton = dialogView.findViewById(R.id.negativeButton);
=======
        final Dialog dialog = new Dialog(context, R.style.Dialog);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_info, null);
        dialog.setContentView(view);

        TextView title = view.findViewById(R.id.title);
        title.setText(R.string.activity_infotitle);

        Button negativeButton = view.findViewById(R.id.negativeButton);
>>>>>>> fb927574d484da88caea343597c9cd7ff3c66095
        negativeButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
