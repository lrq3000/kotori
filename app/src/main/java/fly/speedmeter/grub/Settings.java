package fly.speedmeter.grub;

import android.os.Bundle;
import androidx.preference.PreferenceFragmentCompat;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Created by fly on 19/04/15.
 */
public class Settings extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        androidx.appcompat.widget.Toolbar toolbar = (androidx.appcompat.widget.Toolbar) findViewById(R.id.settingstoolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Display the fragment as the main content.
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.content_frame, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.pref_general, rootKey);
        }
    }
}


