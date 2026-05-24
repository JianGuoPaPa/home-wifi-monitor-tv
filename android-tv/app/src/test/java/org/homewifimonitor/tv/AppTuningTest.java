package org.homewifimonitor.tv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class AppTuningTest {
    @Test
    public void tvRefreshesEverySecond() {
        assertEquals(1_000L, AppConfig.REFRESH_MS);
    }

    @Test
    public void clockTicksEverySecond() {
        assertEquals(1_000L, AppConfig.CLOCK_TICK_MS);
    }

    @Test
    public void tableUsesCompactTvRows() {
        assertTrue(AppConfig.DEVICE_ROW_MIN_HEIGHT_DP <= 50);
        assertTrue(AppConfig.DEVICE_ROW_VERTICAL_PADDING_DP <= 5);
        assertTrue(AppConfig.AVATAR_SIZE_DP <= 34);
    }

    @Test
    public void loginAllowsSlowTvWifiAndRouterUi() {
        assertTrue(AppConfig.ROUTER_LOGIN_TIMEOUT_MS >= 10_000);
        assertTrue(AppConfig.ROUTER_LOGIN_TIMEOUT_MS <= 12_000);
    }

    @Test
    public void steadyStateCallsStayBoundedForOneSecondPolling() {
        assertTrue(AppConfig.ROUTER_STATUS_TIMEOUT_MS <= 5_000);
        assertTrue(AppConfig.ROUTER_DEVICE_LIST_TIMEOUT_MS <= 6_000);
        assertTrue(AppConfig.ROUTER_TOPO_TIMEOUT_MS <= 3_000);
        assertTrue(AppConfig.TOPO_REFRESH_MS >= 30_000);
    }

    @Test
    public void deviceListIsNotFetchedEverySecond() {
        assertTrue(AppConfig.DEVICE_LIST_REFRESH_MS >= 15_000);
        assertTrue(AppConfig.DEVICE_LIST_REFRESH_MS > AppConfig.REFRESH_MS);
    }
}
