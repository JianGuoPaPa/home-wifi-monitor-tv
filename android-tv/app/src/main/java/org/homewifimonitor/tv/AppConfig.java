package org.homewifimonitor.tv;

final class AppConfig {
    static final long REFRESH_MS = 1_000L;
    static final long CLOCK_TICK_MS = 1_000L;
    static final long DEVICE_LIST_REFRESH_MS = 30_000L;
    static final long TOPO_REFRESH_MS = 30_000L;
    static final long STATUS_CACHE_MAX_STALE_MS = 15_000L;
    static final int ROUTER_LOGIN_TIMEOUT_MS = 12_000;
    static final int ROUTER_STATUS_TIMEOUT_MS = 5_000;
    static final int ROUTER_DEVICE_LIST_TIMEOUT_MS = 5_000;
    static final int ROUTER_TOPO_TIMEOUT_MS = 2_000;
    static final int DIAGNOSTIC_LOG_LIMIT = 8;

    static final int ROOT_HORIZONTAL_PADDING_DP = 14;
    static final int ROOT_VERTICAL_PADDING_DP = 8;
    static final int TITLE_SP = 20;
    static final int SUBTITLE_SP = 11;
    static final int STATUS_CARD_WIDTH_DP = 230;
    static final int METRICS_HEIGHT_DP = 76;
    static final int CONTROL_BUTTON_HEIGHT_DP = 32;
    static final int DEVICE_ROW_MIN_HEIGHT_DP = 38;
    static final int DEVICE_ROW_VERTICAL_PADDING_DP = 2;
    static final int AVATAR_SIZE_DP = 28;

    private AppConfig() {
    }
}
