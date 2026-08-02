# AutoBanRobot keeps its small WebView bridge names for JavaScript reflection.
-keepclassmembers class com.autobanrobot.mobile.** {
    @android.webkit.JavascriptInterface <methods>;
}
