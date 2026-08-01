package com.sunny.skin.ui.i18n

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class SunnyLanguage(
    val persistedTag: String,
    val nativeName: String,
) {
    SYSTEM("system", "Use device language"),
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी"),
    SPANISH("es", "Español"),
    ITALIAN("it", "Italiano"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch"),
    PORTUGUESE_BRAZIL("pt-BR", "Português (Brasil)"),
    JAPANESE("ja", "日本語"),
    KOREAN("ko", "한국어"),
    CHINESE_SIMPLIFIED("zh-Hans", "简体中文"),
    CHINESE_TRADITIONAL("zh-Hant", "繁體中文");

    companion object {
        fun fromTag(tag: String?): SunnyLanguage = entries.firstOrNull {
            it.persistedTag == tag
        } ?: SYSTEM
    }
}

object SunnyLanguageController {
    private const val PREFS = "sunny_settings"
    private const val KEY = "app_language"
    private val mutableSelection = MutableStateFlow(SunnyLanguage.SYSTEM)
    val selection: StateFlow<SunnyLanguage> = mutableSelection.asStateFlow()

    fun initialize(context: Context) {
        SunnyTranslations.initialize(context)
        mutableSelection.value = SunnyLanguage.fromTag(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null),
        )
    }

    fun select(context: Context, language: SunnyLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, language.persistedTag)
            .apply()
        mutableSelection.value = language
    }
}

private val LocalSunnyLanguage = staticCompositionLocalOf { SunnyLanguage.ENGLISH }

@Composable
fun SunnyLocalizationProvider(
    selected: SunnyLanguage,
    content: @Composable () -> Unit,
) {
    val deviceLocale = LocalContext.current.resources.configuration.locales[0]
    val deviceLanguage = deviceLocale.language.lowercase(Locale.ROOT)
    val resolved = when (selected) {
        SunnyLanguage.SYSTEM -> when (deviceLanguage) {
            "hi" -> SunnyLanguage.HINDI
            "es" -> SunnyLanguage.SPANISH
            "it" -> SunnyLanguage.ITALIAN
            "fr" -> SunnyLanguage.FRENCH
            "de" -> SunnyLanguage.GERMAN
            "pt" -> SunnyLanguage.PORTUGUESE_BRAZIL
            "ja" -> SunnyLanguage.JAPANESE
            "ko" -> SunnyLanguage.KOREAN
            "zh" -> if (
                deviceLocale.script.equals("Hant", ignoreCase = true) ||
                deviceLocale.country.uppercase(Locale.ROOT) in setOf("TW", "HK", "MO")
            ) SunnyLanguage.CHINESE_TRADITIONAL else SunnyLanguage.CHINESE_SIMPLIFIED
            else -> SunnyLanguage.ENGLISH
        }
        else -> selected
    }
    CompositionLocalProvider(LocalSunnyLanguage provides resolved, content = content)
}

@Composable
fun translated(text: String): String = translateFor(LocalSunnyLanguage.current, text)

internal fun translateFor(language: SunnyLanguage, text: String): String =
    SunnyTranslations.translate(language, text)

private object SunnyTranslations {
    private val generated = mutableMapOf<SunnyLanguage, Map<String, String>>()

    @Synchronized
    fun initialize(context: Context) {
        if (generated.isNotEmpty()) return
        val languages = listOf(
            SunnyLanguage.ITALIAN,
            SunnyLanguage.FRENCH,
            SunnyLanguage.GERMAN,
            SunnyLanguage.PORTUGUESE_BRAZIL,
            SunnyLanguage.JAPANESE,
            SunnyLanguage.KOREAN,
            SunnyLanguage.CHINESE_SIMPLIFIED,
            SunnyLanguage.CHINESE_TRADITIONAL,
        )
        languages.forEach { language ->
            generated[language] = runCatching {
                val json = context.assets.open("i18n/${language.persistedTag}.json")
                    .bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
                buildMap {
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        put(key, json.getString(key))
                    }
                }
            }.getOrDefault(emptyMap())
        }
    }

    fun translate(language: SunnyLanguage, text: String): String {
        if (language == SunnyLanguage.ENGLISH || text.isBlank()) return text
        val exact = when (language) {
            SunnyLanguage.HINDI -> hindi[text]
            SunnyLanguage.SPANISH -> spanish[text]
            SunnyLanguage.SYSTEM, SunnyLanguage.ENGLISH -> null
            else -> generated[language]?.get(text)
        }
        return exact ?: dynamic(language, text) ?: text
    }

    private fun dynamic(language: SunnyLanguage, text: String): String? {
        val selected = Regex("^(\\d+) selected$").matchEntire(text)?.groupValues?.get(1)
        if (selected != null) return when (language) {
            SunnyLanguage.HINDI -> "$selected चयनित"
            SunnyLanguage.SPANISH -> "$selected seleccionados"
            SunnyLanguage.ITALIAN -> "$selected selezionati"
            SunnyLanguage.FRENCH -> "$selected sélectionnés"
            SunnyLanguage.GERMAN -> "$selected ausgewählt"
            SunnyLanguage.PORTUGUESE_BRAZIL -> "$selected selecionados"
            SunnyLanguage.JAPANESE -> "$selected 件選択"
            SunnyLanguage.KOREAN -> "$selected 개 선택됨"
            SunnyLanguage.CHINESE_SIMPLIFIED -> "已选择 $selected 项"
            SunnyLanguage.CHINESE_TRADITIONAL -> "已選取 $selected 項"
            SunnyLanguage.SYSTEM, SunnyLanguage.ENGLISH -> null
        }
        val photos = Regex("^(\\d+) photos$").matchEntire(text)?.groupValues?.get(1)
        if (photos != null) return when (language) {
            SunnyLanguage.HINDI -> "$photos फ़ोटो"
            SunnyLanguage.SPANISH -> "$photos fotos"
            SunnyLanguage.ITALIAN -> "$photos foto"
            SunnyLanguage.FRENCH -> "$photos photos"
            SunnyLanguage.GERMAN -> "$photos Fotos"
            SunnyLanguage.PORTUGUESE_BRAZIL -> "$photos fotos"
            SunnyLanguage.JAPANESE -> "$photos 枚の写真"
            SunnyLanguage.KOREAN -> "사진 $photos 장"
            SunnyLanguage.CHINESE_SIMPLIFIED -> "$photos 张照片"
            SunnyLanguage.CHINESE_TRADITIONAL -> "$photos 張照片"
            SunnyLanguage.SYSTEM, SunnyLanguage.ENGLISH -> null
        }
        val scans = Regex("^(\\d+) scans$").matchEntire(text)?.groupValues?.get(1)
        if (scans != null) return when (language) {
            SunnyLanguage.HINDI -> "$scans स्कैन"
            SunnyLanguage.SPANISH -> "$scans escaneos"
            SunnyLanguage.ITALIAN -> "$scans scansioni"
            SunnyLanguage.FRENCH -> "$scans scans"
            SunnyLanguage.GERMAN -> "$scans Scans"
            SunnyLanguage.PORTUGUESE_BRAZIL -> "$scans registros"
            SunnyLanguage.JAPANESE -> "$scans 件のスキャン"
            SunnyLanguage.KOREAN -> "스캔 $scans 개"
            SunnyLanguage.CHINESE_SIMPLIFIED -> "$scans 次扫描"
            SunnyLanguage.CHINESE_TRADITIONAL -> "$scans 次掃描"
            SunnyLanguage.SYSTEM, SunnyLanguage.ENGLISH -> null
        }
        val attempts = Regex("^Too many attempts\\. Try again in (\\d+)s\\.$")
            .matchEntire(text)?.groupValues?.get(1)
        if (attempts != null) return when (language) {
            SunnyLanguage.HINDI -> "बहुत अधिक प्रयास। $attempts सेकंड बाद फिर कोशिश करें।"
            SunnyLanguage.SPANISH -> "Demasiados intentos. Inténtalo de nuevo en $attempts s."
            SunnyLanguage.ITALIAN -> "Troppi tentativi. Riprova tra $attempts s."
            SunnyLanguage.FRENCH -> "Trop de tentatives. Réessayez dans $attempts s."
            SunnyLanguage.GERMAN -> "Zu viele Versuche. In $attempts s erneut versuchen."
            SunnyLanguage.PORTUGUESE_BRAZIL -> "Muitas tentativas. Tente novamente em $attempts s."
            SunnyLanguage.JAPANESE -> "試行回数が多すぎます。$attempts 秒後に再試行してください。"
            SunnyLanguage.KOREAN -> "시도 횟수가 너무 많습니다. $attempts 초 후 다시 시도하세요."
            SunnyLanguage.CHINESE_SIMPLIFIED -> "尝试次数过多。请在 $attempts 秒后重试。"
            SunnyLanguage.CHINESE_TRADITIONAL -> "嘗試次數過多。請在 $attempts 秒後重試。"
            SunnyLanguage.SYSTEM, SunnyLanguage.ENGLISH -> null
        }
        return null
    }

    private val hindi = mapOf(
        "Overview" to "अवलोकन",
        "Saved" to "सहेजे गए",
        "Areas" to "क्षेत्र",
        "Capture" to "कैप्चर",
        "Add photo" to "फ़ोटो जोड़ें",
        "Settings" to "सेटिंग्स",
        "Reports" to "रिपोर्ट",
        "Back" to "वापस",
        "Done" to "हो गया",
        "Close" to "बंद करें",
        "Cancel" to "रद्द करें",
        "Continue" to "जारी रखें",
        "Save" to "सहेजें",
        "Edit" to "संपादित करें",
        "Delete" to "हटाएँ",
        "Remove" to "निकालें",
        "Share" to "साझा करें",
        "Export" to "निर्यात करें",
        "Apply" to "लागू करें",
        "Skip" to "छोड़ें",
        "Try again" to "फिर कोशिश करें",
        "Got it" to "समझ गया",
        "Yes" to "हाँ",
        "No" to "नहीं",
        "All" to "सभी",
        "Off" to "बंद",
        "Ready" to "तैयार",
        "Selected" to "चयनित",
        "Not selected" to "चयनित नहीं",
        "Name" to "नाम",
        "Note" to "नोट",
        "Photo" to "फ़ोटो",
        "Photos" to "फ़ोटो",
        "Scans" to "स्कैन",
        "Analysis" to "विश्लेषण",
        "Analyse" to "विश्लेषण करें",
        "Analysing…" to "विश्लेषण हो रहा है…",
        "Compare" to "तुलना करें",
        "Follow-up" to "फ़ॉलो-अप",
        "Summary" to "सारांश",
        "Privacy" to "गोपनीयता",
        "About" to "परिचय",
        "Version" to "संस्करण",
        "Membership" to "सदस्यता",
        "Reminders" to "रिमाइंडर",
        "Description" to "विवरण",
        "Body area" to "शरीर का क्षेत्र",
        "Body Area" to "शरीर का क्षेत्र",
        "Visible area" to "दिखाई देने वाला क्षेत्र",
        "Approximate size" to "अनुमानित आकार",
        "Private note" to "निजी नोट",
        "What changed" to "क्या बदला",
        "Colour" to "रंग",
        "Before" to "पहले",
        "After" to "बाद में",
        "Before (older)" to "पहले (पुराना)",
        "After (newer)" to "बाद में (नया)",
        "Take photo" to "फ़ोटो लें",
        "Take Photo" to "फ़ोटो लें",
        "Take a photo" to "फ़ोटो लें",
        "Choose from Library" to "लाइब्रेरी से चुनें",
        "Choose from library" to "लाइब्रेरी से चुनें",
        "Use a clear, close-up photo of one area. Sunny will check lighting and detail before analysis." to "एक क्षेत्र की साफ़, नज़दीकी फ़ोटो लें। विश्लेषण से पहले Sunny रोशनी और विवरण जाँचेगा।",
        "Use live framing and lighting guidance" to "लाइव फ़्रेमिंग और रोशनी मार्गदर्शन उपयोग करें",
        "Use a photo already on this phone" to "इस फ़ोन पर मौजूद फ़ोटो उपयोग करें",
        "That photo could not be opened. Choose another image." to "यह फ़ोटो खोली नहीं जा सकी। कोई दूसरी फ़ोटो चुनें।",
        "Capture head to toe with framing tips for each zone" to "हर क्षेत्र के फ़्रेमिंग सुझावों के साथ सिर से पैर तक कैप्चर करें",
        "Review photo" to "फ़ोटो की समीक्षा",
        "Review follow-up" to "फ़ॉलो-अप की समीक्षा",
        "Save photo" to "फ़ोटो सहेजें",
        "Save follow-up" to "फ़ॉलो-अप सहेजें",
        "Analysing photo…" to "फ़ोटो का विश्लेषण हो रहा है…",
        "Resolve analysis to save" to "सहेजने से पहले विश्लेषण पूरा करें",
        "Retake photo" to "फ़ोटो फिर लें",
        "Change Photo" to "फ़ोटो बदलें",
        "Run analysis" to "विश्लेषण चलाएँ",
        "Re-run analysis" to "विश्लेषण फिर चलाएँ",
        "Cancel analysis" to "विश्लेषण रद्द करें",
        "Camera access is needed" to "कैमरा अनुमति आवश्यक है",
        "Open app settings" to "ऐप सेटिंग्स खोलें",
        "Flip camera" to "कैमरा पलटें",
        "Preparing your photo" to "आपकी फ़ोटो तैयार हो रही है",
        "Checking light and detail…" to "रोशनी और विवरण जाँचे जा रहे हैं…",
        "Exposure and detail look good" to "रोशनी और विवरण अच्छे हैं",
        "Reduce glare or direct flash" to "चमक या सीधी फ्लैश कम करें",
        "Refocus and hold the phone steady" to "फोकस करें और फ़ोन स्थिर रखें",
        "Couldn't read this image" to "यह चित्र पढ़ा नहीं जा सका",
        "Analysis unavailable" to "विश्लेषण उपलब्ध नहीं है",
        "Analysis cancelled" to "विश्लेषण रद्द किया गया",
        "Cloud analysis" to "क्लाउड विश्लेषण",
        "Offline analysis" to "ऑफ़लाइन विश्लेषण",
        "Cloud" to "क्लाउड",
        "On device" to "डिवाइस पर",
        "Where analysis runs" to "विश्लेषण कहाँ चलता है",
        "You can change this anytime" to "आप इसे कभी भी बदल सकते हैं",
        "Cloud analysis is ready" to "क्लाउड विश्लेषण तैयार है",
        "Offline analysis is ready" to "ऑफ़लाइन विश्लेषण तैयार है",
        "Connecting securely to Sunny AI Cloud." to "Sunny AI Cloud से सुरक्षित रूप से जुड़ रहा है।",
        "Fast and requires no download. Photos are securely sent to Sunny for analysis." to "तेज़ है और डाउनलोड की आवश्यकता नहीं। विश्लेषण के लिए फ़ोटो सुरक्षित रूप से Sunny को भेजी जाती हैं।",
        "Works without internet and keeps analysis on this phone." to "इंटरनेट के बिना काम करता है और विश्लेषण इसी फ़ोन पर रखता है।",
        "Download offline analysis" to "ऑफ़लाइन विश्लेषण डाउनलोड करें",
        "Download offline model" to "ऑफ़लाइन मॉडल डाउनलोड करें",
        "Download" to "डाउनलोड",
        "Downloading model" to "मॉडल डाउनलोड हो रहा है",
        "Verifying" to "सत्यापन हो रहा है",
        "Verify" to "सत्यापित करें",
        "Installed" to "इंस्टॉल किया गया",
        "Offline setup" to "ऑफ़लाइन सेटअप",
        "Analysis setup" to "विश्लेषण सेटअप",
        "Analysis options" to "विश्लेषण विकल्प",
        "Download using" to "डाउनलोड माध्यम",
        "Wi-Fi only" to "केवल Wi‑Fi",
        "Wi-Fi + mobile" to "Wi‑Fi + मोबाइल",
        "Mobile-data charges may apply. The download resumes if interrupted." to "मोबाइल डेटा शुल्क लग सकता है। रुकने पर डाउनलोड फिर शुरू हो जाएगा।",
        "Cloud analysis ready" to "क्लाउड विश्लेषण तैयार",
        "Offline analysis installed" to "ऑफ़लाइन विश्लेषण इंस्टॉल है",
        "Download paused" to "डाउनलोड रुका हुआ है",
        "Retry download" to "डाउनलोड फिर शुरू करें",
        "Pro required" to "Pro आवश्यक",
        "View Pro" to "Pro देखें",
        "Device vault" to "डिवाइस वॉल्ट",
        "Your protection settings at a glance" to "आपकी सुरक्षा सेटिंग्स एक नज़र में",
        "App lock" to "ऐप लॉक",
        "App Lock (PIN)" to "ऐप लॉक (PIN)",
        "PIN optional" to "PIN वैकल्पिक",
        "PIN on" to "PIN चालू",
        "Encrypted local" to "स्थानीय रूप से एन्क्रिप्टेड",
        "Privacy & Security" to "गोपनीयता और सुरक्षा",
        "PRIVACY & SECURITY" to "गोपनीयता और सुरक्षा",
        "Your data" to "आपका डेटा",
        "Your control" to "आपका नियंत्रण",
        "Delete all local data" to "सारा स्थानीय डेटा हटाएँ",
        "Delete all local data?" to "सारा स्थानीय डेटा हटाएँ?",
        "Delete everything" to "सब कुछ हटाएँ",
        "This cannot be undone." to "इसे वापस नहीं किया जा सकता।",
        "Local data deleted" to "स्थानीय डेटा हटाया गया",
        "Your device vault is empty." to "आपका डिवाइस वॉल्ट खाली है।",
        "Privacy Policy" to "गोपनीयता नीति",
        "Medical Disclaimer" to "चिकित्सीय अस्वीकरण",
        "Help improve Sunny" to "Sunny को बेहतर बनाने में मदद करें",
        "Contribute to improving Sunny" to "Sunny को बेहतर बनाने में योगदान दें",
        "I agree" to "मैं सहमत हूँ",
        "Not now" to "अभी नहीं",
        "Enter Sunny" to "Sunny खोलें",
        "Sunny is Locked" to "Sunny लॉक है",
        "Your health data is protected. Authenticate to continue." to "आपका स्वास्थ्य डेटा सुरक्षित है। जारी रखने के लिए प्रमाणित करें।",
        "Unlock with PIN" to "PIN से अनलॉक करें",
        "Enter your PIN" to "अपना PIN दर्ज करें",
        "Enter your 4-digit PIN to unlock Sunny." to "Sunny अनलॉक करने के लिए 4 अंकों का PIN दर्ज करें।",
        "Incorrect PIN. Try again." to "गलत PIN। फिर कोशिश करें।",
        "Create a PIN" to "PIN बनाएँ",
        "Choose a 4-digit PIN to protect your skin health data." to "अपने त्वचा रिकॉर्ड की सुरक्षा के लिए 4 अंकों का PIN चुनें।",
        "Confirm your PIN" to "अपने PIN की पुष्टि करें",
        "Re-enter your PIN to confirm." to "पुष्टि के लिए PIN फिर दर्ज करें।",
        "Change PIN" to "PIN बदलें",
        "PINs didn't match. Start over." to "PIN मेल नहीं खाते। फिर शुरू करें।",
        "Get started" to "शुरू करें",
        "Build a clear baseline" to "स्पष्ट आधार बनाएँ",
        "Follow the visual story" to "दृश्य बदलावों की कहानी देखें",
        "A private visual journal for photographing and comparing visible skin changes over time." to "समय के साथ दिखाई देने वाले त्वचा बदलावों की फ़ोटो और तुलना के लिए निजी विज़ुअल जर्नल।",
        "Made for tracking, not diagnosis" to "ट्रैकिंग के लिए, निदान के लिए नहीं",
        "Track visible skin changes over time" to "समय के साथ दिखाई देने वाले त्वचा बदलाव ट्रैक करें",
        "Add your first photo" to "अपनी पहली फ़ोटो जोड़ें",
        "Take your first photo to begin tracking." to "ट्रैकिंग शुरू करने के लिए पहली फ़ोटो लें।",
        "No saved areas yet" to "अभी कोई सहेजा हुआ क्षेत्र नहीं",
        "No photos saved" to "कोई फ़ोटो सहेजी नहीं गई",
        "No matching areas" to "कोई मेल खाता क्षेत्र नहीं",
        "Search tracked areas" to "ट्रैक किए गए क्षेत्र खोजें",
        "Recently updated" to "हाल में अपडेट किए गए",
        "Oldest updated" to "सबसे पुराने अपडेट",
        "Sort saved scans" to "सहेजे स्कैन क्रमबद्ध करें",
        "Clear search" to "खोज साफ़ करें",
        "Select all" to "सभी चुनें",
        "Clear all" to "सभी हटाएँ",
        "Tip: long-press an area to select several." to "सुझाव: कई क्षेत्रों को चुनने के लिए किसी क्षेत्र को देर तक दबाएँ।",
        "Scan photo" to "स्कैन फ़ोटो",
        "Photo history" to "फ़ोटो इतिहास",
        "Compare over time" to "समय के साथ तुलना",
        "Edit tracked area" to "ट्रैक किया क्षेत्र संपादित करें",
        "Delete this tracked area?" to "यह ट्रैक किया क्षेत्र हटाएँ?",
        "Reminder interval" to "रिमाइंडर अंतराल",
        "Set interval" to "अंतराल सेट करें",
        "Set Reminder" to "रिमाइंडर सेट करें",
        "Remove reminder" to "रिमाइंडर हटाएँ",
        "Reminder Center" to "रिमाइंडर केंद्र",
        "No spot reminders scheduled." to "कोई स्पॉट रिमाइंडर तय नहीं है।",
        "Regular skin check" to "नियमित त्वचा जाँच",
        "Hours" to "घंटे",
        "Days" to "दिन",
        "Weeks" to "सप्ताह",
        "Months" to "महीने",
        "Report" to "रिपोर्ट",
        "Create report" to "रिपोर्ट बनाएँ",
        "Generate Report" to "रिपोर्ट तैयार करें",
        "Create clinician-ready reports" to "चिकित्सक के लिए उपयोगी रिपोर्ट बनाएँ",
        "No reports yet" to "अभी कोई रिपोर्ट नहीं",
        "Reports you save will appear here." to "आपकी सहेजी रिपोर्ट यहाँ दिखाई देंगी।",
        "Report Preview" to "रिपोर्ट पूर्वावलोकन",
        "Generate report" to "रिपोर्ट तैयार करें",
        "Generating report…" to "रिपोर्ट तैयार हो रही है…",
        "Report saved" to "रिपोर्ट सहेजी गई",
        "Share report" to "रिपोर्ट साझा करें",
        "Delete report" to "रिपोर्ट हटाएँ",
        "Delete this report?" to "यह रिपोर्ट हटाएँ?",
        "Report unavailable" to "रिपोर्ट उपलब्ध नहीं है",
        "Photo Check" to "फ़ोटो जाँच",
        "Full-body photo check" to "पूरे शरीर की फ़ोटो जाँच",
        "Start photo check" to "फ़ोटो जाँच शुरू करें",
        "Continue photo check" to "फ़ोटो जाँच जारी रखें",
        "Finish session" to "सत्र समाप्त करें",
        "End session" to "सत्र समाप्त करें",
        "Photo check complete" to "फ़ोटो जाँच पूरी हुई",
        "The ABCDE guide" to "ABCDE मार्गदर्शिका",
        "ABCDE self-check" to "ABCDE स्व-जाँच",
        "Educational checklist" to "शैक्षिक चेकलिस्ट",
        "Not sure" to "निश्चित नहीं",
        "See a professional for anything that concerns you." to "किसी भी चिंता के लिए स्वास्थ्य विशेषज्ञ से मिलें।",
        "Create" to "बनाएँ",
        "Discard" to "छोड़ें",
        "Filter:" to "फ़िल्टर:",
        "changed" to "बदला हुआ",
        "Interval" to "अंतराल",
        "Password" to "पासवर्ड",
        "Aligning…" to "संरेखित हो रहा है…",
        "Keep editing" to "संपादन जारी रखें",
        "Keep checking" to "जाँच जारी रखें",
        "What's this?" to "यह क्या है?",
        "Body Coverage" to "शरीर कवरेज",
        "Tracked Areas" to "ट्रैक किए क्षेत्र",
        "Analyse anyway" to "फिर भी विश्लेषण करें",
        "Sunny Analysis" to "Sunny विश्लेषण",
        "Opening report…" to "रिपोर्ट खुल रही है…",
        "Confirm password" to "पासवर्ड की पुष्टि करें",
        "Encrypted backup" to "एन्क्रिप्टेड बैकअप",
        "Save measurement" to "माप सहेजें",
        "Where a scan goes" to "स्कैन कहाँ जाता है",
        "Retake recommended" to "फ़ोटो फिर लेने की सलाह",
        "Showing raw photos" to "मूल फ़ोटो दिखाई जा रही हैं",
        "Sunny visual report" to "Sunny विज़ुअल रिपोर्ट",
        "Filter by date range" to "तारीख सीमा से फ़िल्टर करें",
        "Skin tracking report" to "त्वचा ट्रैकिंग रिपोर्ट",
        "Use offline analysis" to "ऑफ़लाइन विश्लेषण उपयोग करें",
        "End this photo check?" to "यह फ़ोटो जाँच समाप्त करें?",
        "Remind me to re-check" to "फिर जाँचने की याद दिलाएँ",
        "Contribute beta scans?" to "बीटा स्कैन में योगदान दें?",
        "Passwords do not match" to "पासवर्ड मेल नहीं खाते",
        "Save Report to Device?" to "रिपोर्ट डिवाइस पर सहेजें?",
        "Upcoming spot reminders" to "आगामी स्पॉट रिमाइंडर",
        "Discard unsaved changes?" to "बिना सहेजे बदलाव छोड़ें?",
        "Description needs refresh" to "विवरण को रीफ़्रेश करना होगा",
        "Reference-based estimates" to "संदर्भ-आधारित अनुमान",
        "Use at least 10 characters" to "कम से कम 10 अक्षर उपयोग करें",
        "Require a PIN to open Sunny" to "Sunny खोलने के लिए PIN आवश्यक करें",
        "Find a dermatologist near you" to "अपने पास त्वचा विशेषज्ञ खोजें",
        "Manage or cancel subscription" to "सदस्यता प्रबंधित या रद्द करें",
        "Reason for sharing (optional)" to "साझा करने का कारण (वैकल्पिक)",
        "Reference width in millimetres" to "संदर्भ चौड़ाई मिलीमीटर में",
        "A private, on-device PDF preview" to "निजी, डिवाइस पर PDF पूर्वावलोकन",
        "No tracked areas match these filters." to "इन फ़िल्टर से कोई ट्रैक किया क्षेत्र मेल नहीं खाता।",
        "Stored only with this encrypted scan." to "केवल इस एन्क्रिप्टेड स्कैन के साथ संग्रहीत।",
        "Get a nudge to re-photograph this spot" to "इस स्थान की फिर फ़ोटो लेने की याद पाएँ",
        "Remove every scan, reminder and report" to "हर स्कैन, रिमाइंडर और रिपोर्ट हटाएँ",
        "Photos could not be aligned confidently" to "फ़ोटो विश्वसनीय रूप से संरेखित नहीं हो सकीं",
        "Keep Sunny open until analysis finishes." to "विश्लेषण पूरा होने तक Sunny खुला रखें।",
        "Offline analysis is installed and ready." to "ऑफ़लाइन विश्लेषण इंस्टॉल और तैयार है।",
        "Drag the top photo to fine-tune the match." to "मिलान सुधारने के लिए ऊपर की फ़ोटो खींचें।",
        "Export scans, notes, reminders and reports" to "स्कैन, नोट, रिमाइंडर और रिपोर्ट निर्यात करें",
        "Opens Maps — your photos stay on this phone" to "Maps खुलता है — आपकी फ़ोटो इसी फ़ोन पर रहती हैं",
        "Decrypting and preparing pages on this device" to "इस डिवाइस पर पृष्ठ डिक्रिप्ट और तैयार हो रहे हैं",
        "Create a baseline for one area you want to track" to "जिस क्षेत्र को ट्रैक करना है उसका आधार बनाएँ",
        "If enabled, selected photos are sent over HTTPS." to "चालू होने पर चुनी गई फ़ोटो HTTPS से भेजी जाती हैं।",
        "Use a password you can remember. Sunny cannot recover it." to "ऐसा पासवर्ड रखें जो याद रहे। Sunny इसे वापस नहीं ला सकता।",
        "Five things dermatologists teach people to notice on a mole." to "तिल पर ध्यान देने योग्य पाँच बातें जो त्वचा विशेषज्ञ बताते हैं।",
        "Keep framing and lighting consistent for useful comparisons." to "उपयोगी तुलना के लिए फ़्रेमिंग और रोशनी समान रखें।",
        "Drag the divider — older photo on the left, newer photo on the right." to "डिवाइडर खींचें — पुरानी फ़ोटो बाईं ओर और नई दाईं ओर।",
        "Changes here are a prompt to show a clinician — never a verdict." to "यहाँ के बदलाव चिकित्सक को दिखाने का संकेत हैं — कोई निष्कर्ष नहीं।",
        "Take at least two photos of this spot to compare them over time." to "समय के साथ तुलना के लिए इस स्थान की कम से कम दो फ़ोटो लें।",
        "For consistent personal tracking only; not a clinical measurement." to "केवल लगातार व्यक्तिगत ट्रैकिंग के लिए; चिकित्सीय माप नहीं।",
        "The encrypted report will be permanently removed from this phone." to "एन्क्रिप्टेड रिपोर्ट इस फ़ोन से स्थायी रूप से हट जाएगी।",
        "Your edited name, photo or refreshed description will not be saved." to "संपादित नाम, फ़ोटो या नया विवरण सहेजा नहीं जाएगा।",
        "The photo remains private and unsaved until you retry or discard it." to "दोबारा कोशिश या छोड़ने तक फ़ोटो निजी और बिना सहेजी रहेगी।",
        "When enabled, you will need to enter your PIN each time you open Sunny." to "चालू होने पर Sunny खोलते समय हर बार PIN दर्ज करना होगा।",
        "Cloud is selected. Installing offline analysis is optional for Pro users." to "क्लाउड चुना गया है। Pro उपयोगकर्ताओं के लिए ऑफ़लाइन विश्लेषण इंस्टॉल करना वैकल्पिक है।",
        "Try again with a well-lit, close-up, filled-frame photo of a single spot." to "एक स्थान की अच्छी रोशनी वाली, नज़दीकी और पूरा फ़्रेम भरने वाली फ़ोटो से फिर कोशिश करें।",
        "The checklist progress will be cleared. Your saved photos are not affected." to "चेकलिस्ट प्रगति साफ़ होगी। सहेजी फ़ोटो प्रभावित नहीं होंगी।",
        "A personal photo checklist. It does not confirm a complete skin examination." to "निजी फ़ोटो चेकलिस्ट। यह पूर्ण त्वचा परीक्षण की पुष्टि नहीं करती।",
        "These estimates depend on marker placement and are not clinical measurements." to "ये अनुमान मार्कर की स्थिति पर निर्भर हैं और चिकित्सीय माप नहीं हैं।",
        "Use the cloud without a download, or install private offline analysis with Pro." to "बिना डाउनलोड क्लाउड उपयोग करें, या Pro के साथ निजी ऑफ़लाइन विश्लेषण इंस्टॉल करें।",
        "Sunny will nudge you to re-photograph this spot so you can compare it over time." to "Sunny इस स्थान की फिर फ़ोटो लेने की याद दिलाएगा ताकि समय के साथ तुलना कर सकें।",
        "Allow camera access to take a photo. You can still choose an existing photo from your library." to "फ़ोटो लेने के लिए कैमरा अनुमति दें। आप लाइब्रेरी से मौजूदा फ़ोटो भी चुन सकते हैं।",
        "Cloud analysis does not download a model. Offline analysis stores 3.08 GB privately on this phone." to "क्लाउड विश्लेषण मॉडल डाउनलोड नहीं करता। ऑफ़लाइन विश्लेषण इस फ़ोन पर निजी रूप से 3.08 GB रखता है।",
        "Sunny is a skin tracking tool only. It does not provide medical diagnoses or advice. Always consult a qualified healthcare professional for any skin concerns." to "Sunny केवल त्वचा ट्रैकिंग का साधन है। यह चिकित्सीय निदान या सलाह नहीं देता। त्वचा संबंधी किसी भी चिंता के लिए योग्य स्वास्थ्य विशेषज्ञ से परामर्श करें।",
        "Scan photos are sent to Sunny AI Cloud for visual description." to "दृश्य विवरण के लिए स्कैन फ़ोटो Sunny AI Cloud को भेजी जाती हैं।",
        "Scan photos are processed and stored on this phone." to "स्कैन फ़ोटो इसी फ़ोन पर संसाधित और संग्रहीत की जाती हैं।",
        "I understand Sunny does not diagnose, assess risk, or tell me when it is safe to wait. My scan photos will be sent to Sunny AI Cloud for processing." to "मैं समझता हूँ कि Sunny निदान या जोखिम का आकलन नहीं करता और प्रतीक्षा करना सुरक्षित है या नहीं, यह नहीं बताता। मेरी स्कैन फ़ोटो संसाधन के लिए Sunny AI Cloud को भेजी जाएँगी।",
        "I understand Sunny does not diagnose, assess risk, or tell me when it is safe to wait. I will seek professional care for concerns." to "मैं समझता हूँ कि Sunny निदान या जोखिम का आकलन नहीं करता और प्रतीक्षा करना सुरक्षित है या नहीं, यह नहीं बताता। चिंता होने पर मैं विशेषज्ञ की सलाह लूँगा।",
        "Analysing with Sunny AI Cloud." to "Sunny AI Cloud से विश्लेषण हो रहा है।",
        "Running on-device. Your photo never leaves this phone." to "डिवाइस पर चल रहा है। आपकी फ़ोटो इस फ़ोन से बाहर नहीं जाती।",
        "Saved data is encrypted on this device. Scan photos are sent to Sunny AI Cloud." to "सहेजा डेटा इस डिवाइस पर एन्क्रिप्टेड है। स्कैन फ़ोटो Sunny AI Cloud को भेजी जाती हैं।",
        "Scans stay on this device unless you explicitly share an export." to "जब तक आप निर्यात साझा नहीं करते, स्कैन इसी डिवाइस पर रहते हैं।",
        "Sunny AI Cloud is unavailable. Check your connection and try again." to "Sunny AI Cloud उपलब्ध नहीं है। कनेक्शन जाँचें और फिर कोशिश करें।",
        "The installed AI model could not start. Install it from Settings before scanning." to "इंस्टॉल किया AI मॉडल शुरू नहीं हो सका। स्कैन से पहले इसे सेटिंग्स से इंस्टॉल करें।",
        "Language & region" to "भाषा और क्षेत्र",
        "App language" to "ऐप की भाषा",
        "Choose language" to "भाषा चुनें",
        "Use device language" to "डिवाइस की भाषा उपयोग करें",
        "English" to "अंग्रेज़ी",
        "Connecting…" to "कनेक्ट हो रहा है…"
    )

    private val spanish = mapOf(
        "Overview" to "Resumen",
        "Saved" to "Guardados",
        "Areas" to "Zonas",
        "Capture" to "Capturar",
        "Add photo" to "Añadir foto",
        "Settings" to "Ajustes",
        "Reports" to "Informes",
        "Back" to "Atrás",
        "Done" to "Listo",
        "Close" to "Cerrar",
        "Cancel" to "Cancelar",
        "Continue" to "Continuar",
        "Save" to "Guardar",
        "Edit" to "Editar",
        "Delete" to "Eliminar",
        "Remove" to "Quitar",
        "Share" to "Compartir",
        "Export" to "Exportar",
        "Apply" to "Aplicar",
        "Skip" to "Omitir",
        "Try again" to "Intentar de nuevo",
        "Got it" to "Entendido",
        "Yes" to "Sí",
        "No" to "No",
        "All" to "Todos",
        "Off" to "Desactivado",
        "Ready" to "Listo",
        "Selected" to "Seleccionado",
        "Not selected" to "No seleccionado",
        "Name" to "Nombre",
        "Note" to "Nota",
        "Photo" to "Foto",
        "Photos" to "Fotos",
        "Scans" to "Escaneos",
        "Analysis" to "Análisis",
        "Analyse" to "Analizar",
        "Analysing…" to "Analizando…",
        "Compare" to "Comparar",
        "Follow-up" to "Seguimiento",
        "Summary" to "Resumen",
        "Privacy" to "Privacidad",
        "About" to "Acerca de",
        "Version" to "Versión",
        "Membership" to "Membresía",
        "Reminders" to "Recordatorios",
        "Description" to "Descripción",
        "Body area" to "Zona del cuerpo",
        "Body Area" to "Zona del cuerpo",
        "Visible area" to "Área visible",
        "Approximate size" to "Tamaño aproximado",
        "Private note" to "Nota privada",
        "What changed" to "Qué cambió",
        "Colour" to "Color",
        "Before" to "Antes",
        "After" to "Después",
        "Before (older)" to "Antes (anterior)",
        "After (newer)" to "Después (reciente)",
        "Take photo" to "Tomar foto",
        "Take Photo" to "Tomar foto",
        "Take a photo" to "Tomar una foto",
        "Choose from Library" to "Elegir de la galería",
        "Choose from library" to "Elegir de la galería",
        "Use a clear, close-up photo of one area. Sunny will check lighting and detail before analysis." to "Usa una foto clara y cercana de una zona. Sunny comprobará la iluminación y el detalle antes del análisis.",
        "Use live framing and lighting guidance" to "Usa la guía de encuadre e iluminación en directo",
        "Use a photo already on this phone" to "Usa una foto que ya esté en este teléfono",
        "That photo could not be opened. Choose another image." to "No se pudo abrir esa foto. Elige otra imagen.",
        "Capture head to toe with framing tips for each zone" to "Captura de cabeza a pies con consejos de encuadre para cada zona",
        "Review photo" to "Revisar foto",
        "Review follow-up" to "Revisar seguimiento",
        "Save photo" to "Guardar foto",
        "Save follow-up" to "Guardar seguimiento",
        "Analysing photo…" to "Analizando foto…",
        "Resolve analysis to save" to "Completa el análisis para guardar",
        "Retake photo" to "Repetir foto",
        "Change Photo" to "Cambiar foto",
        "Run analysis" to "Ejecutar análisis",
        "Re-run analysis" to "Repetir análisis",
        "Cancel analysis" to "Cancelar análisis",
        "Camera access is needed" to "Se necesita acceso a la cámara",
        "Open app settings" to "Abrir ajustes de la app",
        "Flip camera" to "Cambiar cámara",
        "Preparing your photo" to "Preparando tu foto",
        "Checking light and detail…" to "Comprobando luz y detalle…",
        "Exposure and detail look good" to "La exposición y el detalle son correctos",
        "Reduce glare or direct flash" to "Reduce el brillo o el flash directo",
        "Refocus and hold the phone steady" to "Vuelve a enfocar y mantén el teléfono estable",
        "Couldn't read this image" to "No se pudo leer esta imagen",
        "Analysis unavailable" to "Análisis no disponible",
        "Analysis cancelled" to "Análisis cancelado",
        "Cloud analysis" to "Análisis en la nube",
        "Offline analysis" to "Análisis sin conexión",
        "Cloud" to "Nube",
        "On device" to "En el dispositivo",
        "Where analysis runs" to "Dónde se ejecuta el análisis",
        "You can change this anytime" to "Puedes cambiarlo en cualquier momento",
        "Cloud analysis is ready" to "El análisis en la nube está listo",
        "Offline analysis is ready" to "El análisis sin conexión está listo",
        "Connecting securely to Sunny AI Cloud." to "Conectando de forma segura con Sunny AI Cloud.",
        "Fast and requires no download. Photos are securely sent to Sunny for analysis." to "Es rápido y no requiere descarga. Las fotos se envían de forma segura a Sunny para analizarlas.",
        "Works without internet and keeps analysis on this phone." to "Funciona sin internet y mantiene el análisis en este teléfono.",
        "Download offline analysis" to "Descargar análisis sin conexión",
        "Download offline model" to "Descargar modelo sin conexión",
        "Download" to "Descargar",
        "Downloading model" to "Descargando modelo",
        "Verifying" to "Verificando",
        "Verify" to "Verificar",
        "Installed" to "Instalado",
        "Offline setup" to "Configuración sin conexión",
        "Analysis setup" to "Configuración del análisis",
        "Analysis options" to "Opciones de análisis",
        "Download using" to "Descargar mediante",
        "Wi-Fi only" to "Solo Wi‑Fi",
        "Wi-Fi + mobile" to "Wi‑Fi + datos móviles",
        "Mobile-data charges may apply. The download resumes if interrupted." to "Pueden aplicarse cargos de datos móviles. La descarga se reanuda si se interrumpe.",
        "Cloud analysis ready" to "Análisis en la nube listo",
        "Offline analysis installed" to "Análisis sin conexión instalado",
        "Download paused" to "Descarga pausada",
        "Retry download" to "Reintentar descarga",
        "Pro required" to "Se requiere Pro",
        "View Pro" to "Ver Pro",
        "Device vault" to "Bóveda del dispositivo",
        "Your protection settings at a glance" to "Tus ajustes de protección de un vistazo",
        "App lock" to "Bloqueo de la app",
        "App Lock (PIN)" to "Bloqueo de la app (PIN)",
        "PIN optional" to "PIN opcional",
        "PIN on" to "PIN activado",
        "Encrypted local" to "Cifrado local",
        "Privacy & Security" to "Privacidad y seguridad",
        "PRIVACY & SECURITY" to "PRIVACIDAD Y SEGURIDAD",
        "Your data" to "Tus datos",
        "Your control" to "Tu control",
        "Delete all local data" to "Eliminar todos los datos locales",
        "Delete all local data?" to "¿Eliminar todos los datos locales?",
        "Delete everything" to "Eliminar todo",
        "This cannot be undone." to "Esta acción no se puede deshacer.",
        "Local data deleted" to "Datos locales eliminados",
        "Your device vault is empty." to "La bóveda del dispositivo está vacía.",
        "Privacy Policy" to "Política de privacidad",
        "Medical Disclaimer" to "Aviso médico",
        "Help improve Sunny" to "Ayuda a mejorar Sunny",
        "Contribute to improving Sunny" to "Contribuir a mejorar Sunny",
        "I agree" to "Acepto",
        "Not now" to "Ahora no",
        "Enter Sunny" to "Entrar en Sunny",
        "Sunny is Locked" to "Sunny está bloqueado",
        "Your health data is protected. Authenticate to continue." to "Tus datos de salud están protegidos. Autentícate para continuar.",
        "Unlock with PIN" to "Desbloquear con PIN",
        "Enter your PIN" to "Introduce tu PIN",
        "Enter your 4-digit PIN to unlock Sunny." to "Introduce tu PIN de 4 dígitos para desbloquear Sunny.",
        "Incorrect PIN. Try again." to "PIN incorrecto. Inténtalo de nuevo.",
        "Create a PIN" to "Crear un PIN",
        "Choose a 4-digit PIN to protect your skin health data." to "Elige un PIN de 4 dígitos para proteger tus registros de piel.",
        "Confirm your PIN" to "Confirma tu PIN",
        "Re-enter your PIN to confirm." to "Vuelve a introducir tu PIN para confirmar.",
        "Change PIN" to "Cambiar PIN",
        "PINs didn't match. Start over." to "Los PIN no coinciden. Empieza de nuevo.",
        "Get started" to "Comenzar",
        "Build a clear baseline" to "Crea una referencia clara",
        "Follow the visual story" to "Sigue la historia visual",
        "A private visual journal for photographing and comparing visible skin changes over time." to "Un diario visual privado para fotografiar y comparar cambios visibles en la piel con el tiempo.",
        "Made for tracking, not diagnosis" to "Creado para seguimiento, no para diagnóstico",
        "Track visible skin changes over time" to "Registra cambios visibles en la piel con el tiempo",
        "Add your first photo" to "Añade tu primera foto",
        "Take your first photo to begin tracking." to "Toma tu primera foto para empezar el seguimiento.",
        "No saved areas yet" to "Aún no hay zonas guardadas",
        "No photos saved" to "No hay fotos guardadas",
        "No matching areas" to "No hay zonas coincidentes",
        "Search tracked areas" to "Buscar zonas registradas",
        "Recently updated" to "Actualizados recientemente",
        "Oldest updated" to "Actualizados hace más tiempo",
        "Sort saved scans" to "Ordenar escaneos guardados",
        "Clear search" to "Borrar búsqueda",
        "Select all" to "Seleccionar todo",
        "Clear all" to "Borrar selección",
        "Tip: long-press an area to select several." to "Consejo: mantén pulsada una zona para seleccionar varias.",
        "Scan photo" to "Foto del escaneo",
        "Photo history" to "Historial de fotos",
        "Compare over time" to "Comparar con el tiempo",
        "Edit tracked area" to "Editar zona registrada",
        "Delete this tracked area?" to "¿Eliminar esta zona registrada?",
        "Reminder interval" to "Intervalo del recordatorio",
        "Set interval" to "Definir intervalo",
        "Set Reminder" to "Crear recordatorio",
        "Remove reminder" to "Eliminar recordatorio",
        "Reminder Center" to "Centro de recordatorios",
        "No spot reminders scheduled." to "No hay recordatorios programados.",
        "Regular skin check" to "Revisión regular de la piel",
        "Hours" to "Horas",
        "Days" to "Días",
        "Weeks" to "Semanas",
        "Months" to "Meses",
        "Report" to "Informe",
        "Create report" to "Crear informe",
        "Generate Report" to "Generar informe",
        "Create clinician-ready reports" to "Crea informes para compartir con profesionales",
        "No reports yet" to "Aún no hay informes",
        "Reports you save will appear here." to "Los informes guardados aparecerán aquí.",
        "Report Preview" to "Vista previa del informe",
        "Generate report" to "Generar informe",
        "Generating report…" to "Generando informe…",
        "Report saved" to "Informe guardado",
        "Share report" to "Compartir informe",
        "Delete report" to "Eliminar informe",
        "Delete this report?" to "¿Eliminar este informe?",
        "Report unavailable" to "Informe no disponible",
        "Photo Check" to "Revisión fotográfica",
        "Full-body photo check" to "Revisión fotográfica de cuerpo completo",
        "Start photo check" to "Iniciar revisión fotográfica",
        "Continue photo check" to "Continuar revisión fotográfica",
        "Finish session" to "Finalizar sesión",
        "End session" to "Terminar sesión",
        "Photo check complete" to "Revisión fotográfica completada",
        "The ABCDE guide" to "La guía ABCDE",
        "ABCDE self-check" to "Autoevaluación ABCDE",
        "Educational checklist" to "Lista educativa",
        "Not sure" to "No estoy seguro",
        "See a professional for anything that concerns you." to "Consulta a un profesional ante cualquier preocupación.",
        "Create" to "Crear",
        "Discard" to "Descartar",
        "Filter:" to "Filtrar:",
        "changed" to "cambió",
        "Interval" to "Intervalo",
        "Password" to "Contraseña",
        "Aligning…" to "Alineando…",
        "Keep editing" to "Seguir editando",
        "Keep checking" to "Seguir revisando",
        "What's this?" to "¿Qué es esto?",
        "Body Coverage" to "Cobertura corporal",
        "Tracked Areas" to "Zonas registradas",
        "Analyse anyway" to "Analizar de todos modos",
        "Sunny Analysis" to "Análisis de Sunny",
        "Opening report…" to "Abriendo informe…",
        "Confirm password" to "Confirmar contraseña",
        "Encrypted backup" to "Copia cifrada",
        "Save measurement" to "Guardar medición",
        "Where a scan goes" to "Dónde va un escaneo",
        "Retake recommended" to "Se recomienda repetir la foto",
        "Showing raw photos" to "Mostrando fotos originales",
        "Sunny visual report" to "Informe visual de Sunny",
        "Filter by date range" to "Filtrar por intervalo de fechas",
        "Skin tracking report" to "Informe de seguimiento de piel",
        "Use offline analysis" to "Usar análisis sin conexión",
        "End this photo check?" to "¿Terminar esta revisión fotográfica?",
        "Remind me to re-check" to "Recordarme volver a revisar",
        "Contribute beta scans?" to "¿Contribuir escaneos beta?",
        "Passwords do not match" to "Las contraseñas no coinciden",
        "Save Report to Device?" to "¿Guardar el informe en el dispositivo?",
        "Upcoming spot reminders" to "Próximos recordatorios",
        "Discard unsaved changes?" to "¿Descartar cambios no guardados?",
        "Description needs refresh" to "La descripción debe actualizarse",
        "Reference-based estimates" to "Estimaciones basadas en referencia",
        "Use at least 10 characters" to "Usa al menos 10 caracteres",
        "Require a PIN to open Sunny" to "Requerir PIN para abrir Sunny",
        "Find a dermatologist near you" to "Buscar dermatólogo cerca de ti",
        "Manage or cancel subscription" to "Gestionar o cancelar suscripción",
        "Reason for sharing (optional)" to "Motivo para compartir (opcional)",
        "Reference width in millimetres" to "Ancho de referencia en milímetros",
        "A private, on-device PDF preview" to "Vista previa PDF privada en el dispositivo",
        "No tracked areas match these filters." to "Ninguna zona registrada coincide con estos filtros.",
        "Stored only with this encrypted scan." to "Guardado solo con este escaneo cifrado.",
        "Get a nudge to re-photograph this spot" to "Recibe un aviso para volver a fotografiar esta zona",
        "Remove every scan, reminder and report" to "Eliminar todos los escaneos, recordatorios e informes",
        "Photos could not be aligned confidently" to "No se pudieron alinear las fotos con confianza",
        "Keep Sunny open until analysis finishes." to "Mantén Sunny abierto hasta que termine el análisis.",
        "Offline analysis is installed and ready." to "El análisis sin conexión está instalado y listo.",
        "Drag the top photo to fine-tune the match." to "Arrastra la foto superior para ajustar la coincidencia.",
        "Export scans, notes, reminders and reports" to "Exportar escaneos, notas, recordatorios e informes",
        "Opens Maps — your photos stay on this phone" to "Abre Maps; tus fotos permanecen en este teléfono",
        "Decrypting and preparing pages on this device" to "Descifrando y preparando páginas en este dispositivo",
        "Create a baseline for one area you want to track" to "Crea una referencia para una zona que quieras seguir",
        "If enabled, selected photos are sent over HTTPS." to "Si se activa, las fotos seleccionadas se envían mediante HTTPS.",
        "Use a password you can remember. Sunny cannot recover it." to "Usa una contraseña que recuerdes. Sunny no puede recuperarla.",
        "Five things dermatologists teach people to notice on a mole." to "Cinco aspectos que los dermatólogos enseñan a observar en un lunar.",
        "Keep framing and lighting consistent for useful comparisons." to "Mantén el encuadre y la iluminación para obtener comparaciones útiles.",
        "Drag the divider — older photo on the left, newer photo on the right." to "Arrastra el divisor: foto anterior a la izquierda y reciente a la derecha.",
        "Changes here are a prompt to show a clinician — never a verdict." to "Estos cambios son una señal para consultar; nunca un veredicto.",
        "Take at least two photos of this spot to compare them over time." to "Toma al menos dos fotos de esta zona para compararlas con el tiempo.",
        "For consistent personal tracking only; not a clinical measurement." to "Solo para seguimiento personal; no es una medición clínica.",
        "The encrypted report will be permanently removed from this phone." to "El informe cifrado se eliminará permanentemente de este teléfono.",
        "Your edited name, photo or refreshed description will not be saved." to "No se guardarán el nombre, la foto ni la descripción actualizada.",
        "The photo remains private and unsaved until you retry or discard it." to "La foto seguirá privada y sin guardar hasta reintentar o descartarla.",
        "When enabled, you will need to enter your PIN each time you open Sunny." to "Al activarlo, deberás introducir tu PIN cada vez que abras Sunny.",
        "Cloud is selected. Installing offline analysis is optional for Pro users." to "La nube está seleccionada. Instalar el análisis sin conexión es opcional para usuarios Pro.",
        "Try again with a well-lit, close-up, filled-frame photo of a single spot." to "Inténtalo con una foto cercana, bien iluminada y que llene el encuadre.",
        "The checklist progress will be cleared. Your saved photos are not affected." to "Se borrará el progreso de la lista. Las fotos guardadas no se verán afectadas.",
        "A personal photo checklist. It does not confirm a complete skin examination." to "Una lista fotográfica personal. No confirma un examen completo de la piel.",
        "These estimates depend on marker placement and are not clinical measurements." to "Estas estimaciones dependen de los marcadores y no son mediciones clínicas.",
        "Use the cloud without a download, or install private offline analysis with Pro." to "Usa la nube sin descargar o instala el análisis privado sin conexión con Pro.",
        "Sunny will nudge you to re-photograph this spot so you can compare it over time." to "Sunny te recordará volver a fotografiar esta zona para compararla con el tiempo.",
        "Allow camera access to take a photo. You can still choose an existing photo from your library." to "Permite el acceso a la cámara para tomar una foto. También puedes elegir una foto de la galería.",
        "Cloud analysis does not download a model. Offline analysis stores 3.08 GB privately on this phone." to "El análisis en la nube no descarga un modelo. El análisis sin conexión almacena 3,08 GB de forma privada.",
        "Sunny is a skin tracking tool only. It does not provide medical diagnoses or advice. Always consult a qualified healthcare professional for any skin concerns." to "Sunny es solo una herramienta de seguimiento de la piel. No proporciona diagnósticos ni consejos médicos. Consulta siempre a un profesional sanitario ante cualquier preocupación.",
        "Scan photos are sent to Sunny AI Cloud for visual description." to "Las fotos del escaneo se envían a Sunny AI Cloud para obtener una descripción visual.",
        "Scan photos are processed and stored on this phone." to "Las fotos del escaneo se procesan y guardan en este teléfono.",
        "I understand Sunny does not diagnose, assess risk, or tell me when it is safe to wait. My scan photos will be sent to Sunny AI Cloud for processing." to "Entiendo que Sunny no diagnostica, no evalúa riesgos ni indica cuándo es seguro esperar. Mis fotos se enviarán a Sunny AI Cloud para procesarlas.",
        "I understand Sunny does not diagnose, assess risk, or tell me when it is safe to wait. I will seek professional care for concerns." to "Entiendo que Sunny no diagnostica, no evalúa riesgos ni indica cuándo es seguro esperar. Consultaré a un profesional ante cualquier preocupación.",
        "Analysing with Sunny AI Cloud." to "Analizando con Sunny AI Cloud.",
        "Running on-device. Your photo never leaves this phone." to "Ejecutándose en el dispositivo. Tu foto no sale de este teléfono.",
        "Saved data is encrypted on this device. Scan photos are sent to Sunny AI Cloud." to "Los datos guardados están cifrados en este dispositivo. Las fotos se envían a Sunny AI Cloud.",
        "Scans stay on this device unless you explicitly share an export." to "Los escaneos permanecen en este dispositivo salvo que compartas una exportación.",
        "Sunny AI Cloud is unavailable. Check your connection and try again." to "Sunny AI Cloud no está disponible. Comprueba la conexión e inténtalo de nuevo.",
        "The installed AI model could not start. Install it from Settings before scanning." to "El modelo de IA instalado no pudo iniciarse. Instálalo desde Ajustes antes de escanear.",
        "Language & region" to "Idioma y región",
        "App language" to "Idioma de la app",
        "Choose language" to "Elegir idioma",
        "Use device language" to "Usar el idioma del dispositivo",
        "English" to "Inglés",
        "Connecting…" to "Conectando…"
    )
}
