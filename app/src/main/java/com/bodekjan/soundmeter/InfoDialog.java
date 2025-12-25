package com.bodekjan.soundmeter;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;

public class InfoDialog {

    public static void show(Context context) {
        new AlertDialog.Builder(context, R.style.Dialog)
                .setTitle("Information")
                      .setMessage("This is an information dialog.")
                      .setNegativeButton("OK", (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }
}
