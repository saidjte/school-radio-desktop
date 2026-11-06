# تراخيص الخطوط المدمجة (v0.24.0)

كل الخطوط مدمجة محليًا 100% ولا يحمّل التطبيق أي مورد من الشبكة.

| العائلة | الملفات | المصدر الرسمي | الترخيص |
| --- | --- | --- | --- |
| Cairo | Cairo-{Regular,Medium,SemiBold,Bold}.ttf | google/fonts (ofl/cairo)، أوزان مُجسَّدة من المتغير Cairo[slnt,wght] | SIL Open Font License 1.1 |
| Changa | Changa-{Medium,SemiBold,Bold,ExtraBold}.ttf | google/fonts (ofl/changa)، أوزان مُجسَّدة من المتغير Changa[wght] | SIL Open Font License 1.1 |
| Orbitron | Orbitron-{Medium,SemiBold,Bold,ExtraBold}.ttf | google/fonts (ofl/orbitron)، أوزان مُجسَّدة من المتغير Orbitron[wght] | SIL Open Font License 1.1 |
| Spicy Sale | SpicySale-Regular.otf | إصدار Khurasan™ (Syafrizal) المجاني العام، نسخة cdnfonts المرآة الرسمية | Freeware — مجاني للاستعمال الشخصي والتجاري وفق ما يعلنه مصممه |

نص ترخيص OFL 1.1 الكامل متاح لدى https://openfontlicense.org — لا نعدّل تصميم أي
خط (التجسيد الثابت للأوزان عملية تحضير تقنية لا تعديل رسومي) ولا نبيع أيًا منها كخط مستقل.

## تصدير PDF (v0.30.0)

| العنصر | التفاصيل | الترخيص |
| --- | --- | --- |
| خط ملفات PDF | عائلة Cairo المدمجة أعلاه نفسها (Regular/SemiBold/Bold) — عربي ولاتيني وكل علامات الترقيم | SIL Open Font License 1.1 |
| Apache PDFBox | org.apache.pdfbox:pdfbox:2.0.31 — توليد وثيقة PDF (صفحات A4 وForm XObject وبيانات وصفية) | Apache License 2.0 |
| pdfbox-graphics2d | de.rototor.pdfbox:graphics2d:0.47 — جسر Graphics2D يرسم التشكيل العربي أشكالًا متجهة داخل PDF | Apache License 2.0 |

التصدير محلي 100% دون اتصال: الحروف تُرسم أشكالًا متجهة عبر تشكيل AWT/HarfBuzz فلا
تُضمَّن الخطوط داخل ملف PDF ولا تخرج أعطال خطوط على أي نظام تشغيل، واختيار Cairo
يضمن تغطية كاملة لمحارف القوالب العربية واللاتينية معًا (يتحقق منه اختبار
pdfUnsafeChars حيًّا من الخط المدمج).

## مكتبات استيراد اليوتيوب (v0.25.0)

ميزة استيراد المقاطع من اليوتيوب تعتمد على مكوّنات محلية نقية، كلها تعمل داخل JVM
بلا أي أدوات نظام خارجية:

| المكوّن | الإحداثيات | الدور | الترخيص |
| --- | --- | --- | --- |
| yt-dlp | binaries/yt-dlp/{windows,linux,macos} (النسخة 2026.10.26) | جلب البيانات وتحميل الصوت — أداة مدمجة لكل منصة في نسختها | The Unlicense (ملك عام) |
| JAAD | de.sfuhrm:jaad:0.8.7 | فكّ AAC-LC من كبسولة M4A/MP4 إلى PCM | Public Domain |
| jump3r | de.sciss:jump3r:1.0.5 | ترميز MP3 نقي (منفذ مكتبة LAME) | LGPL v2.1+ (تُستخدم مكتبة دون تعديل) |

ملاحظة LGPL: المكتبة مرتبطة ديناميكيًا عبر Maven دون أي تعديل عليها، ويبقى الإشعار
الرسمي باسمها في هذا الملف — وهو الاستخدام المصرّح به لبرمجيات تُوزَّع بأي رخصة.

ملف selftest/test_tone.m4a (49KB) نغمة صناعية مولّدة بـffmpeg لأغراض الفحص الذاتي فقط.
