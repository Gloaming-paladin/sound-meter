package com.bodekjan.soundmeter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ProfileOptionAdapter extends RecyclerView.Adapter<ProfileOptionAdapter.ViewHolder> {

    private final List<ProfileOption> options;
    private final OnOptionClickListener listener;

    public interface OnOptionClickListener {
        void onOptionClick(ProfileOption option);
    }

    public ProfileOptionAdapter(List<ProfileOption> options, OnOptionClickListener listener) {
        this.options = options;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_profile_option, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProfileOption option = options.get(position);
        holder.icon.setImageResource(option.getIconResId());
        holder.text.setText(option.getTitle());
        holder.itemView.setOnClickListener(v -> listener.onOptionClick(option));
    }

    @Override
    public int getItemCount() {
        return options.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView text;

        ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.option_icon);
            text = itemView.findViewById(R.id.option_text);
        }
    }
}
