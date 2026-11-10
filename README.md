# VIP X Ultimate

مشروع Android مستقل باستخدام Kotlin وJetpack Compose، بواجهة عربية RTL ومعرّف الحزمة com.vipx.ultimate.

## النواة الحالية
- عرض التطبيقات التي تظهر في مشغّل الهاتف.
- البحث بالاسم أو اسم الحزمة.
- عرض الاسم واسم الحزمة والإصدار.
- فتح التطبيق المختار.
- بناء APK تجريبي عبر GitHub Actions.

## الحدود
هذه نواة تأسيسية وليست النسخة النهائية. لا تدّعي تعديل التطبيقات المثبتة أو قراءة بياناتها الخاصة أو تجاوز اشتراكاتها. لا يمكن استعادة المصدر الأصلي من APK مثبت.

## البناء
gradle :app:assembleDebug

الناتج: app/build/outputs/apk/debug/app-debug.apk


## Cloud AI assistant (v1.2.0)
The Android app now includes an Arabic cloud-chat screen. To use live AI, deploy `cloud/worker.js` and configure the server-side secrets described in `cloud/README.md`. The APK does not contain provider credentials; the cloud service is not live until you deploy it.
