package com.example.reminderapp;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppRecyclerAdapter extends RecyclerView.Adapter<AppRecyclerAdapter.ViewHolder> {

    private List<ResolveInfo> originalList;     // 完整資料
    private List<ResolveInfo> filteredList;     // 過濾後顯示的資料
    private final PackageManager pm;
    private final Set<String> selectedPackages = new HashSet<>(); // 記錄已勾選的 package

    public AppRecyclerAdapter(Context context, List<ResolveInfo> apps) {
        this.originalList = new ArrayList<>(apps);
        this.filteredList = new ArrayList<>(apps);
        this.pm = context.getPackageManager();
    }

    public void filter(String query) {
        filteredList.clear();
        if (query.isEmpty()) {
            filteredList.addAll(originalList);
        } else {
            String lowerQuery = query.toLowerCase();
            for (ResolveInfo info : originalList) {
                String name = info.loadLabel(pm).toString().toLowerCase();
                if (name.contains(lowerQuery)) {
                    filteredList.add(info);
                }
            }
        }
        notifyDataSetChanged();
    }

    public Set<String> getSelectedPackages() {
        return new HashSet<>(selectedPackages);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.app_list_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ResolveInfo info = filteredList.get(position);

        holder.icon.setImageDrawable(info.loadIcon(pm));
        holder.name.setText(info.loadLabel(pm).toString());
        // 勾選狀態
        boolean isChecked = selectedPackages.contains(info.activityInfo.packageName);
        holder.checkBox.setChecked(isChecked);

        holder.itemView.setOnClickListener(v -> {
            String pkg = info.activityInfo.packageName;
            if (selectedPackages.contains(pkg)) {
                selectedPackages.remove(pkg);
            } else {
                selectedPackages.add(pkg);
            }
            notifyItemChanged(position);
        });
    }
    @Override
    public int getItemCount() {
        return filteredList.size();
    }
    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView name;
        CheckBox checkBox;
        ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.app_icon);
            name = itemView.findViewById(R.id.app_name);

            // 如果你原本的 item layout 沒有 CheckBox，請加一個
            checkBox = itemView.findViewById(R.id.checkbox); // 需要在 layout 加
        }
    }
}
