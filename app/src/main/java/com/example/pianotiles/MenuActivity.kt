package com.example.pianotiles

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.res.Resources
import java.util.Locale

class MenuActivity : AppCompatActivity() {

    private fun applyLanguage(langKey: String) {
        val locale = when (langKey) {
            "en" -> Locale("en")
            "zh" -> Locale.SIMPLIFIED_CHINESE
            else -> Resources.getSystem().configuration.locales[0]
        }
        val config = resources.configuration
        val dm = resources.displayMetrics
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, dm)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("menu_prefs", MODE_PRIVATE)
        // 先应用已保存语言，再加载布局，确保初次进入即为正确语言
        applyLanguage(prefs.getString("language", "system") ?: "system")
        super.onCreate(savedInstanceState)
    enableImmersive()
        setContentView(R.layout.activity_menu)

        val rgLanes: RadioGroup = findViewById(R.id.rg_lanes)
        val laneDefault = prefs.getInt("lanes", 4)
        when (laneDefault) {
            3 -> rgLanes.check(R.id.rb_lane3)
            5 -> rgLanes.check(R.id.rb_lane5)
            else -> rgLanes.check(R.id.rb_lane4)
        }

        val diffKeys = resources.getStringArray(R.array.difficulty_keys)
        val savedDiff = prefs.getString("diff", "Normal") ?: "Normal"
        when (savedDiff) {
            "Easy" -> findViewById<RadioButton>(R.id.rb_diff_easy).isChecked = true
            "Hard" -> findViewById<RadioButton>(R.id.rb_diff_hard).isChecked = true
            else -> findViewById<RadioButton>(R.id.rb_diff_normal).isChecked = true
        }

        val cbHaptics: CheckBox = findViewById(R.id.cb_haptics)
        val cbSound: CheckBox = findViewById(R.id.cb_sound)
    val cbUseBeat: CheckBox = findViewById(R.id.cb_use_beat)
    val etBpm: EditText = findViewById(R.id.et_bpm)
    val cbUsePattern: CheckBox = findViewById(R.id.cb_use_pattern)
    val etSeed: EditText = findViewById(R.id.et_seed)
        val themeSpinner: Spinner = findViewById(R.id.spinner_theme)
        val themeKeys = resources.getStringArray(R.array.theme_keys)
        val themeLabels = resources.getStringArray(R.array.theme_labels)
    themeSpinner.adapter = ArrayAdapter(
            this, R.layout.spinner_item_light,
            themeLabels
        ).also { it.setDropDownViewResource(R.layout.spinner_dropdown_light) }
        cbHaptics.isChecked = prefs.getBoolean("haptics", true)
        cbSound.isChecked = prefs.getBoolean("sound", false)
    cbUseBeat.isChecked = prefs.getBoolean("useBeat", false)
    etBpm.setText(prefs.getFloat("bpm", 120f).toString())
    cbUsePattern.isChecked = prefs.getBoolean("usePattern", false)
    etSeed.setText(prefs.getLong("seed", 0L).toString())
    val savedTheme = prefs.getString("theme", "System")
    val themeIndex = themeKeys.indexOf(savedTheme).coerceAtLeast(0)
    themeSpinner.setSelection(themeIndex)

        // Language spinner
        val languageSpinner: Spinner = findViewById(R.id.spinner_language)
        val langKeys = resources.getStringArray(R.array.language_keys)
        val langLabels = resources.getStringArray(R.array.language_labels)
    languageSpinner.adapter = ArrayAdapter(
            this, R.layout.spinner_item_light,
            langLabels
        ).also { it.setDropDownViewResource(R.layout.spinner_dropdown_light) }
        val savedLang = prefs.getString("language", "system")
        val langIndex = langKeys.indexOf(savedLang).coerceAtLeast(0)
        languageSpinner.setSelection(langIndex)

        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val langKey = langKeys[position]
                val prev = prefs.getString("language", "system")
                if (prev != langKey) {
                    prefs.edit().putString("language", langKey).apply()
                    applyLanguage(langKey)
                    // 重新加载菜单界面让文案立即生效
                    recreate()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        findViewById<Button>(R.id.btn_start).setOnClickListener {
            val lanes = when (rgLanes.checkedRadioButtonId) {
                R.id.rb_lane3 -> 3
                R.id.rb_lane5 -> 5
                else -> 4
            }
            val diff = when (findViewById<RadioGroup>(R.id.rg_diff).checkedRadioButtonId) {
                R.id.rb_diff_easy -> "Easy"
                R.id.rb_diff_hard -> "Hard"
                else -> "Normal"
            }
            val useBeat = cbUseBeat.isChecked
            val bpm = etBpm.text.toString().toFloatOrNull() ?: 120f
            val theme = themeKeys[themeSpinner.selectedItemPosition]
            // 应用主题
            when (theme) {
                "Light" -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
                "Dark" -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
                else -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            prefs.edit()
                .putInt("lanes", lanes)
                .putString("diff", diff)
                .putBoolean("haptics", cbHaptics.isChecked)
                .putBoolean("sound", cbSound.isChecked)
                .putBoolean("useBeat", useBeat)
                .putFloat("bpm", bpm)
                .putString("theme", theme)
                .putBoolean("usePattern", cbUsePattern.isChecked)
                .putLong("seed", etSeed.text.toString().toLongOrNull() ?: 0L)
                .apply()
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("lanes", lanes)
            intent.putExtra("difficulty", diff)
            intent.putExtra("haptics", cbHaptics.isChecked)
            intent.putExtra("sound", cbSound.isChecked)
            intent.putExtra("useBeat", useBeat)
            intent.putExtra("bpm", bpm)
            intent.putExtra("usePattern", cbUsePattern.isChecked)
            intent.putExtra("seed", etSeed.text.toString().toLongOrNull() ?: 0L)
            startActivity(intent)
        }
        findViewById<Button>(R.id.btn_stats).setOnClickListener {
            startActivity(android.content.Intent(this, StatsActivity::class.java))
        }
    }

    private fun enableImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersive()
    }

    override fun onResume() {
        super.onResume()
        enableImmersive()
    }
}
