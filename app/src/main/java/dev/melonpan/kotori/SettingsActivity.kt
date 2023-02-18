package dev.melonpan.kotori

import android.content.SharedPreferences
import android.os.Bundle

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

import com.google.android.material.appbar.MaterialToolbar

class SettingsActivity : AppCompatActivity() {
    
    internal class SettingsFragment : PreferenceFragmentCompat() {
        
        private var themePref: Preference? = null
        
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.pref_general, rootKey)
            
            var sharedPreferences = preferenceScreen.getSharedPreferences()
            val currentTheme = sharedPreferences.getString("theme", "system")
            val themeValuesArray = resources.getStringArray(R.array.theme_values)
            val themesArray = resources.getStringArray(R.array.themes)
            
            var pref: Preference? = findPreference("version")

            if (pref != null) {
                pref.setSummary(BuildConfig.VERSION_NAME)
            }

            var prefAltitudeUnits: Preference? = findPreference("altitude_msl")

            if (prefAltitudeUnits != null) {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
                    prefAltitudeUnits.setEnabled(false)
                }
            }
            
            themePref = findPreference("theme")
            themePref?.setSummary(themesArray[themeValuesArray.indexOf(currentTheme)])
            
            themePref?.setOnPreferenceChangeListener { preference, newValue ->
                val value = newValue as String
                preference.setSummary(themesArray[themeValuesArray.indexOf(value)])
                when (value) {
                    "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    else -> {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                        } else {
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
                        }
                    }
                }
                true
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
