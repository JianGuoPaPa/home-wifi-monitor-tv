package org.homewifimonitor.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String TAG = "WifiMonitor";
    private static final String PREFS = "router";
    private static final String PREF_HOST = "host";
    private static final String PREF_PASSWORD = "password";
    private static final String DEFAULT_HOST = "";
    private static final String DEVICE_ROW_TAG_PREFIX = "device:";

    private static final int BG = Color.rgb(246, 247, 249);
    private static final int SURFACE = Color.WHITE;
    private static final int SURFACE_SOFT = Color.rgb(238, 243, 248);
    private static final int INK = Color.rgb(20, 32, 51);
    private static final int MUTED = Color.rgb(102, 117, 138);
    private static final int LINE = Color.rgb(219, 227, 237);
    private static final int ACCENT = Color.rgb(19, 115, 230);
    private static final int GOOD = Color.rgb(15, 143, 97);
    private static final int WARN = Color.rgb(204, 122, 0);
    private static final int DANGER = Color.rgb(199, 62, 74);
    private static final int HEAVY_BG = Color.rgb(255, 248, 236);
    private static final int FOCUS_BG = Color.rgb(232, 241, 255);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
    private final List<Button> filterButtons = new ArrayList<>();
    private final DiagnosticsLog diagnosticsLog = new DiagnosticsLog(AppConfig.DIAGNOSTIC_LOG_LIMIT);

    private SharedPreferences prefs;
    private RouterClient client;
    private Snapshot currentSnapshot;
    private String filter = "all";
    private String focusedDeviceMac = "";
    private int consecutiveErrors;
    private volatile boolean refreshInFlight;

    private TextView subtitle;
    private TextView statusDot;
    private TextView statusText;
    private TextView sampledAt;
    private TextView currentTime;
    private TextView wanDown;
    private TextView wanUp;
    private TextView wanDownPeak;
    private TextView wanUpPeak;
    private TextView onlineCount;
    private TextView activeCount;
    private TextView meshCount;
    private TextView routerName;
    private ScrollView deviceScroll;
    private LinearLayout rowsContainer;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            refreshSnapshot();
            handler.postDelayed(this, AppConfig.REFRESH_MS);
        }
    };

    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, AppConfig.CLOCK_TICK_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        enterImmersiveMode();
        buildUi();

        if (savedHost().isEmpty() || savedPassword().isEmpty()) {
            renderIdle("首次使用需要输入路由器地址和管理密码");
            showSettingsDialog(false);
        } else {
            restartClient();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
        startClock();
        if (client != null) startPolling();
    }

    @Override
    protected void onPause() {
        stopPolling();
        stopClock();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopPolling();
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            showSettingsDialog(true);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_R || keyCode == KeyEvent.KEYCODE_REFRESH) {
            refreshSnapshot();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && !isDeviceRow(getCurrentFocus()) && moveFocusToFirstRow()) {
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN || keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
            return scrollDeviceList(1);
        }
        if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP || keyCode == KeyEvent.KEYCODE_PAGE_UP) {
            return scrollDeviceList(-1);
        }
        return super.onKeyDown(keyCode, event);
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(
                dp(AppConfig.ROOT_HORIZONTAL_PADDING_DP),
                dp(AppConfig.ROOT_VERTICAL_PADDING_DP),
                dp(AppConfig.ROOT_HORIZONTAL_PADDING_DP),
                dp(AppConfig.ROOT_VERTICAL_PADDING_DP)
        );
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        top.addView(titleBlock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = text("家庭 Wi-Fi 实时设备", AppConfig.TITLE_SP, INK, Typeface.BOLD);
        titleBlock.addView(title);
        subtitle = text("等待连接...", AppConfig.SUBTITLE_SP, MUTED, Typeface.NORMAL);
        subtitle.setPadding(0, dp(3), 0, 0);
        ellipsize(subtitle);
        titleBlock.addView(subtitle);

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.setPadding(dp(10), dp(5), dp(10), dp(5));
        statusCard.setBackground(box(SURFACE, dp(8), LINE, 1));
        top.addView(statusCard, new LinearLayout.LayoutParams(dp(AppConfig.STATUS_CARD_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT));

        statusDot = text("●", 18, WARN, Typeface.BOLD);
        statusCard.addView(statusDot, new LinearLayout.LayoutParams(dp(18), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout statusTexts = new LinearLayout(this);
        statusTexts.setOrientation(LinearLayout.VERTICAL);
        statusTexts.setPadding(dp(8), 0, 0, 0);
        statusCard.addView(statusTexts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statusText = text("待设置", 14, INK, Typeface.BOLD);
        sampledAt = text("数据 --", 11, MUTED, Typeface.NORMAL);
        currentTime = text("当前 --", 11, MUTED, Typeface.NORMAL);
        statusTexts.addView(statusText);
        statusTexts.addView(sampledAt);
        statusTexts.addView(currentTime);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setPadding(0, dp(10), 0, dp(8));
        root.addView(metrics, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(AppConfig.METRICS_HEIGHT_DP)
        ));

        Metric downMetric = metric("WAN 下载");
        wanDown = downMetric.value;
        wanDownPeak = downMetric.detail;
        metrics.addView(downMetric.container, metricParams());

        Metric upMetric = metric("WAN 上传");
        wanUp = upMetric.value;
        wanUpPeak = upMetric.detail;
        metrics.addView(upMetric.container, metricParams());

        Metric onlineMetric = metric("在线设备");
        onlineCount = onlineMetric.value;
        activeCount = onlineMetric.detail;
        metrics.addView(onlineMetric.container, metricParams());

        Metric meshMetric = metric("Mesh 节点");
        meshCount = meshMetric.value;
        routerName = meshMetric.detail;
        metrics.addView(meshMetric.container, metricParams());

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(0, 0, 0, dp(8));
        root.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(5), dp(5), dp(5), dp(5));
        tabs.setBackground(box(SURFACE_SOFT, dp(8), LINE, 1));
        controls.addView(tabs, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        tabs.addView(filterButton("全部", "all"));
        tabs.addView(filterButton("正在用网", "active"));
        tabs.addView(filterButton("高流量", "heavy"));
        tabs.addView(filterButton("Mesh 节点", "mesh"));

        Button refreshButton = actionButton("刷新");
        refreshButton.setOnClickListener(view -> refreshSnapshot());
        controls.addView(refreshButton, new LinearLayout.LayoutParams(dp(80), dp(AppConfig.CONTROL_BUTTON_HEIGHT_DP)));

        Button settingsButton = actionButton("设置");
        settingsButton.setOnClickListener(view -> showSettingsDialog(true));
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(80), dp(AppConfig.CONTROL_BUTTON_HEIGHT_DP));
        settingsParams.leftMargin = dp(8);
        controls.addView(settingsButton, settingsParams);

        LinearLayout table = new LinearLayout(this);
        table.setOrientation(LinearLayout.VERTICAL);
        table.setBackground(box(SURFACE, dp(8), LINE, 1));
        root.addView(table, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        table.addView(headerRow());

        deviceScroll = new ScrollView(this);
        deviceScroll.setFillViewport(false);
        deviceScroll.setFocusable(false);
        table.addView(deviceScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        rowsContainer = new LinearLayout(this);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        deviceScroll.addView(rowsContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        updateFilterButtons();
        if (!filterButtons.isEmpty()) filterButtons.get(0).requestFocus();
    }

    private void restartClient() {
        stopPolling();
        diagnosticsLog.add("client restart host=" + savedHost());
        Log.d(TAG, diagnosticsLog.latestLine());
        client = new RouterClient(savedHost(), savedPassword(), diagnosticsLog);
        currentSnapshot = null;
        consecutiveErrors = 0;
        renderIdle("连接 " + savedHost() + " 中...");
        startPolling();
    }

    private void startPolling() {
        handler.removeCallbacks(pollRunnable);
        handler.post(pollRunnable);
    }

    private void stopPolling() {
        handler.removeCallbacks(pollRunnable);
    }

    private void startClock() {
        handler.removeCallbacks(clockRunnable);
        updateClock();
        handler.postDelayed(clockRunnable, AppConfig.CLOCK_TICK_MS);
    }

    private void stopClock() {
        handler.removeCallbacks(clockRunnable);
    }

    private void updateClock() {
        if (currentTime != null) {
            currentTime.setText("当前 " + timeFormat.format(new Date()));
        }
    }

    private void refreshSnapshot() {
        if (client == null || refreshInFlight) return;
        refreshInFlight = true;
        executor.execute(() -> {
            try {
                Snapshot snapshot = client.snapshot();
                runOnUiThread(() -> renderSnapshot(snapshot));
            } catch (Exception error) {
                Log.w(TAG, "snapshot failed " + diagnosticsLog.latestLine(), error);
                runOnUiThread(() -> renderError(error));
            } finally {
                refreshInFlight = false;
            }
        });
    }

    private void renderSnapshot(Snapshot snapshot) {
        currentSnapshot = snapshot;
        consecutiveErrors = 0;
        statusDot.setTextColor(GOOD);
        statusText.setText("实时连接");
        sampledAt.setText("数据 " + timeFormat.format(new Date(snapshot.sampledAtMillis)));
        subtitle.setText((snapshot.routerName.isEmpty() ? "小米路由器" : snapshot.routerName) + " / " + snapshot.routerHost + diagnosticSuffix());
        wanDown.setText(formatMbps(snapshot.wan.downMbps));
        wanUp.setText(formatMbps(snapshot.wan.upMbps));
        wanDownPeak.setText("历史峰值 " + formatMbps(snapshot.wan.maxDownMbps));
        wanUpPeak.setText("历史峰值 " + formatMbps(snapshot.wan.maxUpMbps));
        onlineCount.setText(String.valueOf(snapshot.onlineCount));
        activeCount.setText("活跃 " + snapshot.activeCount + " / 列表 " + snapshot.totalCount);
        meshCount.setText(String.valueOf(snapshot.meshCount));
        routerName.setText(snapshot.routerName.isEmpty() ? "主路由" : snapshot.routerName);
        Log.d(TAG, "snapshot rendered " + diagnosticsLog.latestLine());
        renderRows();
    }

    private void renderIdle(String message) {
        statusDot.setTextColor(WARN);
        statusText.setText("待连接");
        sampledAt.setText("数据 --");
        subtitle.setText(message);
        wanDown.setText("--");
        wanUp.setText("--");
        wanDownPeak.setText("历史峰值 --");
        wanUpPeak.setText("历史峰值 --");
        onlineCount.setText("--");
        activeCount.setText("活跃 --");
        meshCount.setText("--");
        routerName.setText("--");
        rowsContainer.removeAllViews();
        rowsContainer.addView(emptyRow(message));
    }

    private void renderError(Exception error) {
        consecutiveErrors++;
        diagnosticsLog.add("ui error #" + consecutiveErrors + " " + shortUiError(error));
        boolean hasSnapshot = currentSnapshot != null && currentSnapshot.devices != null && !currentSnapshot.devices.isEmpty();
        statusDot.setTextColor(hasSnapshot ? WARN : DANGER);
        statusText.setText(hasSnapshot ? (consecutiveErrors >= 3 ? "更新延迟" : "正在重试") : "连接失败");
        sampledAt.setText(hasSnapshot
                ? "数据 " + timeFormat.format(new Date(currentSnapshot.sampledAtMillis))
                : "数据 " + timeFormat.format(new Date()));
        subtitle.setText(hasSnapshot
                ? "保留上次数据，正在重试" + diagnosticSuffix()
                : friendlyError(error) + diagnosticSuffix());
        if (!hasSnapshot) {
            rowsContainer.removeAllViews();
            rowsContainer.addView(emptyRow(subtitle.getText().toString()));
        }
    }

    private String friendlyError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = error.getClass().getSimpleName();
        }
        return "路由器更新失败：" + message;
    }

    private String shortUiError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = error.getClass().getSimpleName();
        }
        return message.length() > 80 ? message.substring(0, 80) : message;
    }

    private String diagnosticSuffix() {
        String latest = diagnosticsLog.latestLine();
        return latest.isEmpty() ? "" : " ｜ " + latest;
    }

    private void renderRows() {
        View focusedBefore = getCurrentFocus();
        boolean restoreRowFocus = isDeviceRow(focusedBefore);
        String restoreMac = restoreRowFocus ? rowMac(focusedBefore) : focusedDeviceMac;
        View restoreTarget = null;

        rowsContainer.removeAllViews();
        if (currentSnapshot == null) {
            rowsContainer.addView(emptyRow("等待路由器数据..."));
            return;
        }

        List<Device> visible = visibleDevices(currentSnapshot.devices);
        if (visible.isEmpty()) {
            rowsContainer.addView(emptyRow("没有匹配的在线设备"));
            return;
        }

        for (Device device : visible) {
            View row = deviceRow(device);
            rowsContainer.addView(row);
            if (restoreRowFocus && device.mac.equals(restoreMac)) {
                restoreTarget = row;
            }
        }

        if (restoreRowFocus) {
            View target = restoreTarget != null ? restoreTarget : rowsContainer.getChildAt(0);
            if (target != null) {
                target.post(target::requestFocus);
            }
        }
    }

    private List<Device> visibleDevices(List<Device> devices) {
        List<Device> visible = new ArrayList<>();
        for (Device device : devices) {
            if ("active".equals(filter) && !device.isActive) continue;
            if ("heavy".equals(filter) && Math.max(device.downMbps, device.upMbps) < 5) continue;
            if ("mesh".equals(filter) && !device.isMeshNode) continue;
            visible.add(device);
        }
        return visible;
    }

    private boolean moveFocusToFirstRow() {
        if (rowsContainer == null || rowsContainer.getChildCount() == 0) return false;
        View first = rowsContainer.getChildAt(0);
        if (first == null || !first.isFocusable()) return false;
        first.requestFocus();
        scrollRowIntoView(first);
        return true;
    }

    private boolean scrollDeviceList(int direction) {
        if (deviceScroll == null) return false;
        int distance = Math.max(dp(220), deviceScroll.getHeight() * 3 / 4);
        deviceScroll.smoothScrollBy(0, direction * distance);
        return true;
    }

    private void scrollRowIntoView(View row) {
        if (deviceScroll == null || row == null) return;
        deviceScroll.post(() -> {
            int top = row.getTop();
            int bottom = row.getBottom();
            int visibleTop = deviceScroll.getScrollY();
            int visibleBottom = visibleTop + deviceScroll.getHeight();
            if (top < visibleTop) {
                deviceScroll.smoothScrollTo(0, top);
            } else if (bottom > visibleBottom) {
                deviceScroll.smoothScrollTo(0, bottom - deviceScroll.getHeight());
            }
        });
    }

    private boolean isDeviceRow(View view) {
        return rowMac(view).length() > 0;
    }

    private String rowMac(View view) {
        if (view == null || !(view.getTag() instanceof String)) return "";
        String tag = (String) view.getTag();
        if (!tag.startsWith(DEVICE_ROW_TAG_PREFIX)) return "";
        return tag.substring(DEVICE_ROW_TAG_PREFIX.length());
    }

    private LinearLayout headerRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(3), dp(8), dp(3));
        row.setBackgroundColor(Color.rgb(251, 252, 254));
        row.addView(headerCell("设备", 2.2f));
        row.addView(headerCell("连接", 1.5f));
        row.addView(headerCell("下载", 1.0f));
        row.addView(headerCell("上传", 1.0f));
        row.addView(headerCell("历史峰值", 1.3f));
        row.addView(headerCell("IP / MAC", 1.8f));
        return row;
    }

    private View deviceRow(Device device) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setTag(DEVICE_ROW_TAG_PREFIX + device.mac);
        row.setPadding(dp(8), dp(AppConfig.DEVICE_ROW_VERTICAL_PADDING_DP), dp(8), dp(AppConfig.DEVICE_ROW_VERTICAL_PADDING_DP));
        row.setMinimumHeight(dp(AppConfig.DEVICE_ROW_MIN_HEIGHT_DP));
        boolean heavy = Math.max(device.downMbps, device.upMbps) >= 5;
        setRowBackground(row, heavy, false);
        row.setOnFocusChangeListener((view, hasFocus) -> {
            setRowBackground(row, heavy, hasFocus);
            if (hasFocus) {
                focusedDeviceMac = device.mac;
                scrollRowIntoView(row);
            }
        });

        row.addView(deviceCell(device), cellParams(2.2f));
        row.addView(singleLineCell(device.connectionLabel, device.isMeshNode ? GOOD : MUTED, Typeface.BOLD), cellParams(1.5f));
        row.addView(singleLineCell(formatMbps(device.downMbps), speedColor(device.downMbps), Typeface.BOLD), cellParams(1.0f));
        row.addView(singleLineCell(formatMbps(device.upMbps), speedColor(device.upMbps), Typeface.BOLD), cellParams(1.0f));
        row.addView(peakCell(device), cellParams(1.3f));
        row.addView(ipCell(device), cellParams(1.8f));
        return row;
    }

    private View deviceCell(Device device) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.HORIZONTAL);
        cell.setGravity(Gravity.CENTER_VERTICAL);

        TextView avatar = text(deviceGlyph(device), 11, Color.rgb(12, 84, 173), Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(box(Color.rgb(232, 241, 255), dp(8), Color.TRANSPARENT, 0));
        cell.addView(avatar, new LinearLayout.LayoutParams(dp(AppConfig.AVATAR_SIZE_DP), dp(AppConfig.AVATAR_SIZE_DP)));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(8), 0, dp(6), 0);
        cell.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = text(device.displayName, 12, INK, Typeface.BOLD);
        ellipsize(name);
        names.addView(name);

        String detail = device.originalName.isEmpty() ? (device.isActive ? "正在使用网络" : "空闲") : device.originalName;
        TextView original = text(detail, 9, MUTED, Typeface.NORMAL);
        original.setPadding(0, dp(1), 0, 0);
        ellipsize(original);
        names.addView(original);
        return cell;
    }

    private View peakCell(Device device) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        TextView down = text("下 " + formatMbps(device.maxDownMbps), 9, MUTED, Typeface.NORMAL);
        TextView up = text("上 " + formatMbps(device.maxUpMbps), 9, MUTED, Typeface.NORMAL);
        up.setPadding(0, dp(1), 0, 0);
        ellipsize(down);
        ellipsize(up);
        cell.addView(down);
        cell.addView(up);
        return cell;
    }

    private View ipCell(Device device) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        TextView ip = text(device.ip.isEmpty() ? "-" : device.ip, 9, INK, Typeface.NORMAL);
        TextView mac = text(device.mac.isEmpty() ? "-" : device.mac, 9, MUTED, Typeface.NORMAL);
        mac.setPadding(0, dp(1), 0, 0);
        ellipsize(ip);
        ellipsize(mac);
        cell.addView(ip);
        cell.addView(mac);
        return cell;
    }

    private TextView singleLineCell(String value, int color, int typeface) {
        TextView cell = text(value, 11, color, typeface);
        ellipsize(cell);
        return cell;
    }

    private TextView headerCell(String label, float weight) {
        TextView view = text(label, 10, MUTED, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setAllCaps(false);
        view.setLayoutParams(cellParams(weight));
        return view;
    }

    private View emptyRow(String message) {
        TextView empty = text(message, 12, MUTED, Typeface.NORMAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(16), dp(26), dp(16), dp(26));
        return empty;
    }

    private Button filterButton(String label, String value) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setOnClickListener(view -> {
            filter = value;
            updateFilterButtons();
            renderRows();
        });
        button.setOnFocusChangeListener((view, hasFocus) -> updateFilterButtons());
        button.setFocusable(true);
        button.setTag(value);
        filterButtons.add(button);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(AppConfig.CONTROL_BUTTON_HEIGHT_DP)
        );
        params.rightMargin = dp(4);
        button.setLayoutParams(params);
        return button;
    }

    private void updateFilterButtons() {
        for (Button button : filterButtons) {
            boolean active = filter.equals(button.getTag());
            boolean focused = button.hasFocus();
            button.setTextColor(active ? Color.WHITE : focused ? ACCENT : MUTED);
            button.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
            button.setBackground(box(active ? ACCENT : focused ? FOCUS_BG : Color.TRANSPARENT, dp(6), focused ? ACCENT : Color.TRANSPARENT, focused ? 2 : 0));
        }
    }

    private Button actionButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        button.setTextColor(INK);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setFocusable(true);
        button.setOnFocusChangeListener((view, hasFocus) -> setActionButtonBackground(button, hasFocus));
        setActionButtonBackground(button, false);
        return button;
    }

    private Metric metric(String label) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(8), dp(5), dp(8), dp(5));
        container.setBackground(box(SURFACE, dp(8), LINE, 1));

        TextView title = text(label, 10, MUTED, Typeface.NORMAL);
        TextView value = text("--", 18, INK, Typeface.BOLD);
        value.setPadding(0, dp(2), 0, 0);
        TextView detail = text("--", 9, MUTED, Typeface.NORMAL);
        ellipsize(detail);
        container.addView(title);
        container.addView(value);
        container.addView(detail);
        return new Metric(container, value, detail);
    }

    private void showSettingsDialog(boolean allowCancel) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(4);
        form.setPadding(pad, pad, pad, 0);

        TextView help = text("电视会直接登录路由器读取设备速率；密码只保存在电视本机。", 14, MUTED, Typeface.NORMAL);
        help.setPadding(0, 0, 0, dp(12));
        form.addView(help);

        TextView diagnostics = text("诊断日志\n" + diagnosticsLog.snapshotText(), 11, MUTED, Typeface.NORMAL);
        diagnostics.setPadding(0, 0, 0, dp(12));
        form.addView(diagnostics);

        TextView hostLabel = text("路由器地址", 13, MUTED, Typeface.BOLD);
        form.addView(hostLabel);
        EditText hostInput = new EditText(this);
        hostInput.setSingleLine(true);
        hostInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        hostInput.setHint("router-host.local");
        hostInput.setText(savedHost());
        hostInput.setSelectAllOnFocus(true);
        form.addView(hostInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView passwordLabel = text("管理密码", 13, MUTED, Typeface.BOLD);
        passwordLabel.setPadding(0, dp(12), 0, 0);
        form.addView(passwordLabel);
        EditText passwordInput = new EditText(this);
        passwordInput.setSingleLine(true);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setText(savedPassword());
        passwordInput.setSelectAllOnFocus(true);
        form.addView(passwordInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("路由器设置")
                .setView(form)
                .setPositiveButton("保存并连接", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setCancelable(allowCancel);
        dialog.setCanceledOnTouchOutside(allowCancel);
        dialog.setOnShowListener(d -> {
            if (!allowCancel) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setVisibility(View.GONE);
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String host = hostInput.getText().toString().trim();
                String password = passwordInput.getText().toString();
                if (host.isEmpty()) {
                    hostInput.setError("请输入路由器地址");
                    hostInput.requestFocus();
                    return;
                }
                if (password.trim().isEmpty()) {
                    passwordInput.setError("请输入管理密码");
                    passwordInput.requestFocus();
                    return;
                }
                prefs.edit()
                        .putString(PREF_HOST, host)
                        .putString(PREF_PASSWORD, password)
                        .apply();
                hideKeyboard(passwordInput);
                dialog.dismiss();
                restartClient();
            });
            passwordInput.requestFocus();
            passwordInput.postDelayed(() -> {
                InputMethodManager input = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (input != null) input.showSoftInput(passwordInput, InputMethodManager.SHOW_IMPLICIT);
            }, 250);
        });
        dialog.show();
    }

    private String savedHost() {
        return prefs.getString(PREF_HOST, DEFAULT_HOST);
    }

    private String savedPassword() {
        return prefs.getString(PREF_PASSWORD, "");
    }

    private void hideKeyboard(View view) {
        InputMethodManager input = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (input != null) input.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private static String formatMbps(double value) {
        if (value >= 100) return String.valueOf(Math.round(value)) + " Mbps";
        if (value >= 10) return String.format(Locale.CHINA, "%.1f Mbps", value);
        if (value >= 1) return String.format(Locale.CHINA, "%.2f Mbps", value);
        if (value > 0) return String.valueOf(Math.round(value * 1000)) + " Kbps";
        return "0 Kbps";
    }

    private static int speedColor(double value) {
        if (value >= 5) return WARN;
        if (value > 0) return INK;
        return MUTED;
    }

    private static String deviceGlyph(Device device) {
        if (device.isMeshNode) return "路";
        String name = (device.displayName + " " + device.originalName).toLowerCase(Locale.ROOT);
        if (name.contains("电视") || name.contains("tv")) return "TV";
        if (name.contains("mac")) return "Mac";
        if (name.contains("iphone")) return "iP";
        if (name.contains("ipad")) return "iPad";
        if (name.contains("camera")) return "Cam";
        return "Wi";
    }

    private TextView text(String value, int sp, int color, int typeface) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, typeface);
        view.setIncludeFontPadding(true);
        return view;
    }

    private void ellipsize(TextView view) {
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
    }

    private LinearLayout.LayoutParams metricParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.rightMargin = dp(8);
        return params;
    }

    private LinearLayout.LayoutParams cellParams(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    private void setRowBackground(View view, boolean heavy, boolean focused) {
        int color = focused ? FOCUS_BG : heavy ? HEAVY_BG : SURFACE;
        int stroke = focused ? ACCENT : LINE;
        view.setBackground(box(color, 0, stroke, focused ? 2 : 1));
    }

    private void setActionButtonBackground(Button button, boolean focused) {
        button.setTextColor(focused ? ACCENT : INK);
        button.setTypeface(Typeface.DEFAULT, focused ? Typeface.BOLD : Typeface.NORMAL);
        button.setBackground(box(focused ? FOCUS_BG : SURFACE, dp(8), focused ? ACCENT : LINE, focused ? 2 : 1));
    }

    private GradientDrawable box(int color, int radiusPx, int strokeColor, int strokeWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        if (strokeWidthDp > 0 && strokeColor != Color.TRANSPARENT) {
            drawable.setStroke(dp(strokeWidthDp), strokeColor);
        }
        return drawable;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        ));
    }

    private static final class Metric {
        final LinearLayout container;
        final TextView value;
        final TextView detail;

        Metric(LinearLayout container, TextView value, TextView detail) {
            this.container = container;
            this.value = value;
            this.detail = detail;
        }
    }
}
