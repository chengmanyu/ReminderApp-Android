package com.example.reminderapp;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import java.util.Collections;
import java.util.Set;

public class ReminderAccessibilityService extends AccessibilityService {
    private static final String TAG = "ReminderService";
    private long lastTriggerTime = 0;
    private static final long MIN_GAP_MS = 6000; // 6 秒

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        String currentPkg = getCurrentPackageName(event);
        if (currentPkg == null) return;

        Log.d(TAG, "前景 App: " + currentPkg);

        // 讀取使用者設定的清單
        SharedPreferences prefs = getSharedPreferences("reminder_prefs", MODE_PRIVATE);
        Set<String> selected = prefs.getStringSet("selected_packages", Collections.emptySet());
        Log.d(TAG, "目前選擇的 App 清單大小: " + selected.size());
        Log.d(TAG, "選擇的 App: " + selected.toString());

        String msg = prefs.getString("reminder_message", "預設訊息");
        Log.d(TAG, "目前設定的提醒訊息: " + msg);

        if (selected.contains(currentPkg)) {
            long now = System.currentTimeMillis();
            if (now - lastTriggerTime >= MIN_GAP_MS) {
                lastTriggerTime = now;
                Log.d(TAG, "觸發通知！App: " + currentPkg);

                MainActivity.showNotificationStatic(getApplicationContext(), currentPkg);
            }
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "服務中斷");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "服務已連線");
    }

    // 從 event 取 package name 的 helper 方法
    private String getCurrentPackageName(AccessibilityEvent event) {
        CharSequence pkg = event.getPackageName();
        return (pkg != null) ? pkg.toString() : null;
    }
}
