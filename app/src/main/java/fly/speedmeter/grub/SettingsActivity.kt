package fly.speedmeter.grub

import android.os.Bundle

import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

import com.google.android.material.appbar.MaterialToolbar

class SettingsActivity : AppCompatActivity() {
    internal class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.pref_general, rootKey)

            var pref: Preference? = findPreference("version")

            if (pref != null) {
                pref.setSummary(BuildConfig.VERSION_NAME)
            }
        }
    }
    override fun onCreate(savedInstance: Bundle?) {
        super.onCreate(savedInstance)
        setContentView(R.layout.activity_settings)

        val toolbar: MaterialToolbar = findViewById(R.id.settingstoolbar)

        setSupportActionBar(toolbar)
        getSupportActionBar()?.setHomeButtonEnabled(true)
        getSupportActionBar()?.setDisplayHomeAsUpEnabled(true)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.content_frame, SettingsFragment())
            .commit()
    }
}
