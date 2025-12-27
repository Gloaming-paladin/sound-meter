package com.bodekjan.soundmeter;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class InfoDialog {

    public static void show(Context context) {
        final Dialog dialog = new Dialog(context, R.style.Dialog);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_info, null);
        dialog.setContentView(view);

        TextView title = view.findViewById(R.id.title);
        title.setText(R.string.activity_infotitle);

        Button negativeButton = view.findViewById(R.id.negativeButton);
        negativeButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
