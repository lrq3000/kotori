package fly.speedmeter.grub;

public interface PositioningStatus {
    
    interface Listener {
        void onStatusChanged(int event);
    }
    
    boolean isProviderEnabled();
    
    int getSatelliteCount();
    
    int getSatellitesUsed();
    
    void setListener(Listener listener);
    
    void unregister();
}