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
The Android app includes an Arabic cloud-chat screen. The included Worker is pinned to Gemini's free-tier model only; deploy `cloud/worker.js` and configure the secrets described in `cloud/README.md`. It never falls back to a paid provider. The cloud service is not live until you deploy it.

## النواة المحلية المجانية
- يمكن اختيار تطبيق مثبت وتحليل APK الأساسي وملفات split محلياً على الهاتف.
- يتضمن التحليل بصمة SHA-256، بيانات الإصدار وSDK، بنية DEX والمكتبات والموارد، الصلاحيات، وبصمة شهادة التوقيع التي يعرضها Android.
- أربعة محللات محلية تقدم تقارير بنيوية وأمنية أولية وتصميمية/موارد وهندسية بقواعد صريحة.
- تُحفظ آخر نتيجة داخل مساحة التطبيق الخاصة ويمكن فتحها لاحقاً.
- مسار التحليل المحلي لا يحتاج إنترنت أو رمز وصول ولا يرفع APK إلى خادم.

**تنبيه دقة:** المحللات المحلية الحالية قواعد تحليلية وليست نموذج ذكاء اصطناعي توليدياً. الذكاء السحابي القديم اختياري فقط، ويتطلب خادماً ومزوداً خارجياً وقد يخضع لحصص أو رسوم. لا توجد خدمة سحابية مطلوبة لتشغيل المختبر المحلي.

التفاصيل ومعايير القبول: [docs/LOCAL_FIRST_FREE_CORE.md](docs/LOCAL_FIRST_FREE_CORE.md)
