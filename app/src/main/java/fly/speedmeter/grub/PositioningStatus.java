package fly.speedmeter.grub;

public interface PositioningStatus {
    boolean enabled();
    
    int getSatelliteCount();
    
    int getSatellitesUsed();
    
    void update();
    
    void unregister();
}