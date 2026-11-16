# زهرة SHEIN · موقع حساب السلال

الزبون يلصق رابط سلة شي ان، وهاتف القارئ (تطبيق Zahra shein على سامسونج) يقرأ السلة في الخلفية، والموقع يحسب كل قطعة والمجموع بالدينار العراقي.

- `public/index.html` صفحة الزبون.
- `public/admin.html` صفحة التحكم (`/admin`): سعر الدولار، نسبة العربون، إيقاف الخدمة، رمز ربط الهاتف، ملفات Firebase.
- `src/index.js` الخادم على Cloudflare Workers، والبيانات في D1.

## الحساب
- سعر القطعة = سعرها في السلة المشتركة بالدولار × سعر الدولار.
- المجموع يُقرَّب للأعلى لأقرب 250 دينار. العربون = نسبة من المجموع، مقرّبة للأعلى لأقرب 250.
- الشحن غير محسوب، والقطع النافدة محسوبة.

الموقع منشور على: https://zahra-shein.zahrashein.workers.dev (صفحة التحكم: `/admin`).

## النشر
الصفحات في `public/` تُجمع داخل `src/static.js` (`npm run build`)، فيخدمها الخادم بنفسه دون رفع ملفات منفصل.
```
npm install
npx wrangler d1 create zahra          # مرة واحدة؛ ضع database_id الناتج في wrangler.toml
npx wrangler d1 execute zahra --remote --file=schema.sql
npm run deploy
```
ثم افتح `/admin`، اختر كلمة السر، وارفع ملفي Firebase، وانسخ رمز الربط إلى التطبيق.

## Firebase
1. console.firebase.google.com ← مشروع جديد.
2. أضف تطبيق Android باسم الحزمة `com.rafelo.picks` ونزّل `google-services.json`.
3. إعدادات المشروع ← حسابات الخدمة ← إنشاء مفتاح خاص جديد (ملف JSON).
4. ارفع الملفين في صفحة التحكم.

## تجربة محلية
`.dev.vars` فيه `FAKE_PUSH=1` لتجاوز Firebase:
```
npx wrangler d1 execute zahra --local --file=schema.sql
npx wrangler dev
```
