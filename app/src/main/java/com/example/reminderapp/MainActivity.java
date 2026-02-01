package com.example.reminderapp;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private PackageManager pm;
    public static final String CHANNEL_ID = "MyChannelId";

    // 成員變數
    private RecyclerView recyclerView;
    private AppRecyclerAdapter adapter;
    private List<ResolveInfo> resolveInfoList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // 請求通知權限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        // 初始化 PackageManager
        pm = getPackageManager();
        // 新增 RecyclerView 初始化
        recyclerView = findViewById(R.id.recycler_apps);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        EditText inputText = findViewById(R.id.inputbox);

        // 儲存提示訊息的按鈕
        Button confirmButton = findViewById(R.id.confirm_button);
        confirmButton.setOnClickListener(v -> {
            String message = inputText.getText().toString().trim();
            if (message.isEmpty()) {
                Toast.makeText(this, "請輸入提示訊息", Toast.LENGTH_SHORT).show();
                return;
            }

            // 儲存到 SharedPreferences（讓 Service 讀取）
            SharedPreferences prefs = getSharedPreferences("reminder_prefs", MODE_PRIVATE);
            prefs.edit().putString("reminder_message", message).apply();

            Toast.makeText(this, "提示訊息已儲存", Toast.LENGTH_SHORT).show();
            inputText.setText("");
        });

        // 儲存 App 按鈕（現在才宣告！）
        Button saveAppsButton = findViewById(R.id.save_apps_button);
        saveAppsButton.setOnClickListener(v -> {
            if (adapter != null) {
                Set<String> selected = adapter.getSelectedPackages();
                SharedPreferences prefs = getSharedPreferences("reminder_prefs", MODE_PRIVATE);
                prefs.edit().putStringSet("selected_packages", selected).apply();
                Toast.makeText(this, "已儲存 " + selected.size() + " 個 App", Toast.LENGTH_SHORT).show();
            }
        });

        ProgressBar progress = findViewById(R.id.progress_bar);
        progress.setVisibility(View.VISIBLE);
        loadInstalledApps();
        progress.setVisibility(View.GONE);

        // 處理通知頻道與權限（只放在這裡一次）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            } else {
                createNotificationChannel();
            }
        } else {
            createNotificationChannel();
        }

        // 檢查無障礙服務
        // 在 onCreate 最後
        if (isFirstTimeAskAccessibility() && !isAccessibilityServiceEnabled()) {
            showAccessibilityDialog();
            markAccessibilityAsked();
        }

        // 搜尋輸入
        EditText searchApp = findViewById(R.id.search_app);
        searchApp.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (adapter != null) {
                    adapter.filter(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

    }

    private boolean isFirstTimeAskAccessibility() {
        SharedPreferences prefs = getSharedPreferences("reminder_prefs", MODE_PRIVATE);
        return prefs.getBoolean("first_time_accessibility", true);
    }

    private void markAccessibilityAsked() {
        SharedPreferences prefs = getSharedPreferences("reminder_prefs", MODE_PRIVATE);
        prefs.edit().putBoolean("first_time_accessibility", false).apply();
    }

    private void showAccessibilityDialog() {
        new AlertDialog.Builder(this)
                .setTitle("需要開啟無障礙服務")
                .setMessage("為了在您開啟指定 App 時顯示提醒，本應用需要「無障礙服務」權限。\n\n" +
                        "請按「前往設定」 → 找到「年輕人提示器」或「提醒服務」 → 開啟它。")
                .setPositiveButton("前往設定", (dialog, which) -> {
                    // 跳轉到無障礙服務設定頁面
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("稍後再說", (dialog, which) -> {
                    dialog.dismiss();
                    // 可選擇再顯示 Toast 提醒
                    Toast.makeText(this, "未開啟無障礙服務，提醒功能將無法使用", Toast.LENGTH_LONG).show();
                })
                .setCancelable(false)  // 建議設 false，避免用戶直接關掉
                .show();
    }


    // 檢查無障礙服務是否已啟用
    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

        for (AccessibilityServiceInfo service : enabledServices) {
            if (service.getId().equals(getPackageName() + "/.ReminderAccessibilityService")) {
                return true;
            }
        }
        return false;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        CharSequence name = "提醒通知";
        String description = "年輕人提示器的重要通知";
        int importance = NotificationManager.IMPORTANCE_HIGH;

        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);

        long[] vibrationPattern = {0, 1000, 500, 500};
        channel.setVibrationPattern(vibrationPattern);
        channel.enableVibration(true);

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        channel.setSound(defaultSoundUri, audioAttributes);

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    public static void showNotificationStatic(Context context, String pkgName) {
        SharedPreferences prefs = context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE);
        String message = prefs.getString("reminder_message", "該專注一下了～別一直玩手機喔！");

        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("提提你 !!!")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        // 用 pkgName 產生不同 id，避免覆蓋（可選）
        int notificationId = pkgName.hashCode();
        manager.notify(notificationId, builder.build());
    }
    private void loadInstalledApps() {
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);

        resolveInfoList.clear();

        for (ResolveInfo info : apps) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;

            resolveInfoList.add(info);
        }

        Collections.sort(resolveInfoList, (a, b) -> a.loadLabel(pm).toString().compareTo(b.loadLabel(pm).toString()));

        adapter = new AppRecyclerAdapter(this, resolveInfoList);
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isAccessibilityServiceEnabled()) {
            // 可以輕量提醒，例如 Toast 或小 banner
            Toast.makeText(this, "提醒功能需要無障礙服務，請開啟", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                createNotificationChannel();
                Toast.makeText(this, "通知權限已開啟", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "未開啟通知權限，提醒可能無法顯示", Toast.LENGTH_LONG).show();
            }
        }
    }
}