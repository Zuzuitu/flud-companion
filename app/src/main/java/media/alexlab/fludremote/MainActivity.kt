package media.alexlab.fludremote

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    companion object {
        private const val RELAY_DEPLOY_URL =
            "https://deploy.workers.cloudflare.com/?url=https://github.com/Zuzuitu/flud-companion/tree/main/selfhost/relay"
    }
    private lateinit var rootScroll: ScrollView
    private lateinit var setupView: TextView
    private lateinit var statusView: TextView
    private lateinit var addressView: TextView
    private lateinit var tokenView: TextView
    private lateinit var fludView: TextView
    private lateinit var overlayView: TextView
    private lateinit var autoStartView: TextView
    private lateinit var remoteAutoStartView: TextView
    private lateinit var cloudView: TextView
    private lateinit var cloudIdentityView: TextView
    private lateinit var lastCommandView: TextView
    private lateinit var qrView: ImageView
    private lateinit var qrCaptionView: TextView
    private var qrMode: String = "remote"
    private lateinit var autoStartButton: Button
    private lateinit var remoteAutoStartButton: Button
    private lateinit var cloudButton: Button
    private var advancedToggleTextTarget: TextView? = null

    private val uiHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshUi()
            uiHandler.postDelayed(this, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        maybeRequestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        uiHandler.removeCallbacks(refreshRunnable)
        refreshUi()
        uiHandler.postDelayed(refreshRunnable, 1500)
    }

    override fun onPause() {
        uiHandler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    private fun tr(key: String): String = AppI18n.t(this, key)

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this)
        rootScroll = scroll
        scroll.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#050A0E"), Color.parseColor("#010305"))
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(34), dp(24), dp(34), dp(34))
        }
        scroll.addView(root)

        val topActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        topActions.addView(headerActionButton("? " + tr("how_to")) { showHowTo() })
        topActions.addView(headerActionButton(AppI18n.current(this).uppercase()) { showLanguagePicker() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply { marginStart = dp(8) })
        topActions.addView(headerActionButton("🍺 " + tr("beer")) { showBeerSupport() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply { marginStart = dp(8) })
        root.addView(topActions, fullWidth().apply { bottomMargin = dp(8) })

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(18))
        }
        brand.addView(ImageView(this).apply {
            setImageResource(R.drawable.companion_mark_v0200)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            background = roundedBackground("#020608", "#12363A", 22)
            contentDescription = "Flud Companion logo"
        }, LinearLayout.LayoutParams(dp(78), dp(78)).apply { marginEnd = dp(16) })

        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brandLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        brandLine.addView(TextView(this).apply {
            text = "Flud"
            textSize = 32f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#F5F8FA"))
        })
        brandLine.addView(TextView(this).apply {
            text = " Companion"
            textSize = 29f
            setTextColor(Color.parseColor("#17B8BB"))
        })
        brandText.addView(brandLine)
        brandText.addView(TextView(this).apply {
            text = "alexlab.media"
            textSize = 13f
            setTextColor(Color.parseColor("#87949E"))
            letterSpacing = 0.16f
        })
        brandText.addView(TextView(this).apply {
            text = "Bridge 0.24.0"
            textSize = 12f
            setTextColor(Color.parseColor("#56636D"))
            setPadding(0, dp(4), 0, 0)
        })
        brandText.addView(TextView(this).apply {
            text = tr("cross_platform")
            textSize = 10f
            setTextColor(Color.parseColor("#6B7882"))
            setPadding(0, dp(5), 0, 0)
        })
        brand.addView(brandText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(brand, fullWidth())

        val modePill = TextView(this).apply {
            text = tr("mode")
            textSize = 12f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#70DAD9"))
            letterSpacing = 0.08f
            setPadding(dp(16), dp(11), dp(16), dp(11))
            background = roundedBackground("#081216", "#1A4145", 99)
        }
        root.addView(modePill, fullWidth().apply {
            bottomMargin = dp(20)
        })

        root.addView(sectionTitle(tr("status")), fullWidth())

        val statusCard = panel()
        setupView = TextView(this).apply {
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#EDF4F6"))
            setLineSpacing(0f, 1.18f)
        }
        statusCard.addView(setupView, fullWidth())

        val quickSetup = button(tr("quick_setup"), true) { showQuickSetup() }
        statusCard.addView(quickSetup)
        root.addView(statusCard, fullWidth().apply { bottomMargin = dp(16) })

        root.addView(sectionTitle(tr("controls")), fullWidth())

        val controls = panel()
        autoStartButton = button(tr("auto_after_reboot")) { toggleAutoStart() }
        remoteAutoStartButton = button(tr("enable_helper")) {
            // Always open the helper settings entry point, even when the service is
            // already enabled. On NVIDIA SHIELD this falls back to Settings home.
            openAccessibilitySettings()
        }
        cloudButton = button(tr("remote_relay")) { toggleCloudRelay() }
        controls.addView(twoColumnGrid(listOf(
            button(tr("start_bridge")) { startBridge() },
            button(tr("stop_bridge")) { stopBridge() },
            autoStartButton,
            remoteAutoStartButton,
            cloudButton,
            button(tr("remote_setup")) { showRelaySetup() },
            button(tr("configure_relay")) { configureRelayUrl() }
        )))
        root.addView(controls, fullWidth().apply { bottomMargin = dp(16) })

        root.addView(sectionTitle(tr("pairing")), fullWidth())

        val pairingCard = panel()
        qrView = ImageView(this).apply {
            setBackgroundColor(Color.WHITE)
            setPadding(dp(9), dp(9), dp(9), dp(9))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Flud Companion pairing QR code"
        }
        pairingCard.addView(qrView, LinearLayout.LayoutParams(dp(286), dp(286)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })

        qrCaptionView = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#8D9AA4"))
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(12), dp(6), dp(8))
        }
        pairingCard.addView(qrCaptionView, fullWidth())

        val qrButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            clipChildren = false
            clipToPadding = false
        }
        qrButtons.addView(actionPill(tr("local_qr")) {
            qrMode = "local"
            refreshUi()
        }, LinearLayout.LayoutParams(0, dp(54), 1f).apply {
            marginEnd = dp(6)
        })
        qrButtons.addView(actionPill(tr("remote_qr")) {
            qrMode = "remote"
            refreshUi()
        }, LinearLayout.LayoutParams(0, dp(54), 1f).apply {
            marginStart = dp(6)
        })
        pairingCard.addView(qrButtons, fullWidth())

        pairingCard.addView(TextView(this).apply {
            text = tr("pairing_private")
            textSize = 11f
            setTextColor(Color.parseColor("#65727C"))
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(10), dp(6), 0)
        }, fullWidth())
        root.addView(pairingCard, fullWidth().apply { bottomMargin = dp(16) })

        root.addView(sectionTitle(tr("advanced")), fullWidth())

        val advancedPanel = panel().apply { visibility = android.view.View.GONE }
        statusView = infoText(advancedPanel)
        addressView = infoText(advancedPanel)
        tokenView = infoText(advancedPanel)
        fludView = infoText(advancedPanel)
        overlayView = infoText(advancedPanel)
        autoStartView = infoText(advancedPanel)
        remoteAutoStartView = infoText(advancedPanel)
        cloudView = infoText(advancedPanel)
        cloudIdentityView = infoText(advancedPanel)
        lastCommandView = infoText(advancedPanel)

        val advancedButtons = listOf(
            button(tr("enable_bg")) { requestOverlayPermission() },
            button(tr("open_flud")) { val result = FludLauncher.openApp(this); toast(result.message) },
            button(tr("copy_lan")) { copyText("Flud Companion LAN token", BridgePreferences.token(this)); toast(tr("copy_lan")) },
            button(tr("copy_remote_url")) {
                val url = cloudMagnetUrlOrNull()
                if (url == null) toast(tr("configure_relay")) else { copyText("Flud Companion remote URL", url); toast(tr("copy_remote_url")) }
            },
            button(tr("copy_remote_token")) { copyText("Flud Companion remote token", BridgePreferences.cloudToken(this)); toast(tr("copy_remote_token")) },
            button(tr("regen_lan")) { BridgePreferences.regenerateToken(this); toast(tr("regen_lan")); refreshUi() },
            button(tr("reset_remote")) { BridgePreferences.resetCloudIdentity(this); toast(tr("reset_remote")); restartBridgeIfRunning(); refreshUi() }
        )
        advancedPanel.addView(twoColumnGrid(advancedButtons))

        val advancedToggle = actionPill(tr("show_advanced")) {
            val opening = advancedPanel.visibility != android.view.View.VISIBLE
            advancedPanel.visibility = if (opening) android.view.View.VISIBLE else android.view.View.GONE
            advancedToggleTextTarget?.text = if (opening) tr("hide_advanced") else tr("show_advanced")
        }
        advancedToggleTextTarget = advancedToggle
        root.addView(advancedToggle, fullWidth().apply {
            topMargin = dp(6)
            bottomMargin = dp(10)
            leftMargin = dp(6)
            rightMargin = dp(6)
        })
        root.addView(advancedPanel, fullWidth().apply { bottomMargin = dp(14) })

        root.addView(TextView(this).apply {
            text = tr("disclaimer")
            textSize = 11f
            setTextColor(Color.parseColor("#59656E"))
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.28f)
            setPadding(dp(14), dp(16), dp(14), dp(12))
        }, fullWidth())

        return scroll
    }

    private fun panel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = roundedBackground("#091116", "#1C3038", 22)
    }

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#59C3C5"))
        letterSpacing = 0.14f
        setPadding(dp(4), dp(2), 0, dp(9))
    }

    private fun infoText(parent: LinearLayout): TextView {
        val view = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#AFBBC3"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground("#050B0F", "#15262D", 14)
        }
        parent.addView(view, fullWidth().apply {
            topMargin = dp(4)
            bottomMargin = dp(4)
        })
        return view
    }

    private fun compactButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#DDE6E9"))
        backgroundTintList = null
        background = roundedBackground("#071117", "#24424A", 20)
        stateListAnimator = null
        setOnFocusChangeListener { view, focused ->
            view.background = roundedBackground(
                if (focused) "#102A30" else "#071117",
                if (focused) "#2FCBCB" else "#24424A",
                20
            )
            view.scaleX = 1f
            view.scaleY = 1f
        }
        setOnClickListener { onClick() }
    }

    private fun actionPill(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#DDE6E9"))
        setPadding(dp(16), 0, dp(16), 0)
        background = roundedBackground("#071117", "#24424A", 28)
        setOnFocusChangeListener { view, focused ->
            view.background = roundedBackground(
                if (focused) "#102A30" else "#071117",
                if (focused) "#2FCBCB" else "#24424A",
                28
            )
            view.scaleX = 1f
            view.scaleY = 1f
        }
        setOnClickListener { onClick() }
    }

    private fun headerActionButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        minHeight = dp(42)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#B8C5CB"))
        backgroundTintList = null
        background = roundedBackground("#050C10", "#18343A", 16)
        stateListAnimator = null
        setPadding(dp(12), 0, dp(12), 0)
        setOnFocusChangeListener { view, focused ->
            view.background = roundedBackground(if (focused) "#0D252A" else "#050C10", if (focused) "#39C8C9" else "#18343A", 16)
        }
        setOnClickListener { onClick() }
    }

    private fun twoColumnGrid(buttons: List<Button>): LinearLayout {
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(2), dp(6), dp(6))
            clipChildren = false
            clipToPadding = false
        }
        buttons.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            pair.forEachIndexed { index, button ->
                row.addView(button, LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                    topMargin = dp(6)
                    if (index == 0) marginEnd = dp(5) else marginStart = dp(5)
                })
            }
            if (pair.size == 1) row.addView(android.view.View(this), LinearLayout.LayoutParams(0, dp(58), 1f).apply { marginStart = dp(5) })
            grid.addView(row, fullWidth())
        }
        return grid
    }

    private fun showLanguagePicker() {
        val codes = AppI18n.supported.toTypedArray()
        val names = codes.map { AppI18n.name(it) }.toTypedArray()
        val current = codes.indexOf(AppI18n.current(this)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(tr("language"))
            .setSingleChoiceItems(names, current) { dialog, which ->
                AppI18n.set(this, codes[which])
                dialog.dismiss()
                setContentView(buildUi())
                refreshUi()
            }
            .setNegativeButton(tr("cancel"), null)
            .show()
    }

    private fun showHowTo() {
        AlertDialog.Builder(this)
            .setTitle(tr("how_title"))
            .setMessage(tr("how_intro") + "\n\n" + tr("how_steps"))
            .setPositiveButton(tr("quick_setup")) { _, _ -> showQuickSetup() }
            .setNegativeButton(tr("close"), null)
            .show()
    }

    private fun showBeerSupport() {
        AlertDialog.Builder(this)
            .setTitle(tr("beer_title"))
            .setMessage(tr("beer_text") + "\n\nhttps://www.paypal.me/AlexandruCiobanu00")
            .setPositiveButton(tr("open_paypal")) { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.me/AlexandruCiobanu00")))
                } catch (_: Exception) {
                    copyText("PayPal.me", "https://www.paypal.me/AlexandruCiobanu00")
                    toast(tr("copy_paypal"))
                }
            }
            .setNeutralButton(tr("copy_paypal")) { _, _ -> copyText("PayPal.me", "https://www.paypal.me/AlexandruCiobanu00") }
            .setNegativeButton(tr("close"), null)
            .show()
    }

    private fun button(label: String, primary: Boolean = false, onClick: (android.view.View) -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 15f
            isAllCaps = false
            minHeight = dp(52)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (primary) Color.parseColor("#001416") else Color.parseColor("#E7EEF1"))
            backgroundTintList = null
            background = roundedBackground(
                if (primary) "#17B8BB" else "#071117",
                if (primary) "#4AD9D8" else "#223A43",
                22
            )
            stateListAnimator = null
            setOnFocusChangeListener { view, focused ->
                view.background = roundedBackground(
                    if (focused) { if (primary) "#34C9C9" else "#102A30" } else { if (primary) "#17B8BB" else "#071117" },
                    if (focused) "#71DFDD" else { if (primary) "#4AD9D8" else "#223A43" },
                    22
                )
                view.scaleX = 1f
                view.scaleY = 1f
            }
            setOnClickListener { onClick(it) }
            layoutParams = fullWidth().apply { topMargin = dp(8) }
        }
    }

    private fun roundedBackground(fill: String, stroke: String? = null, radius: Int = 18): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(fill))
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), Color.parseColor(stroke))
        }
    }

    private fun startBridge() {
        val intent = Intent(this, BridgeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        window.decorView.postDelayed({ refreshUi() }, 400)
    }

    private fun stopBridge() {
        stopService(Intent(this, BridgeService::class.java))
        window.decorView.postDelayed({ refreshUi() }, 250)
    }

    private fun restartBridgeIfRunning() {
        if (!BridgeService.isRunning) return
        stopService(Intent(this, BridgeService::class.java))
        window.decorView.postDelayed({ startBridge() }, 500)
    }

    private fun toggleAutoStart() {
        val enabled = !BridgePreferences.autoStart(this)
        BridgePreferences.setAutoStart(this, enabled)
        toast(if (enabled) tr("auto_reboot_enabled_toast") else tr("auto_reboot_disabled_toast"))
        refreshUi()
    }

    private fun showQuickSetup() {
        val message = tr("quick_choose") + "\n\n" + tr("lan_only_desc") + "\n\n" + tr("lan_remote_desc")
        AlertDialog.Builder(this)
            .setTitle(tr("quick_setup"))
            .setMessage(message)
            .setPositiveButton(tr("lan_remote")) { _, _ -> runQuickSetup(true) }
            .setNeutralButton(tr("lan_only")) { _, _ -> runQuickSetup(false) }
            .setNegativeButton(tr("cancel"), null)
            .show()
    }

    private fun runQuickSetup(wantsRemote: Boolean, skipOverlay: Boolean = false, skipHelper: Boolean = false) {
        val installed = FludLauncher.installedPackage(this)
        if (installed == null) {
            AlertDialog.Builder(this)
                .setTitle(tr("flud_not_detected_title"))
                .setMessage(tr("flud_not_detected_msg"))
                .setPositiveButton(tr("ok"), null)
                .show()
            return
        }

        if (!BridgeService.isRunning) {
            startBridge()
            if (!BridgePreferences.autoStart(this)) BridgePreferences.setAutoStart(this, true)
            uiHandler.postDelayed({ runQuickSetup(wantsRemote, skipOverlay, skipHelper) }, 700)
            return
        }

        if (!BridgePreferences.autoStart(this)) {
            BridgePreferences.setAutoStart(this, true)
        }

        val overlayAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        if (!overlayAllowed && !skipOverlay) {
            AlertDialog.Builder(this)
                .setTitle(tr("bg_permission_title"))
                .setMessage(tr("bg_permission_msg"))
                .setPositiveButton(tr("open_permission")) { _, _ -> requestOverlayPermission() }
                .setNeutralButton(tr("skip")) { _, _ -> runQuickSetup(wantsRemote, true, skipHelper) }
                .setNegativeButton(tr("cancel"), null)
                .show()
            return
        }

        if (!FludAutoStartService.isEnabled(this) && !skipHelper) {
            AlertDialog.Builder(this)
                .setTitle(tr("helper_optional_title"))
                .setMessage(tr("helper_optional_msg"))
                .setPositiveButton(tr("open_settings")) { _, _ -> openAccessibilitySettings() }
                .setNeutralButton(tr("skip")) { _, _ -> runQuickSetup(wantsRemote, skipOverlay, true) }
                .setNegativeButton(tr("cancel"), null)
                .show()
            return
        }

        if (wantsRemote && !validRelayUrl(BridgePreferences.cloudBaseUrl(this))) {
            showRelaySetup { runQuickSetup(true, skipOverlay, skipHelper) }
            return
        }

        if (wantsRemote && !BridgePreferences.cloudEnabled(this)) {
            BridgePreferences.setCloudEnabled(this, true)
            restartBridgeIfRunning()
        }

        qrMode = if (wantsRemote) "remote" else "local"
        refreshUi()

        AlertDialog.Builder(this)
            .setTitle(tr("setup_ready"))
            .setMessage(if (wantsRemote) tr("setup_ready_remote") else tr("setup_ready_lan"))
            .setPositiveButton(tr("show_qr")) { _, _ -> scrollToPairingQr() }
            .setNegativeButton(tr("done"), null)
            .show()
    }

    private fun scrollToPairingQr() {
        rootScroll.post {
            val rect = Rect()
            qrView.getDrawingRect(rect)
            rootScroll.offsetDescendantRectToMyCoords(qrView, rect)
            rootScroll.smoothScrollTo(0, (rect.top - dp(24)).coerceAtLeast(0))
        }
    }

    private fun openAccessibilitySettings() {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)

        // Phones/tablets normally expose the public Accessibility settings action.
        // NVIDIA SHIELD Android TV 11 exposes only a FrameworkPackageStubs handler,
        // so on TV we intentionally skip that broken deep-link.
        if (!isTv) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(flags)
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    toast(tr("access_opened"))
                    return
                }
            } catch (_: Exception) {
            }
        }

        // Restore the simple Settings-home fallback that proved reliable on SHIELD
        // during the earlier v0.13 tests. The user then navigates to Accessibility.
        try {
            val intent = Intent(Settings.ACTION_SETTINGS).addFlags(flags)
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                toast(tr("access_manual"))
                return
            }
        } catch (_: Exception) {
        }

        // Last fallback: launch the Settings package itself.
        val settingsPackages = linkedSetOf("com.android.tv.settings", "com.android.settings")
        for (settingsPackage in settingsPackages) {
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(settingsPackage) ?: continue
                startActivity(launchIntent.addFlags(flags))
                toast(tr("access_manual"))
                return
            } catch (_: Exception) {
            }
        }

        toast(tr("settings_unavailable"))
    }

    private fun toggleCloudRelay() {
        val enabled = !BridgePreferences.cloudEnabled(this)
        if (enabled && !validRelayUrl(BridgePreferences.cloudBaseUrl(this))) {
            toast(tr("relay_config_first"))
            configureRelayUrl()
            return
        }
        BridgePreferences.setCloudEnabled(this, enabled)
        toast(if (enabled) tr("relay_enabled") else tr("relay_disabled"))
        restartBridgeIfRunning()
        refreshUi()
    }

    private fun showRelaySetup(onConfigured: (() -> Unit)? = null) {
        AlertDialog.Builder(this)
            .setTitle(tr("remote_setup"))
            .setMessage(tr("relay_setup_msg"))
            .setPositiveButton(tr("enter_relay")) { _, _ -> configureRelayUrl(onConfigured) }
            .setNeutralButton(tr("show_deploy_qr")) { _, _ -> showRelayDeployQr() }
            .setNegativeButton(tr("cancel"), null)
            .show()
    }

    private fun showRelayDeployQr() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(12), dp(22), dp(6))
        }

        val image = ImageView(this).apply {
            setBackgroundColor(Color.WHITE)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Deploy Remote relay QR code"
            setImageBitmap(makeQrBitmap(RELAY_DEPLOY_URL, dp(300)))
        }
        container.addView(image, LinearLayout.LayoutParams(dp(320), dp(320)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })

        container.addView(TextView(this).apply {
            text = tr("deploy_desc")
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(8))
        }, fullWidth())

        AlertDialog.Builder(this)
            .setTitle(tr("deploy_relay"))
            .setView(container)
            .setPositiveButton(tr("copy_deploy")) { _, _ ->
                copyText("Flud Companion relay deploy URL", RELAY_DEPLOY_URL)
                toast(tr("deploy_copied"))
            }
            .setNegativeButton(tr("close"), null)
            .show()
    }

    private fun configureRelayUrl(onSaved: (() -> Unit)? = null) {
        val input = EditText(this).apply {
            hint = "https://your-relay.workers.dev"
            setText(BridgePreferences.cloudBaseUrl(this@MainActivity))
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSelection(text.length)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(tr("relay_url_title"))
            .setMessage(tr("relay_url_msg"))
            .setView(input)
            .setPositiveButton(tr("save"), null)
            .setNegativeButton(tr("cancel"), null)
            .setNeutralButton(tr("clear")) { _, _ ->
                BridgePreferences.setCloudBaseUrl(this, "")
                BridgePreferences.setCloudEnabled(this, false)
                restartBridgeIfRunning()
                refreshUi()
                toast(tr("relay_cleared"))
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().trim().trimEnd('/')
                if (!validRelayUrl(value)) {
                    toast(tr("relay_invalid"))
                    return@setOnClickListener
                }
                BridgePreferences.setCloudBaseUrl(this, value)
                BridgePreferences.setCloudEnabled(this, true)
                dialog.dismiss()
                restartBridgeIfRunning()
                refreshUi()
                toast(tr("relay_saved"))
                onSaved?.invoke()
            }
        }
        dialog.show()
    }

    private fun validRelayUrl(value: String): Boolean {
        if (!value.startsWith("https://", ignoreCase = true)) return false
        val parsed = try { Uri.parse(value) } catch (_: Exception) { null }
        return parsed?.host?.isNotBlank() == true
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            toast(tr("overlay_not_required"))
            return
        }
        if (Settings.canDrawOverlays(this)) {
            toast(tr("overlay_already"))
            return
        }

        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    private fun refreshUi() {
        val ip = NetworkUtils.localIpv4() ?: "<Android-device-IP>"
        val token = BridgePreferences.token(this)
        val installed = FludLauncher.installedPackage(this)
        val overlayAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        val autoStart = BridgePreferences.autoStart(this)
        val remoteAutoStartReady = FludAutoStartService.isEnabled(this)
        val cloudEnabled = BridgePreferences.cloudEnabled(this)
        val cloudBase = BridgePreferences.cloudBaseUrl(this)
        val cloud = CloudRelayClient.snapshot()
        val last = BridgePreferences.lastCommand(this)
        val lanReady = BridgeService.isRunning && installed != null
        val remoteConfigured = validRelayUrl(cloudBase)
        val remoteReady = cloudEnabled && remoteConfigured && cloud.state.name == "CONNECTED"

        setupView.text = buildString {
            append("LAN: ")
            append(if (lanReady) tr("ready") else tr("needs_setup"))
            append("  •  ${tr("start_after_reboot")}: ")
            append(if (autoStart) "${tr("on")} ✓" else tr("off"))
            append("\n${tr("auto_helper")}: ")
            append(if (remoteAutoStartReady) tr("ready") else tr("optional_off"))
            append("\n${tr("remote")}: ")
            append(when {
                remoteReady -> tr("ready")
                remoteConfigured && cloudEnabled -> tr("connecting")
                remoteConfigured -> tr("configured_disabled")
                else -> tr("not_configured_optional")
            })
        }

        statusView.text = "${tr("bridge")}: ${if (BridgeService.isRunning) tr("running") else tr("stopped")}"
        addressView.text = "LAN API: http://$ip:${BridgeService.PORT}\n${tr("local_web_ui")}: http://$ip:${BridgeService.PORT}/app"
        tokenView.text = "LAN API token: ${maskSecret(token)} — ${tr("lan_token_hint")}"
        fludView.text = "Flud: ${when (installed) {
            FludLauncher.FREE_PACKAGE -> tr("detected_free")
            FludLauncher.PAID_PACKAGE -> tr("detected_plus")
            else -> tr("not_detected")
        }}"
        overlayView.text = "${tr("background_launch")}: ${if (overlayAllowed) tr("permission_enabled") else tr("permission_not_enabled")}"
        autoStartView.text = "${tr("auto_after_reboot")}: ${if (autoStart) tr("on") else tr("off")}"
        autoStartButton.text = if (autoStart) tr("disable_auto_start") else tr("enable_auto_start")
        remoteAutoStartView.text = if (remoteAutoStartReady) {
            "${tr("auto_helper")}: ${tr("ready")} — ${FludAutoStartService.status()}"
        } else {
            tr("helper_off_desc")
        }
        remoteAutoStartButton.text = if (remoteAutoStartReady) tr("helper_settings_ready") else tr("enable_helper")
        cloudButton.text = if (cloudEnabled) tr("disable_remote_relay") else tr("enable_remote_relay")
        cloudView.text = "${tr("remote_relay")}: ${if (!cloudEnabled) tr("off") else cloud.state.name} — ${cloud.detail}"

        val remoteUrl = cloudMagnetUrlOrNull()
        cloudIdentityView.text = buildString {
            append("${tr("relay")}: ")
            append(if (cloudBase.isBlank()) tr("not_configured") else cloudBase)
            append("\n${tr("remote_device")}: ")
            append(BridgePreferences.cloudDeviceId(this@MainActivity))
            append("\n${tr("remote_token")}: ")
            append(maskSecret(BridgePreferences.cloudToken(this@MainActivity)))
            append("\n${tr("remote_post")}: ")
            append(remoteUrl ?: tr("remote_post_unavailable"))
        }

        lastCommandView.text = if (last == null) {
            tr("last_none")
        } else {
            val whenText = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(last.atMillis))
            "${tr("last_command")}: ${if (last.success) "OK" else tr("failed")} — ${last.message} — $whenText"
        }

        updatePairingQr(ip, token, cloudBase)
    }

    private fun updatePairingQr(ip: String, lanToken: String, cloudBase: String) {
        val localUrl = "http://$ip:${BridgeService.PORT}/app#v=1&token=${Uri.encode(lanToken)}"
        val remoteReady = validRelayUrl(cloudBase)
        val remoteUrl = if (remoteReady) {
            val device = BridgePreferences.cloudDeviceId(this)
            val remoteToken = BridgePreferences.cloudToken(this)
            "$cloudBase/app#v=1&device=${Uri.encode(device)}&token=${Uri.encode(remoteToken)}"
        } else null

        if (qrMode == "remote" && remoteUrl == null) qrMode = "local"
        val value = if (qrMode == "remote") remoteUrl!! else localUrl
        qrView.setImageBitmap(makeQrBitmap(value, dp(300)))
        qrCaptionView.text = if (qrMode == "remote") tr("remote_qr_caption") else tr("local_qr_caption")
    }

    private fun cloudMagnetUrlOrNull(): String? {
        val base = BridgePreferences.cloudBaseUrl(this)
        if (!validRelayUrl(base)) return null
        return "$base/api/v1/device/${BridgePreferences.cloudDeviceId(this)}/magnet"
    }

    private fun maskSecret(value: String): String {
        if (value.length <= 10) return "••••••••"
        return value.take(4) + "••••••••" + value.takeLast(4)
    }

    private fun copyText(label: String, value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    }

    private fun makeQrBitmap(text: String, size: Int): Bitmap? {
        return try {
            val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                val offset = y * size
                for (x in 0 until size) {
                    pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        } catch (_: Exception) {
            null
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
    }

    private fun fullWidth(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
