package media.alexlab.fludremote

import android.content.Context
import java.util.Locale

object AppI18n {
    private const val PREFS = "flud_companion_i18n"
    private const val KEY_LANG = "lang"
    val supported = listOf("en", "ro", "fr", "de")

    fun current(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANG, null)
        if (saved in supported) return saved!!
        val code = Locale.getDefault().language.lowercase(Locale.US)
        return if (code in supported) code else "en"
    }

    fun set(context: Context, code: String) {
        if (code in supported) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANG, code).apply()
    }

    fun name(code: String): String = when (code) {
        "ro" -> "Română"
        "fr" -> "Français"
        "de" -> "Deutsch"
        else -> "English"
    }

    fun t(context: Context, key: String): String = translations[key]?.get(current(context))
        ?: translations[key]?.get("en") ?: key

    private fun m(en: String, ro: String, fr: String, de: String) = mapOf("en" to en, "ro" to ro, "fr" to fr, "de" to de)

    private val translations = mapOf(
        "mode" to m("ANDROID BRIDGE  •  LAN + REMOTE", "ANDROID BRIDGE  •  LAN + REMOTE", "ANDROID BRIDGE  •  LAN + REMOTE", "ANDROID BRIDGE  •  LAN + REMOTE"),
        "cross_platform" to m("Cross-platform Web Companion · iPhone · Android · Tablet · Desktop", "Companion Web cross-platform · iPhone · Android · tabletă · desktop", "Companion Web multiplateforme · iPhone · Android · tablette · ordinateur", "Plattformübergreifender Web Companion · iPhone · Android · Tablet · Desktop"),
        "status" to m("STATUS", "STARE", "ÉTAT", "STATUS"),
        "controls" to m("CONTROLS", "COMENZI", "COMMANDES", "STEUERUNG"),
        "pairing" to m("PAIRING", "ASOCIERE", "ASSOCIATION", "KOPPLUNG"),
        "advanced" to m("ADVANCED", "AVANSAT", "AVANCÉ", "ERWEITERT"),
        "quick_setup" to m("Quick setup", "Configurare rapidă", "Configuration rapide", "Schnelleinrichtung"),
        "start_bridge" to m("Start Bridge", "Pornește Bridge", "Démarrer Bridge", "Bridge starten"),
        "stop_bridge" to m("Stop Bridge", "Oprește Bridge", "Arrêter Bridge", "Bridge stoppen"),
        "auto_after_reboot" to m("Auto-start after reboot", "Pornire automată după restart", "Démarrage auto après redémarrage", "Autostart nach Neustart"),
        "enable_helper" to m("Auto-start helper settings", "Setări helper auto-start", "Réglages assistant auto-start", "Auto-start-Helfer Einstellungen"),
        "helper_enabled" to m("Auto-start helper enabled ✓", "Helper auto-start activ ✓", "Assistant auto-start activé ✓", "Auto-start-Helfer aktiv ✓"),
        "helper_settings_ready" to m("Auto-start helper settings ✓", "Setări helper auto-start ✓", "Réglages assistant auto-start ✓", "Auto-start-Helfer Einstellungen ✓"),
        "remote_relay" to m("Remote relay", "Relay Remote", "Relay Remote", "Remote-Relay"),
        "remote_setup" to m("Remote relay setup", "Configurare relay Remote", "Configuration du relay Remote", "Remote-Relay einrichten"),
        "configure_relay" to m("Configure relay URL", "Configurează URL relay", "Configurer l’URL du relay", "Relay-URL konfigurieren"),
        "local_qr" to m("Local QR", "QR Local", "QR Local", "Local QR"),
        "remote_qr" to m("Remote QR", "QR Remote", "QR Remote", "Remote QR"),
        "pairing_private" to m("Pairing secrets stay in the URL fragment (#…) and are never sent as part of the relay request. Keep the QR private.", "Secretele de asociere rămân în fragmentul URL (#…) și nu sunt trimise ca parte a cererii către relay. Păstrează codul QR privat.", "Les secrets d’association restent dans le fragment URL (#…) et ne sont jamais envoyés au relay. Gardez le QR privé.", "Kopplungsdaten bleiben im URL-Fragment (#…) und werden nicht an den Relay gesendet. Halte den QR-Code privat."),
        "enable_bg" to m("Enable background launch", "Permite lansarea în fundal", "Autoriser le lancement en arrière-plan", "Hintergrundstart erlauben"),
        "open_flud" to m("Open Flud", "Deschide Flud", "Ouvrir Flud", "Flud öffnen"),
        "access_diag" to m("Accessibility diagnostics", "Diagnostic Accesibilitate", "Diagnostic d’accessibilité", "Bedienungshilfe-Diagnose"),
        "copy_lan" to m("Copy LAN API token", "Copiază tokenul API LAN", "Copier le jeton API LAN", "LAN-API-Token kopieren"),
        "copy_remote_url" to m("Copy remote command URL", "Copiază URL-ul comenzii Remote", "Copier l’URL de commande Remote", "Remote-Befehls-URL kopieren"),
        "copy_remote_token" to m("Copy remote token", "Copiază tokenul Remote", "Copier le token Remote", "Remote-Token kopieren"),
        "regen_lan" to m("Regenerate LAN API token", "Regenerează tokenul API LAN", "Régénérer le jeton API LAN", "LAN-API-Token neu erzeugen"),
        "reset_remote" to m("Reset remote identity", "Resetează identitatea Remote", "Réinitialiser l’identité Remote", "Remote-Identität zurücksetzen"),
        "show_advanced" to m("Show advanced details", "Arată detalii avansate", "Afficher les détails avancés", "Erweiterte Details anzeigen"),
        "hide_advanced" to m("Hide advanced details", "Ascunde detaliile avansate", "Masquer les détails avancés", "Erweiterte Details ausblenden"),
        "how_to" to m("How-to", "Ghid", "Guide", "Anleitung"),
        "language" to m("Language", "Limbă", "Langue", "Sprache"),
        "beer" to m("Offer me a beer", "Oferă-mi o bere", "Offrez-moi une bière", "Spendier mir ein Bier"),
        "beer_title" to m("Offer me a beer 🍺", "Oferă-mi o bere 🍺", "Offrez-moi une bière 🍺", "Spendier mir ein Bier 🍺"),
        "beer_text" to m("If you appreciate Flud Companion and the time spent building it, you can leave any small token of appreciation. Thank you.", "Dacă apreciezi Flud Companion și timpul investit în dezvoltarea lui, poți lăsa un mic semn de recunoștință, indiferent de sumă. Mulțumesc!", "Si vous appréciez Flud Companion et le temps consacré à son développement, vous pouvez laisser un petit geste de soutien, quel qu’en soit le montant. Merci !", "Wenn dir Flud Companion und die investierte Entwicklungszeit gefallen, kannst du mit einem noch so kleinen Beitrag deine Wertschätzung zeigen. Danke!"),
        "open_paypal" to m("Open PayPal", "Deschide PayPal", "Ouvrir PayPal", "PayPal öffnen"),
        "copy_paypal" to m("Copy PayPal link", "Copiază link-ul PayPal", "Copier le lien PayPal", "PayPal-Link kopieren"),
        "how_title" to m("Flud Companion — beginner setup", "Flud Companion — configurare pentru începători", "Flud Companion — configuration débutant", "Flud Companion — Einrichtung für Einsteiger"),
        "how_intro" to m("Follow the steps in order. LAN needs no cloud account. Remote uses a relay inside your own Cloudflare account.", "Urmează pașii în ordine. LAN nu are nevoie de cont cloud. Remote folosește un relay în propriul tău cont Cloudflare.", "Suivez les étapes dans l’ordre. Le LAN ne nécessite aucun compte cloud. Remote utilise un relay dans votre propre compte Cloudflare.", "Folge den Schritten der Reihe nach. LAN braucht kein Cloud-Konto. Remote verwendet einen Relay in deinem eigenen Cloudflare-Konto."),
        "how_steps" to m(
            "1. Install Flud or Flud+ and Flud Companion Bridge on the Android device.\n\n2. Open Quick setup. Choose LAN only, or LAN + Remote.\n\n3. If you want Auto-start download, enable Flud Companion Auto-start in Android Accessibility. If the helper button cannot open Accessibility on your TV, open Android Settings manually and go to Device Preferences → Accessibility → Flud Companion Auto-start.\n\n4. LAN: scan Local QR with the phone while both devices are on the same network. You are done for local use.\n\n5. Remote: create/sign in to a free Cloudflare account. Open Remote relay setup → Show Deploy QR. Let Cloudflare create the Worker and R2 mailbox.\n\n6. Open the new relay page and copy its HTTPS URL (usually *.workers.dev).\n\n7. Back in Bridge: Configure relay URL, paste it and enable Remote relay.\n\n8. Scan Remote QR with the phone. The Remote PWA saves Device ID + token automatically.\n\n9. Flud Companion Web works cross-platform on iPhone, Android, tablet and desktop. On supported browsers use Add to Home Screen / Install app for an app-like launch. Chrome is the primary beta browser; Safari should also be smoke-tested.",
            "1. Instalează Flud sau Flud+ și Flud Companion Bridge pe dispozitivul Android.\n\n2. Deschide Configurare rapidă. Alege doar LAN sau LAN + Remote.\n\n3. Dacă vrei Pornire automată descărcare, activează Flud Companion Auto-start în Accesibilitate Android. Dacă butonul helperului nu poate deschide Accesibilitate pe televizorul tău, deschide manual Setările Android și intră la Preferințe dispozitiv → Accesibilitate → Flud Companion Auto-start.\n\n4. LAN: scanează QR Local cu telefonul cât timp ambele dispozitive sunt în aceeași rețea. Pentru utilizare locală ai terminat.\n\n5. Remote: creează/intră într-un cont Cloudflare gratuit. Deschide Configurare relay Remote → Arată QR de deploy. Lasă Cloudflare să creeze Worker-ul și mailbox-ul R2.\n\n6. Deschide noua pagină relay și copiază URL-ul HTTPS (de obicei *.workers.dev).\n\n7. Înapoi în Bridge: Configurează URL relay, lipește-l și activează Relay Remote.\n\n8. Scanează QR Remote cu telefonul. PWA Remote salvează automat Device ID + token.\n\n9. Flud Companion Web este cross-platform pe iPhone, Android, tabletă și desktop. În browserele compatibile folosește Adaugă pe ecranul principal / Instalează aplicația. Chrome este browserul principal de test beta; Safari trebuie verificat și el de bază.",
            "1. Installez Flud ou Flud+ et Flud Companion Bridge sur l’appareil Android.\n\n2. Ouvrez Configuration rapide. Choisissez LAN uniquement ou LAN + Remote.\n\n3. Pour le démarrage automatique, activez Flud Companion Auto-start dans Accessibilité Android. Si le bouton de l’assistant ne peut pas ouvrir Accessibilité sur votre téléviseur, ouvrez manuellement les Réglages Android puis Préférences de l’appareil → Accessibilité → Flud Companion Auto-start.\n\n4. LAN : scannez le QR Local avec le téléphone lorsque les deux appareils sont sur le même réseau local. Pour l’usage local, c’est terminé.\n\n5. Remote : créez/connectez-vous à un compte Cloudflare gratuit. Ouvrez Configuration du relay Remote → Afficher le QR de déploiement. Autorisez Cloudflare à créer le Worker et la boîte R2.\n\n6. Ouvrez la nouvelle page du relay et copiez son URL HTTPS (généralement *.workers.dev).\n\n7. Dans Bridge : configurez l’URL du relay, collez-la et activez le relay Remote.\n\n8. Scannez le QR Remote avec le téléphone. La PWA Remote enregistre automatiquement le Device ID et le token.\n\n9. Sur le téléphone, utilisez l’option Ajouter à l’écran d’accueil / Installer l’application du navigateur lorsqu’elle est disponible. Chrome est le navigateur principal des tests bêta ; Safari doit aussi être vérifié rapidement.",
            "1. Installiere Flud oder Flud+ und Flud Companion Bridge auf dem Android-Gerät.\n\n2. Öffne Schnelleinrichtung. Wähle nur LAN oder LAN + Remote.\n\n3. Für automatischen Download aktiviere Flud Companion Auto-start in den Android-Bedienungshilfen. Falls die Helfer-Schaltfläche die Bedienungshilfen auf deinem TV nicht öffnen kann, öffne die Android-Einstellungen manuell und gehe zu Geräteeinstellungen → Bedienungshilfen → Flud Companion Auto-start.\n\n4. LAN: Scanne Local QR mit dem Telefon, während beide Geräte im selben Netzwerk sind. Für lokale Nutzung bist du fertig.\n\n5. Remote: Erstelle/öffne ein kostenloses Cloudflare-Konto. Öffne Remote-Relay einrichten → Deploy-QR anzeigen. Cloudflare erstellt den Worker und die R2-Mailbox.\n\n6. Öffne die neue Relay-Seite und kopiere die HTTPS-URL (meist *.workers.dev).\n\n7. Zurück in Bridge: Relay-URL konfigurieren, einfügen und Remote-Relay aktivieren.\n\n8. Scanne Remote QR mit dem Telefon. Die Remote-PWA speichert Device ID + Token automatisch.\n\n9. Verwende auf dem Telefon – sofern verfügbar – die Browser-Option Zum Home-Bildschirm / App installieren. Chrome ist der primäre Beta-Testbrowser; Safari sollte zusätzlich kurz geprüft werden."
        ),
        "quick_choose" to m("Choose how you want to use the Bridge.", "Alege cum vrei să folosești Bridge-ul.", "Choisissez comment utiliser le Bridge.", "Wähle, wie du den Bridge verwenden möchtest."),
        "lan_only_desc" to m("LAN only: phone and Android device are on the same local network. No cloud account or relay is required.", "Doar LAN: telefonul și dispozitivul Android sunt în aceeași rețea locală. Nu este necesar niciun cont cloud sau relay.", "LAN uniquement : le téléphone et l’appareil Android sont sur le même réseau local. Aucun compte cloud ni relay requis.", "Nur LAN: Telefon und Android-Gerät befinden sich im selben lokalen Netzwerk. Kein Cloud-Konto oder Relay erforderlich."),
        "lan_remote_desc" to m("LAN + Remote: keeps LAN control and also lets you send magnets while away from home through your own self-hosted relay.", "LAN + Remote: păstrează controlul LAN și permite trimiterea magneturilor din afara casei prin propriul relay self-hosted.", "LAN + Remote : conserve le contrôle LAN et permet d’envoyer des magnets à distance via votre propre relay auto-hébergé.", "LAN + Remote: behält die LAN-Steuerung und ermöglicht Magnet-Links von unterwegs über deinen eigenen Relay."),
        "lan_remote" to m("LAN + Remote", "LAN + Remote", "LAN + Remote", "LAN + Remote"),
        "lan_only" to m("LAN only", "Doar LAN", "LAN uniquement", "Nur LAN"),
        "cancel" to m("Cancel", "Anulează", "Annuler", "Abbrechen"),
        "done" to m("Done", "Gata", "Terminé", "Fertig"),
        "ok" to m("OK", "OK", "OK", "OK"),
        "close" to m("Close", "Închide", "Fermer", "Schließen"),
        "copy" to m("Copy", "Copiază", "Copier", "Kopieren"),
        "save" to m("Save", "Salvează", "Enregistrer", "Speichern"),
        "clear" to m("Clear", "Șterge", "Effacer", "Löschen"),
        "skip" to m("Skip", "Sari", "Ignorer", "Überspringen"),
        "open_settings" to m("Open Settings", "Deschide Setări", "Ouvrir les réglages", "Einstellungen öffnen"),
        "show_qr" to m("Show QR", "Arată QR", "Afficher le QR", "QR anzeigen"),
        "enter_relay" to m("Enter relay URL", "Introdu URL relay", "Saisir l’URL du relay", "Relay-URL eingeben"),
        "show_deploy_qr" to m("Show Deploy QR", "Arată QR Deploy", "Afficher le QR Deploy", "Deploy-QR anzeigen"),
        "deploy_relay" to m("Deploy your Remote relay", "Instalează relay-ul Remote", "Déployer votre relay Remote", "Remote-Relay bereitstellen"),
        "copy_deploy" to m("Copy deploy link", "Copiază link-ul de deploy", "Copier le lien de déploiement", "Deploy-Link kopieren"),
        "relay_url_title" to m("Self-hosted relay URL", "URL relay self-hosted", "URL du relay auto-hébergé", "Selbst gehostete Relay-URL"),
        "relay_url_msg" to m("Enter your own HTTPS relay URL. A custom domain is optional; a workers.dev URL works.", "Introdu propriul URL HTTPS al relay-ului. Domeniul personal este opțional; un URL workers.dev funcționează.", "Saisissez votre propre URL HTTPS de relay. Un domaine personnalisé est facultatif ; une URL workers.dev fonctionne.", "Gib deine eigene HTTPS-Relay-URL ein. Eine eigene Domain ist optional; eine workers.dev-URL funktioniert."),
        "relay_setup_msg" to m("Remote uses a relay owned by you. No shared Flud Companion cloud account is required.\n\nNew user: scan the Deploy QR with your phone, deploy the relay to your own Cloudflare account, then copy the HTTPS workers.dev URL shown by the relay landing page.\n\nAlready deployed: choose Enter relay URL.", "Remote folosește un relay care îți aparține. Nu este necesar niciun cont cloud comun Flud Companion.\n\nUtilizator nou: scanează QR Deploy cu telefonul, instalează relay-ul în propriul cont Cloudflare, apoi copiază URL-ul HTTPS workers.dev afișat de pagina relay.\n\nDeja instalat: alege Introdu URL relay.", "Remote utilise un relay qui vous appartient. Aucun compte cloud Flud Companion partagé n’est requis.\n\nNouvel utilisateur : scannez le QR Deploy avec le téléphone, déployez le relay dans votre propre compte Cloudflare, puis copiez l’URL HTTPS workers.dev affichée.\n\nDéjà déployé : choisissez Saisir l’URL du relay.", "Remote verwendet einen Relay in deinem Besitz. Ein gemeinsames Flud-Companion-Cloud-Konto ist nicht nötig.\n\nNeuer Nutzer: Scanne den Deploy-QR-Code, stelle den Relay in deinem eigenen Cloudflare-Konto bereit und kopiere anschließend die angezeigte HTTPS-workers.dev-URL.\n\nBereits bereitgestellt: Wähle Relay-URL eingeben."),
        "auto_reboot_enabled_toast" to m("Bridge will auto-start after reboot", "Bridge-ul va porni automat după restart", "Le Bridge démarrera automatiquement après le redémarrage", "Bridge startet nach einem Neustart automatisch"),
        "auto_reboot_disabled_toast" to m("Auto-start disabled", "Pornirea automată a fost dezactivată", "Démarrage automatique désactivé", "Autostart deaktiviert"),
        "flud_not_detected_title" to m("Flud not detected", "Flud nu a fost detectat", "Flud non détecté", "Flud nicht erkannt"),
        "flud_not_detected_msg" to m("Install Flud or Flud+ on this Android device first, then open Quick setup again.", "Instalează mai întâi Flud sau Flud+ pe acest dispozitiv Android, apoi deschide din nou Configurare rapidă.", "Installez d’abord Flud ou Flud+ sur cet appareil Android, puis relancez Configuration rapide.", "Installiere zuerst Flud oder Flud+ auf diesem Android-Gerät und öffne dann erneut die Schnelleinrichtung."),
        "bg_permission_title" to m("Background launch permission", "Permisiune pentru lansarea în fundal", "Autorisation de lancement en arrière-plan", "Berechtigung für Hintergrundstart"),
        "bg_permission_msg" to m("Recommended for reliable remote use: allow Flud Companion to launch Flud while the Bridge is in the background. After granting it, open Quick setup again. You can also skip this step.", "Recomandat pentru Remote fiabil: permite Flud Companion să deschidă Flud când Bridge-ul rulează în fundal. După acordarea permisiunii, deschide din nou Configurare rapidă. Poți și să sari peste acest pas.", "Recommandé pour un usage Remote fiable : autorisez Flud Companion à lancer Flud lorsque le Bridge est en arrière-plan. Après l’autorisation, relancez Configuration rapide. Vous pouvez aussi ignorer cette étape.", "Für zuverlässige Remote-Nutzung empfohlen: Erlaube Flud Companion, Flud zu starten, während der Bridge im Hintergrund läuft. Öffne danach die Schnelleinrichtung erneut. Du kannst diesen Schritt auch überspringen."),
        "open_permission" to m("Open permission", "Deschide permisiunea", "Ouvrir l’autorisation", "Berechtigung öffnen"),
        "helper_optional_title" to m("Optional auto-start helper", "Helper auto-start opțional", "Assistant auto-start facultatif", "Optionaler Auto-start-Helfer"),
        "helper_optional_msg" to m("Enable the Accessibility helper only if you want Auto-start download. Some Android TV firmwares do not expose a direct Accessibility deep-link; in that case Settings home opens and the helper must be enabled manually. You can skip this step and still send magnets normally.", "Activează helperul de Accesibilitate doar dacă vrei Pornire automată descărcare. Unele firmware-uri Android TV nu oferă un link direct către Accesibilitate; în acest caz se deschid Setările și helperul trebuie activat manual. Poți să sari peste acest pas și să trimiți magneturi normal.", "Activez l’assistant d’accessibilité uniquement si vous souhaitez le démarrage automatique du téléchargement. Certains firmwares Android TV n’exposent pas de lien direct vers Accessibilité ; dans ce cas, les Réglages s’ouvrent et l’assistant doit être activé manuellement. Vous pouvez ignorer cette étape et envoyer les magnets normalement.", "Aktiviere den Bedienungshilfe-Helfer nur, wenn du Downloads automatisch starten möchtest. Manche Android-TV-Firmwares bieten keinen direkten Link zu den Bedienungshilfen; dann öffnen sich die Einstellungen und der Helfer muss manuell aktiviert werden. Du kannst diesen Schritt überspringen und Magnet-Links trotzdem normal senden."),
        "setup_ready" to m("Setup ready", "Configurare gata", "Configuration prête", "Einrichtung fertig"),
        "setup_ready_remote" to m("LAN is ready and Remote is configured. Scan the Remote QR with your phone to pair the Remote PWA. You can switch to Local QR at any time for direct LAN control.", "LAN este gata, iar Remote este configurat. Scanează QR Remote cu telefonul pentru a asocia PWA Remote. Poți trece oricând la QR Local pentru control direct prin LAN.", "Le LAN est prêt et Remote est configuré. Scannez le QR Remote avec votre téléphone pour associer la PWA Remote. Vous pouvez passer au QR Local à tout moment pour un contrôle LAN direct.", "LAN ist bereit und Remote ist eingerichtet. Scanne den Remote-QR-Code mit dem Telefon, um die Remote-PWA zu koppeln. Für direkte LAN-Steuerung kannst du jederzeit zu Local QR wechseln."),
        "setup_ready_lan" to m("LAN is ready. Scan the Local QR with your phone while both devices are on the same local network.", "LAN este gata. Scanează QR Local cu telefonul cât timp ambele dispozitive sunt în aceeași rețea locală.", "Le LAN est prêt. Scannez le QR Local avec votre téléphone lorsque les deux appareils sont sur le même réseau local.", "LAN ist bereit. Scanne den Local-QR-Code mit dem Telefon, während sich beide Geräte im selben lokalen Netzwerk befinden."),
        "access_opened" to m("Accessibility opened. Select ‘Flud Companion Auto-start’.", "Accesibilitate a fost deschisă. Selectează „Flud Companion Auto-start”.", "Accessibilité ouverte. Sélectionnez « Flud Companion Auto-start ».", "Bedienungshilfen geöffnet. Wähle „Flud Companion Auto-start“."),
        "access_manual" to m("Opening Android Settings. Go to Device Preferences → Accessibility → Flud Companion Auto-start.", "Se deschid Setările Android. Intră la Preferințe dispozitiv → Accesibilitate → Flud Companion Auto-start.", "Ouverture des Réglages Android. Allez dans Préférences de l’appareil → Accessibilité → Flud Companion Auto-start.", "Android-Einstellungen werden geöffnet. Gehe zu Geräteeinstellungen → Bedienungshilfen → Flud Companion Auto-start."),
        "settings_unavailable" to m("Android Settings could not be opened automatically. Open Settings manually, then Device Preferences → Accessibility → Flud Companion Auto-start.", "Setările Android nu au putut fi deschise automat. Deschide-le manual, apoi Preferințe dispozitiv → Accesibilitate → Flud Companion Auto-start.", "Les Réglages Android n’ont pas pu être ouverts automatiquement. Ouvrez-les manuellement, puis Préférences de l’appareil → Accessibilité → Flud Companion Auto-start.", "Die Android-Einstellungen konnten nicht automatisch geöffnet werden. Öffne sie manuell und gehe zu Geräteeinstellungen → Bedienungshilfen → Flud Companion Auto-start."),
        "diag_title" to m("Accessibility diagnostics", "Diagnostic Accesibilitate", "Diagnostic d’accessibilité", "Bedienungshilfe-Diagnose"),
        "diag_copied" to m("Diagnostics copied", "Diagnosticul a fost copiat", "Diagnostic copié", "Diagnose kopiert"),
        "relay_config_first" to m("Configure your relay URL first", "Configurează mai întâi URL-ul relay-ului", "Configurez d’abord l’URL du relay", "Konfiguriere zuerst die Relay-URL"),
        "relay_enabled" to m("Remote relay enabled", "Relay Remote activat", "Relay Remote activé", "Remote-Relay aktiviert"),
        "relay_disabled" to m("Remote relay disabled", "Relay Remote dezactivat", "Relay Remote désactivé", "Remote-Relay deaktiviert"),
        "disable_remote_relay" to m("Disable remote relay", "Dezactivează relay Remote", "Désactiver le relay Remote", "Remote-Relay deaktivieren"),
        "enable_remote_relay" to m("Enable remote relay", "Activează relay Remote", "Activer le relay Remote", "Remote-Relay aktivieren"),
        "deploy_desc" to m("Scan with your phone. Cloudflare will create a personal Worker and R2 mailbox from the public relay template. After deployment, open the relay URL, tap Copy relay URL, then return here and choose Configure relay URL.", "Scanează cu telefonul. Cloudflare va crea un Worker personal și un mailbox R2 din șablonul public de relay. După deploy, deschide URL-ul relay-ului, apasă Copy relay URL, apoi revino aici și alege Configurează URL relay.", "Scannez avec votre téléphone. Cloudflare créera un Worker personnel et une boîte R2 depuis le modèle public de relay. Après le déploiement, ouvrez l’URL du relay, touchez Copy relay URL, revenez ici puis choisissez Configurer l’URL du relay.", "Scanne mit dem Telefon. Cloudflare erstellt aus der öffentlichen Relay-Vorlage einen persönlichen Worker und eine R2-Mailbox. Öffne nach dem Deployment die Relay-URL, tippe auf Copy relay URL, kehre hierher zurück und wähle Relay-URL konfigurieren."),
        "deploy_copied" to m("Deploy link copied", "Link-ul de deploy a fost copiat", "Lien de déploiement copié", "Deploy-Link kopiert"),
        "relay_cleared" to m("Relay URL cleared; LAN mode is unchanged", "URL-ul relay-ului a fost șters; modul LAN rămâne neschimbat", "URL du relay effacée ; le mode LAN reste inchangé", "Relay-URL gelöscht; LAN-Modus bleibt unverändert"),
        "relay_invalid" to m("Enter a valid HTTPS URL, for example https://name.workers.dev", "Introdu un URL HTTPS valid, de exemplu https://name.workers.dev", "Saisissez une URL HTTPS valide, par exemple https://name.workers.dev", "Gib eine gültige HTTPS-URL ein, z. B. https://name.workers.dev"),
        "relay_saved" to m("Relay saved and enabled", "Relay salvat și activat", "Relay enregistré et activé", "Relay gespeichert und aktiviert"),
        "overlay_not_required" to m("Not required on this Android version", "Nu este necesar pe această versiune Android", "Non requis sur cette version d’Android", "Auf dieser Android-Version nicht erforderlich"),
        "overlay_already" to m("Background launch permission is already enabled. Restart the Bridge to activate it.", "Permisiunea pentru lansarea în fundal este deja activă. Repornește Bridge-ul pentru a o aplica.", "L’autorisation de lancement en arrière-plan est déjà activée. Redémarrez le Bridge pour l’appliquer.", "Die Berechtigung für Hintergrundstart ist bereits aktiviert. Starte den Bridge neu, um sie anzuwenden."),
        "ready" to m("READY ✓", "GATA ✓", "PRÊT ✓", "BEREIT ✓"),
        "needs_setup" to m("needs setup", "necesită configurare", "configuration requise", "Einrichtung nötig"),
        "reboot" to m("Reboot", "Restart", "Redémarrage", "Neustart"),
        "start_after_reboot" to m("Start after reboot", "Pornire după restart", "Démarrage après redémarrage", "Start nach Neustart"),
        "auto_helper" to m("Auto-start helper", "Helper auto-start", "Assistant auto-start", "Auto-start-Helfer"),
        "optional_off" to m("optional / OFF", "opțional / OPRIT", "facultatif / OFF", "optional / AUS"),
        "remote" to m("Remote", "Remote", "Remote", "Remote"),
        "connecting" to m("connecting…", "se conectează…", "connexion…", "Verbindung…"),
        "configured_disabled" to m("configured / disabled", "configurat / dezactivat", "configuré / désactivé", "konfiguriert / deaktiviert"),
        "not_configured_optional" to m("not configured (optional)", "neconfigurat (opțional)", "non configuré (facultatif)", "nicht konfiguriert (optional)"),
        "bridge" to m("Bridge", "Bridge", "Bridge", "Bridge"),
        "running" to m("RUNNING", "PORNIT", "ACTIF", "LÄUFT"),
        "stopped" to m("STOPPED", "OPRIT", "ARRÊTÉ", "GESTOPPT"),
        "local_web_ui" to m("Local Web UI", "Interfață Web Locală", "Interface Web locale", "Lokale Weboberfläche"),
        "lan_token_hint" to m("use Copy LAN API token when needed", "folosește Copiază tokenul API LAN când este necesar", "utilisez Copier le jeton API LAN si nécessaire", "bei Bedarf LAN-API-Token kopieren verwenden"),
        "detected_free" to m("detected (free)", "detectat (free)", "détecté (gratuit)", "erkannt (kostenlos)"),
        "detected_plus" to m("detected (Flud+)", "detectat (Flud+)", "détecté (Flud+)", "erkannt (Flud+)"),
        "not_detected" to m("not detected", "nedetectat", "non détecté", "nicht erkannt"),
        "background_launch" to m("Background launch", "Lansare în fundal", "Lancement en arrière-plan", "Hintergrundstart"),
        "permission_enabled" to m("permission enabled", "permisiune activă", "autorisation activée", "Berechtigung aktiviert"),
        "permission_not_enabled" to m("permission not enabled", "permisiune neactivată", "autorisation non activée", "Berechtigung nicht aktiviert"),
        "on" to m("ON", "PORNIT", "ON", "AN"),
        "off" to m("OFF", "OPRIT", "OFF", "AUS"),
        "disable_auto_start" to m("Disable auto-start", "Dezactivează auto-start", "Désactiver l’auto-start", "Autostart deaktivieren"),
        "enable_auto_start" to m("Enable auto-start", "Activează auto-start", "Activer l’auto-start", "Autostart aktivieren"),
        "helper_off_desc" to m("Auto-start helper: OFF — enable it in Android Accessibility if you want LAN or Remote automatic confirmation (Right → Right → OK is fallback only)", "Helper auto-start: OPRIT — activează-l în Accesibilitate Android dacă vrei confirmare automată LAN sau Remote (Dreapta → Dreapta → OK este doar fallback)", "Assistant auto-start : OFF — activez-le dans Accessibilité Android pour la confirmation automatique LAN ou Remote (Droite → Droite → OK uniquement en secours)", "Auto-start-Helfer: AUS — aktiviere ihn in den Android-Bedienungshilfen für automatische LAN- oder Remote-Bestätigung (Rechts → Rechts → OK nur als Fallback)"),
        "relay" to m("Relay", "Relay", "Relay", "Relay"),
        "not_configured" to m("not configured", "neconfigurat", "non configuré", "nicht konfiguriert"),
        "remote_device" to m("Remote device", "Dispozitiv Remote", "Appareil Remote", "Remote-Gerät"),
        "remote_token" to m("Remote token", "Token Remote", "Token Remote", "Remote-Token"),
        "remote_post" to m("Remote POST", "POST Remote", "POST Remote", "Remote POST"),
        "remote_post_unavailable" to m("not available until relay is configured", "indisponibil până la configurarea relay-ului", "indisponible tant que le relay n’est pas configuré", "nicht verfügbar, bis der Relay konfiguriert ist"),
        "last_none" to m("Last command: none yet", "Ultima comandă: încă niciuna", "Dernière commande : aucune", "Letzter Befehl: noch keiner"),
        "last_command" to m("Last command", "Ultima comandă", "Dernière commande", "Letzter Befehl"),
        "failed" to m("FAILED", "EȘUAT", "ÉCHEC", "FEHLER"),
        "remote_qr_caption" to m("REMOTE QR — scan with the phone camera to open your self-hosted PWA and pair Device ID + token automatically.", "QR REMOTE — scanează cu camera telefonului pentru a deschide PWA self-hosted și a asocia automat Device ID + token.", "QR REMOTE — scannez avec l’appareil photo du téléphone pour ouvrir votre PWA auto-hébergée et associer automatiquement Device ID + token.", "REMOTE QR — mit der Telefonkamera scannen, um die selbst gehostete PWA zu öffnen und Device ID + Token automatisch zu koppeln."),
        "local_qr_caption" to m("LOCAL QR — scan while connected to the same LAN to open the Android device Web UI and pair the LAN token automatically.", "QR LOCAL — scanează când ești conectat la același LAN pentru a deschide interfața Web a dispozitivului Android și a asocia automat tokenul LAN.", "QR LOCAL — scannez lorsque vous êtes connecté au même LAN pour ouvrir l’interface Web de l’appareil Android et associer automatiquement le token LAN.", "LOCAL QR — im selben LAN scannen, um die Weboberfläche des Android-Geräts zu öffnen und den LAN-Token automatisch zu koppeln."),
        "disclaimer" to m("Independent companion for Flud.\nNot affiliated with, endorsed by, or sponsored by Delphi Softwares or the Flud developers.\n“Flud” is used only to identify compatibility · alexlab.media", "Companion independent pentru Flud.\nNu este afiliat, aprobat sau sponsorizat de Delphi Softwares sau de dezvoltatorii Flud.\n„Flud” este folosit doar pentru a indica compatibilitatea · alexlab.media", "Companion indépendant pour Flud.\nNon affilié, approuvé ou sponsorisé par Delphi Softwares ou les développeurs de Flud.\n« Flud » est utilisé uniquement pour indiquer la compatibilité · alexlab.media", "Unabhängiger Companion für Flud.\nNicht mit Delphi Softwares oder den Flud-Entwicklern verbunden, von ihnen unterstützt oder gesponsert.\n„Flud“ wird nur zur Kennzeichnung der Kompatibilität verwendet · alexlab.media")
    )
}
