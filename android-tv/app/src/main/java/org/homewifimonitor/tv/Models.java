package org.homewifimonitor.tv;

import java.util.ArrayList;
import java.util.List;

final class Device {
    String mac = "";
    String displayName = "";
    String originalName = "";
    String ip = "";
    String connectionLabel = "";
    double downBps;
    double upBps;
    double downMbps;
    double upMbps;
    double maxDownMbps;
    double maxUpMbps;
    boolean isMeshNode;
    boolean isActive;
}

final class WanStats {
    double downMbps;
    double upMbps;
    double maxDownMbps;
    double maxUpMbps;
}

final class Snapshot {
    String routerHost = "";
    String routerName = "";
    long sampledAtMillis;
    int totalCount;
    int onlineCount;
    int activeCount;
    int wifiCount;
    int meshCount;
    WanStats wan = new WanStats();
    List<Device> devices = new ArrayList<>();
}
