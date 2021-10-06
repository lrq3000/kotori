package fly.speedmeter.grub;

import android.content.Context;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.Manifest;

import androidx.core.content.PermissionChecker;

public class GnssStatusNg extends GnssStatus.Callback implements PositioningStatus {
    
    boolean mEnabled;
    int satellitesUsed;
    int satelliteCount;
    
    private Context mContext;
    private LocationManager mLocationManager;
    
    public GnssStatusNg(Context mContext) {
        setupObjects();
    }
    
    private void setupObjects() {
        if (PermissionChecker.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
            != PermissionChecker.PERMISSION_GRANTED) {
            return;
        }
        
        mLocationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        mLocationManager.registerGnssStatusCallback(mContext.getMainExecutor(), this);
    }
    
    @Override
    public boolean enabled() {
        return mEnabled;
    }
    
    @Override
    public int getSatelliteCount() {
        return satelliteCount;
    }
    
    @Override
    public int getSatellitesUsed() {
        return satellitesUsed;
    }
    
    @Override
    public void unregister() {
        mLocationManager.unregisterGnssStatusCallback(this);
    }
    
    @Override
    public void update() { }
    
    @Override
    public void onSatelliteStatusChanged(GnssStatus status) {
        satelliteCount = status.getSatelliteCount();
        
        for (int i = 0; i < satelliteCount; i++) {
            if (status.usedInFix(i)) {
                satellitesUsed++;
            }
        }
        update();
    }
    
    @Override
    public void onStarted() {
        mEnabled = true;
        update();
    }
    
    @Override
    public void onStopped() {
        mEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        update();
    }
}
