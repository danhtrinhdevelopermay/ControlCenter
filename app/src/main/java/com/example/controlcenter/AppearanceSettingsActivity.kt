package com.example.controlcenter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class AppearanceSettingsActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView
    private lateinit var resetAppearanceBtn: Button
    private lateinit var applyAppearanceBtn: Button

    private lateinit var buttonColorInput: EditText
    private lateinit var buttonColorPreview: View
    private lateinit var buttonOpacitySeekBar: SeekBar
    private lateinit var buttonOpacityValue: TextView
    private lateinit var buttonActiveColorInput: EditText
    private lateinit var buttonActiveColorPreview: View
    private lateinit var buttonActiveOpacitySeekBar: SeekBar
    private lateinit var buttonActiveOpacityValue: TextView

    private lateinit var toggleColorInput: EditText
    private lateinit var toggleColorPreview: View
    private lateinit var toggleOpacitySeekBar: SeekBar
    private lateinit var toggleOpacityValue: TextView
    private lateinit var toggleActiveColorInput: EditText
    private lateinit var toggleActiveColorPreview: View
    private lateinit var toggleActiveOpacitySeekBar: SeekBar
    private lateinit var toggleActiveOpacityValue: TextView

    private lateinit var playerColorInput: EditText
    private lateinit var playerColorPreview: View
    private lateinit var playerOpacitySeekBar: SeekBar
    private lateinit var playerOpacityValue: TextView

    private lateinit var sliderColorInput: EditText
    private lateinit var sliderColorPreview: View
    private lateinit var sliderOpacitySeekBar: SeekBar
    private lateinit var sliderOpacityValue: TextView
    private lateinit var sliderFillColorInput: EditText
    private lateinit var sliderFillColorPreview: View
    private lateinit var sliderFillOpacitySeekBar: SeekBar
    private lateinit var sliderFillOpacityValue: TextView

    private lateinit var notificationColorInput: EditText
    private lateinit var notificationColorPreview: View
    private lateinit var notificationOpacitySeekBar: SeekBar
    private lateinit var notificationOpacityValue: TextView
    private lateinit var notificationHeaderColorInput: EditText
    private lateinit var notificationHeaderColorPreview: View
    private lateinit var notificationHeaderOpacitySeekBar: SeekBar
    private lateinit var notificationHeaderOpacityValue: TextView

    private lateinit var buttonCornerRadiusSeekBar: SeekBar
    private lateinit var buttonCornerRadiusValue: TextView
    private lateinit var notificationCornerRadiusSeekBar: SeekBar
    private lateinit var notificationCornerRadiusValue: TextView
    
    private lateinit var controlCenterBlurSeekBar: SeekBar
    private lateinit var controlCenterBlurValue: TextView
    private lateinit var notificationCenterBlurSeekBar: SeekBar
    private lateinit var notificationCenterBlurValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_appearance_settings)

        initViews()
        loadSettings()
        setupListeners()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        resetAppearanceBtn = findViewById(R.id.resetAppearanceBtn)
        applyAppearanceBtn = findViewById(R.id.applyAppearanceBtn)

        buttonColorInput = findViewById(R.id.buttonColorInput)
        buttonColorPreview = findViewById(R.id.buttonColorPreview)
        buttonOpacitySeekBar = findViewById(R.id.buttonOpacitySeekBar)
        buttonOpacityValue = findViewById(R.id.buttonOpacityValue)
        buttonActiveColorInput = findViewById(R.id.buttonActiveColorInput)
        buttonActiveColorPreview = findViewById(R.id.buttonActiveColorPreview)
        buttonActiveOpacitySeekBar = findViewById(R.id.buttonActiveOpacitySeekBar)
        buttonActiveOpacityValue = findViewById(R.id.buttonActiveOpacityValue)

        toggleColorInput = findViewById(R.id.toggleColorInput)
        toggleColorPreview = findViewById(R.id.toggleColorPreview)
        toggleOpacitySeekBar = findViewById(R.id.toggleOpacitySeekBar)
        toggleOpacityValue = findViewById(R.id.toggleOpacityValue)
        toggleActiveColorInput = findViewById(R.id.toggleActiveColorInput)
        toggleActiveColorPreview = findViewById(R.id.toggleActiveColorPreview)
        toggleActiveOpacitySeekBar = findViewById(R.id.toggleActiveOpacitySeekBar)
        toggleActiveOpacityValue = findViewById(R.id.toggleActiveOpacityValue)

        playerColorInput = findViewById(R.id.playerColorInput)
        playerColorPreview = findViewById(R.id.playerColorPreview)
        playerOpacitySeekBar = findViewById(R.id.playerOpacitySeekBar)
        playerOpacityValue = findViewById(R.id.playerOpacityValue)

        sliderColorInput = findViewById(R.id.sliderColorInput)
        sliderColorPreview = findViewById(R.id.sliderColorPreview)
        sliderOpacitySeekBar = findViewById(R.id.sliderOpacitySeekBar)
        sliderOpacityValue = findViewById(R.id.sliderOpacityValue)
        sliderFillColorInput = findViewById(R.id.sliderFillColorInput)
        sliderFillColorPreview = findViewById(R.id.sliderFillColorPreview)
        sliderFillOpacitySeekBar = findViewById(R.id.sliderFillOpacitySeekBar)
        sliderFillOpacityValue = findViewById(R.id.sliderFillOpacityValue)

        notificationColorInput = findViewById(R.id.notificationColorInput)
        notificationColorPreview = findViewById(R.id.notificationColorPreview)
        notificationOpacitySeekBar = findViewById(R.id.notificationOpacitySeekBar)
        notificationOpacityValue = findViewById(R.id.notificationOpacityValue)
        notificationHeaderColorInput = findViewById(R.id.notificationHeaderColorInput)
        notificationHeaderColorPreview = findViewById(R.id.notificationHeaderColorPreview)
        notificationHeaderOpacitySeekBar = findViewById(R.id.notificationHeaderOpacitySeekBar)
        notificationHeaderOpacityValue = findViewById(R.id.notificationHeaderOpacityValue)
        
        buttonCornerRadiusSeekBar = findViewById(R.id.buttonCornerRadiusSeekBar)
        buttonCornerRadiusValue = findViewById(R.id.buttonCornerRadiusValue)
        notificationCornerRadiusSeekBar = findViewById(R.id.notificationCornerRadiusSeekBar)
        notificationCornerRadiusValue = findViewById(R.id.notificationCornerRadiusValue)
        
        controlCenterBlurSeekBar = findViewById(R.id.controlCenterBlurSeekBar)
        controlCenterBlurValue = findViewById(R.id.controlCenterBlurValue)
        notificationCenterBlurSeekBar = findViewById(R.id.notificationCenterBlurSeekBar)
        notificationCenterBlurValue = findViewById(R.id.notificationCenterBlurValue)
    }

    private fun loadSettings() {
        buttonColorInput.setText(AppearanceSettings.colorToHex(AppearanceSettings.getButtonColor(this)))
        buttonOpacitySeekBar.progress = AppearanceSettings.getButtonOpacity(this)
        buttonOpacityValue.text = "${AppearanceSettings.getButtonOpacity(this)}%"
        buttonActiveColorInput.setText(AppearanceSettings.colorToHex(AppearanceSettings.getButtonActiveColor(this)))
        buttonActiveOpacitySeekBar.progress = AppearanceSettings.getButtonActiveOpacity(this)
        buttonActiveOpacityValue.text = "${AppearanceSettings.getButtonActiveOpacity(this)}%"

        toggleColorInput.setText(AppearanceSettings.colorToHex(AppearanceSettings.getToggleColor(this)))
        toggleOpacitySeekBar.progress = AppearanceSettings.getToggleOpacity(this)
        toggleOpacityValue.text = "${AppearanceSettings.getToggleOpacity(this)}%"
        toggleActiveColorInput.setText(AppearanceSettings.colorToHex(AppearanceSettings.getToggleActiveColor(this)))
        toggleActiveOpacitySeekBar.progress = AppearanceSettings.getToggleActiveOpacity(this)
        toggleActiveOpacityValue.text = "${AppearanceSettings.getToggleActiveOpacity(this)}%"

        playerColorInput.setText(AppearanceSettings.colorToHex(AppearanceSettings.getPlayerColor(this)))
        playerOpacitySeekBar.progress = AppearanceSettings.getPlayerOpacity(this)
        playerOpacityValue.text = "${AppearanceSettings.getPlayerOpacity(this)}%"

        sliderColorInput.setText(AppearanceSettings.colorToHex(AppearanceSettings.getSliderColor(this)))
        sliderOpacitySeekBar.progress = AppearanceSettings.getSliderOpacity(this)
        sliderOpacityValue.text = "${AppearanceSettings.getSliderOpacity(this)}%"
        sliderFillColorInput.setText(AppearanceSettings.colorToHex(AppearanceSettings.getSliderFillColor(this)))
        sliderFillOpacitySeekBar.progress = AppearanceSettings.getSliderFillOpacity(this)
        sliderFillOpacityValue.text = "${AppearanceSettings.getSliderFillOpacity(this)}%"

        notificationColorInput.setText(AppearanceSettings.colorToHex(AppearanceSettings.getNotificationColor(this)))
        notificationOpacitySeekBar.progress = AppearanceSettings.getNotificationOpacity(this)
        notificationOpacityValue.text = "${AppearanceSettings.getNotificationOpacity(this)}%"
        notificationHeaderColorInput.setText(AppearanceSettings.colorToHex(AppearanceSettings.getNotificationHeaderColor(this)))
        notificationHeaderOpacitySeekBar.progress = AppearanceSettings.getNotificationHeaderOpacity(this)
        notificationHeaderOpacityValue.text = "${AppearanceSettings.getNotificationHeaderOpacity(this)}%"

        buttonCornerRadiusSeekBar.progress = AppearanceSettings.getButtonCornerRadius(this)
        buttonCornerRadiusValue.text = "${AppearanceSettings.getButtonCornerRadius(this)}dp"
        notificationCornerRadiusSeekBar.progress = AppearanceSettings.getNotificationCornerRadius(this)
        notificationCornerRadiusValue.text = "${AppearanceSettings.getNotificationCornerRadius(this)}dp"
        
        controlCenterBlurSeekBar.progress = AppearanceSettings.getControlCenterBlur(this)
        controlCenterBlurValue.text = "${AppearanceSettings.getControlCenterBlur(this)}%"
        notificationCenterBlurSeekBar.progress = AppearanceSettings.getNotificationCenterBlur(this)
        notificationCenterBlurValue.text = "${AppearanceSettings.getNotificationCenterBlur(this)}%"

        updateAllPreviews()
    }

    private fun setupListeners() {
        backButton.setOnClickListener { finish() }

        resetAppearanceBtn.setOnClickListener {
            AppearanceSettings.resetToDefaults(this)
            loadSettings()
            Toast.makeText(this, "Reset to default settings", Toast.LENGTH_SHORT).show()
        }

        applyAppearanceBtn.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Settings applied", Toast.LENGTH_SHORT).show()
            finish()
        }

        setupColorInputListener(buttonColorInput) { color ->
            AppearanceSettings.setButtonColor(this, color)
            updateButtonPreview()
        }

        setupSeekBarListener(buttonOpacitySeekBar, buttonOpacityValue) { value ->
            AppearanceSettings.setButtonOpacity(this, value)
            updateButtonPreview()
        }

        setupColorInputListener(buttonActiveColorInput) { color ->
            AppearanceSettings.setButtonActiveColor(this, color)
            updateButtonActivePreview()
        }

        setupSeekBarListener(buttonActiveOpacitySeekBar, buttonActiveOpacityValue) { value ->
            AppearanceSettings.setButtonActiveOpacity(this, value)
            updateButtonActivePreview()
        }

        setupColorInputListener(toggleColorInput) { color ->
            AppearanceSettings.setToggleColor(this, color)
            updateTogglePreview()
        }

        setupSeekBarListener(toggleOpacitySeekBar, toggleOpacityValue) { value ->
            AppearanceSettings.setToggleOpacity(this, value)
            updateTogglePreview()
        }

        setupColorInputListener(toggleActiveColorInput) { color ->
            AppearanceSettings.setToggleActiveColor(this, color)
            updateToggleActivePreview()
        }

        setupSeekBarListener(toggleActiveOpacitySeekBar, toggleActiveOpacityValue) { value ->
            AppearanceSettings.setToggleActiveOpacity(this, value)
            updateToggleActivePreview()
        }

        setupColorInputListener(playerColorInput) { color ->
            AppearanceSettings.setPlayerColor(this, color)
            updatePlayerPreview()
        }

        setupSeekBarListener(playerOpacitySeekBar, playerOpacityValue) { value ->
            AppearanceSettings.setPlayerOpacity(this, value)
            updatePlayerPreview()
        }

        setupColorInputListener(sliderColorInput) { color ->
            AppearanceSettings.setSliderColor(this, color)
            updateSliderPreview()
        }

        setupSeekBarListener(sliderOpacitySeekBar, sliderOpacityValue) { value ->
            AppearanceSettings.setSliderOpacity(this, value)
            updateSliderPreview()
        }

        setupColorInputListener(sliderFillColorInput) { color ->
            AppearanceSettings.setSliderFillColor(this, color)
            updateSliderFillPreview()
        }

        setupSeekBarListener(sliderFillOpacitySeekBar, sliderFillOpacityValue) { value ->
            AppearanceSettings.setSliderFillOpacity(this, value)
            updateSliderFillPreview()
        }

        setupColorInputListener(notificationColorInput) { color ->
            AppearanceSettings.setNotificationColor(this, color)
            updateNotificationPreview()
        }

        setupSeekBarListener(notificationOpacitySeekBar, notificationOpacityValue) { value ->
            AppearanceSettings.setNotificationOpacity(this, value)
            updateNotificationPreview()
        }

        setupColorInputListener(notificationHeaderColorInput) { color ->
            AppearanceSettings.setNotificationHeaderColor(this, color)
            updateNotificationHeaderPreview()
        }

        setupSeekBarListener(notificationHeaderOpacitySeekBar, notificationHeaderOpacityValue) { value ->
            AppearanceSettings.setNotificationHeaderOpacity(this, value)
            updateNotificationHeaderPreview()
        }
        
        setupSeekBarListenerDp(buttonCornerRadiusSeekBar, buttonCornerRadiusValue) { value ->
            AppearanceSettings.setButtonCornerRadius(this, value)
        }
        
        setupSeekBarListenerDp(notificationCornerRadiusSeekBar, notificationCornerRadiusValue) { value ->
            AppearanceSettings.setNotificationCornerRadius(this, value)
        }
        
        setupSeekBarListener(controlCenterBlurSeekBar, controlCenterBlurValue) { value ->
            AppearanceSettings.setControlCenterBlur(this, value)
        }
        
        setupSeekBarListener(notificationCenterBlurSeekBar, notificationCenterBlurValue) { value ->
            AppearanceSettings.setNotificationCenterBlur(this, value)
        }
    }

    private fun setupColorInputListener(editText: EditText, onColorChange: (Int) -> Unit) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s.toString()
                if (text.length == 7 && text.startsWith("#")) {
                    try {
                        val color = AppearanceSettings.hexToColor(text)
                        onColorChange(color)
                    } catch (e: Exception) {}
                }
            }
        })
    }

    private fun setupSeekBarListener(seekBar: SeekBar, valueText: TextView, onValueChange: (Int) -> Unit) {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                valueText.text = "$progress%"
                if (fromUser) {
                    onValueChange(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    private fun setupSeekBarListenerDp(seekBar: SeekBar, valueText: TextView, onValueChange: (Int) -> Unit) {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                valueText.text = "${progress}dp"
                if (fromUser) {
                    onValueChange(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun saveSettings() {
        try {
            AppearanceSettings.setButtonColor(this, AppearanceSettings.hexToColor(buttonColorInput.text.toString()))
        } catch (e: Exception) {}
        AppearanceSettings.setButtonOpacity(this, buttonOpacitySeekBar.progress)
        
        try {
            AppearanceSettings.setButtonActiveColor(this, AppearanceSettings.hexToColor(buttonActiveColorInput.text.toString()))
        } catch (e: Exception) {}
        AppearanceSettings.setButtonActiveOpacity(this, buttonActiveOpacitySeekBar.progress)

        try {
            AppearanceSettings.setToggleColor(this, AppearanceSettings.hexToColor(toggleColorInput.text.toString()))
        } catch (e: Exception) {}
        AppearanceSettings.setToggleOpacity(this, toggleOpacitySeekBar.progress)
        
        try {
            AppearanceSettings.setToggleActiveColor(this, AppearanceSettings.hexToColor(toggleActiveColorInput.text.toString()))
        } catch (e: Exception) {}
        AppearanceSettings.setToggleActiveOpacity(this, toggleActiveOpacitySeekBar.progress)

        try {
            AppearanceSettings.setPlayerColor(this, AppearanceSettings.hexToColor(playerColorInput.text.toString()))
        } catch (e: Exception) {}
        AppearanceSettings.setPlayerOpacity(this, playerOpacitySeekBar.progress)

        try {
            AppearanceSettings.setSliderColor(this, AppearanceSettings.hexToColor(sliderColorInput.text.toString()))
        } catch (e: Exception) {}
        AppearanceSettings.setSliderOpacity(this, sliderOpacitySeekBar.progress)
        
        try {
            AppearanceSettings.setSliderFillColor(this, AppearanceSettings.hexToColor(sliderFillColorInput.text.toString()))
        } catch (e: Exception) {}
        AppearanceSettings.setSliderFillOpacity(this, sliderFillOpacitySeekBar.progress)

        try {
            AppearanceSettings.setNotificationColor(this, AppearanceSettings.hexToColor(notificationColorInput.text.toString()))
        } catch (e: Exception) {}
        AppearanceSettings.setNotificationOpacity(this, notificationOpacitySeekBar.progress)
        
        try {
            AppearanceSettings.setNotificationHeaderColor(this, AppearanceSettings.hexToColor(notificationHeaderColorInput.text.toString()))
        } catch (e: Exception) {}
        AppearanceSettings.setNotificationHeaderOpacity(this, notificationHeaderOpacitySeekBar.progress)
        
        AppearanceSettings.setButtonCornerRadius(this, buttonCornerRadiusSeekBar.progress)
        AppearanceSettings.setNotificationCornerRadius(this, notificationCornerRadiusSeekBar.progress)
        AppearanceSettings.setControlCenterBlur(this, controlCenterBlurSeekBar.progress)
        AppearanceSettings.setNotificationCenterBlur(this, notificationCenterBlurSeekBar.progress)
    }

    private fun updateAllPreviews() {
        updateButtonPreview()
        updateButtonActivePreview()
        updateTogglePreview()
        updateToggleActivePreview()
        updatePlayerPreview()
        updateSliderPreview()
        updateSliderFillPreview()
        updateNotificationPreview()
        updateNotificationHeaderPreview()
    }

    private fun updateButtonPreview() {
        val color = AppearanceSettings.getButtonColorWithOpacity(this, false)
        updateCirclePreview(buttonColorPreview, color)
    }

    private fun updateButtonActivePreview() {
        val color = AppearanceSettings.getButtonColorWithOpacity(this, true)
        updateCirclePreview(buttonActiveColorPreview, color)
    }

    private fun updateTogglePreview() {
        val color = AppearanceSettings.getToggleColorWithOpacity(this, false)
        updateRoundedRectPreview(toggleColorPreview, color, 16f)
    }

    private fun updateToggleActivePreview() {
        val color = AppearanceSettings.getToggleColorWithOpacity(this, true)
        updateRoundedRectPreview(toggleActiveColorPreview, color, 16f)
    }

    private fun updatePlayerPreview() {
        val color = AppearanceSettings.getPlayerColorWithOpacity(this)
        updateRoundedRectPreview(playerColorPreview, color, 24f)
    }

    private fun updateSliderPreview() {
        val color = AppearanceSettings.getSliderColorWithOpacity(this)
        updateRoundedRectPreview(sliderColorPreview, color, 20f)
    }

    private fun updateSliderFillPreview() {
        val color = AppearanceSettings.getSliderFillColorWithOpacity(this)
        updateRoundedRectPreview(sliderFillColorPreview, color, 20f)
    }

    private fun updateCirclePreview(view: View, color: Int) {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.OVAL
        drawable.setColor(color)
        view.background = drawable
    }

    private fun updateRoundedRectPreview(view: View, color: Int, cornerRadius: Float) {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.cornerRadius = cornerRadius * resources.displayMetrics.density
        drawable.setColor(color)
        view.background = drawable
    }

    private fun updateNotificationPreview() {
        val color = AppearanceSettings.getNotificationColorWithOpacity(this)
        updateRoundedRectPreview(notificationColorPreview, color, 20f)
    }

    private fun updateNotificationHeaderPreview() {
        val color = AppearanceSettings.getNotificationHeaderColorWithOpacity(this)
        updateCirclePreview(notificationHeaderColorPreview, color)
    }
}
