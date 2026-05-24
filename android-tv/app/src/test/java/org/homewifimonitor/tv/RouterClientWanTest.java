package org.homewifimonitor.tv;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public final class RouterClientWanTest {
    @Test
    public void usesRouterWanStatsWhenStatusProvidesThem() throws Exception {
        JSONObject status = new JSONObject("{\"wanStatistics\":{\"downspeed\":1000000,\"upspeed\":500000}}");

        WanStats stats = RouterClient.summarizeWan(status, Arrays.asList(device(10_000_000, 10_000_000)));

        assertEquals(8.0, stats.downMbps, 0.001);
        assertEquals(4.0, stats.upMbps, 0.001);
    }

    @Test
    public void fallsBackToSumOfDeviceRatesWhenStatusTimesOut() {
        WanStats stats = RouterClient.summarizeWan(new JSONObject(), Arrays.asList(
                device(750_000, 250_000),
                device(375_000, 250_000)
        ));

        assertEquals(9.0, stats.downMbps, 0.001);
        assertEquals(4.0, stats.upMbps, 0.001);
    }

    private static Device device(double downBps, double upBps) {
        Device device = new Device();
        device.downBps = downBps;
        device.upBps = upBps;
        return device;
    }
}
