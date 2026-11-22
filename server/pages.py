"""Public pages for search engines: ayattla.com/muyassir/ (all tools) and /muyassir/t/<id> (one tool), plus sitemap.xml.
The tool list comes from site_tools.json, generated from the Android tool registry (gen: see deploy.md)."""
import html, json, os
from fastapi import APIRouter
from fastapi.responses import HTMLResponse, Response

router = APIRouter()
BASE = os.environ.get("MUYASSIR_BASE", "https://ayattla.com/muyassir").rstrip("/")
DATA = json.load(open(os.path.join(os.path.dirname(__file__), "site_tools.json"), encoding="utf-8"))
CATS = {c["id"]: c for c in DATA["categories"]}
TOOLS = {t["id"]: t for t in DATA["tools"]}
COLOR = {"files": "#3346D3", "images": "#7340D0", "study": "#0B7F7A", "saudi": "#23814A",
         "productivity": "#B0600A", "docs": "#C02A63", "calc": "#1F6FB8", "smart": "#A14522"}
e = html.escape

STEPS = {
    "files": ["افتح الأداة في تطبيق مُيسّر", "اختر الملف من جهازك أو من تطبيق الملفات", "اضبط الخيارات إن وجدت ثم ابدأ", "احفظ الناتج أو شاركه مباشرة"],
    "images": ["افتح الأداة في تطبيق مُيسّر", "اختر صورة أو أكثر من المعرض", "اضبط الإعدادات وشاهد المعاينة", "احفظ الصورة الجديدة أو شاركها"],
    "study": ["افتح الأداة في تطبيق مُيسّر", "اكتب البيانات أو اختر قالبًا جاهزًا", "راجع الناتج على الشاشة", "صدّره ملف PDF أو Word أو شاركه"],
    "saudi": ["افتح الحاسبة في تطبيق مُيسّر", "أدخل الأجر والتواريخ والبيانات المطلوبة", "اقرأ النتيجة وتفصيلها", "اعرض طريقة الحساب والمادة النظامية ومصدرها الرسمي"],
    "productivity": ["افتح الأداة في تطبيق مُيسّر", "أضف عناصرك وبياناتك", "نظّمها وعدّلها متى شئت", "تبقى محفوظة على جهازك"],
    "docs": ["افتح الأداة في تطبيق مُيسّر", "عبئ الحقول بالعربية", "شاهد المستند قبل حفظه", "صدّره PDF أو Word وشاركه"],
    "calc": ["افتح الحاسبة في تطبيق مُيسّر", "أدخل القيم", "تظهر النتيجة فورًا", "انسخ النتيجة أو أعد الحساب"],
    "smart": ["افتح الأداة في تطبيق مُيسّر", "أدخل النص أو اختر الملف", "ابدأ المعالجة", "انسخ الناتج أو احفظه"],
}
NOTE = {
    "saudi": "النتيجة تقديرية للاسترشاد، وتعرض الحاسبة المعادلة ورقم المادة والمصدر الرسمي وتاريخ آخر مراجعة. للقرار النهائي راجع الجهة المختصة.",
}

CSS = """*{box-sizing:border-box}body{margin:0;font-family:"IBM Plex Sans Arabic",system-ui,-apple-system,"Segoe UI",Tahoma,sans-serif;background:#F6F7FB;color:#141A2E;line-height:1.7}
a{color:inherit;text-decoration:none}main{max-width:1040px;margin:0 auto;padding:20px 16px 48px}
.top{display:flex;justify-content:space-between;align-items:center;padding:6px 0 18px}.brand{font-weight:700;font-size:20px;color:#3346D3}
.hero{text-align:center;padding:28px 8px 8px}.hero h1{font-size:clamp(28px,5vw,44px);line-height:1.25;margin:0 0 12px}
.hero p{color:#5B6178;font-size:18px;max-width:620px;margin:0 auto 18px}.soon{display:inline-block;background:#EEF0FD;color:#2836A8;border-radius:999px;padding:6px 16px;font-size:14px}
.search{display:block;width:100%;max-width:520px;margin:22px auto 4px;padding:14px 18px;border:1px solid #CBD0DE;border-radius:14px;font:inherit;font-size:16px;background:#fff}
.search:focus{outline:3px solid #B9C1F5;border-color:#3346D3}
section{margin-top:34px}h2{font-size:22px;margin:0 0 4px;text-align:center}.blurb{color:#5B6178;text-align:center;margin:0 0 14px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:10px}
.tool{background:#fff;border:1px solid #E3E6EF;border-radius:14px;padding:14px 16px;text-align:center;border-top:3px solid var(--c)}
.tool:hover,.tool:focus-visible{border-color:var(--c);outline:none}.tool b{display:block;font-size:16px}.tool span{color:#5B6178;font-size:14px}
.card{background:#fff;border:1px solid #E3E6EF;border-radius:16px;padding:20px;margin-top:14px}
.crumbs{font-size:14px;color:#5B6178;text-align:center}.crumbs a{color:#3346D3}
ol{padding-inline-start:22px;margin:6px 0}li{margin:4px 0}.note{background:#FFF7E8;border-color:#F1DDB5}
.privacy{border-inline-start:4px solid #23814A}footer{text-align:center;color:#5B6178;font-size:14px;margin-top:40px}
@media (prefers-color-scheme:dark){body{background:#0F1320;color:#E8EBF5}.tool,.card,.search{background:#171C2C;border-color:#2A3147;color:#E8EBF5}.tool{border-top-color:var(--c)}
.hero p,.blurb,.tool span,.crumbs,footer{color:#A6ADC4}.soon{background:#1E2552;color:#C3CBFF}.note{background:#2A2414;border-color:#4A3E1E}}"""


def page(title: str, desc: str, path: str, body: str, ld: list) -> str:
    url = BASE + path
    lds = "".join(f'<script type="application/ld+json">{json.dumps(x, ensure_ascii=False)}</script>' for x in ld)
    return (f'<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
            f'<title>{e(title)}</title><meta name="description" content="{e(desc)}"><link rel="canonical" href="{url}">'
            f'<meta property="og:type" content="website"><meta property="og:title" content="{e(title)}"><meta property="og:description" content="{e(desc)}">'
            f'<meta property="og:url" content="{url}"><meta property="og:locale" content="ar_SA"><meta name="theme-color" content="#3346D3">'
            f'<style>{CSS}</style>{lds}</head><body><main><div class="top"><a class="brand" href="{BASE}/">مُيسّر</a>'
            f'<a href="https://ayattla.com/" style="font-size:14px;color:#5B6178">من آيات تتلى</a></div>{body}'
            f'<footer>مُيسّر: أدوات عربية تعمل على جهازك، ولا تُرفع ملفاتك إلى أي خادم.</footer></main></body></html>')


def tile(t: dict) -> str:
    c = COLOR.get(t["cats"][0], "#3346D3")
    return (f'<a class="tool" style="--c:{c}" href="{BASE}/t/{t["id"]}" data-k="{e(t["title"] + " " + t["sub"] + " " + t.get("keys", ""))}">'
            f'<b>{e(t["title"])}</b><span>{e(t["sub"])}</span></a>')


@router.get("/", response_class=HTMLResponse)
def home():
    secs = []
    for c in DATA["categories"]:
        ts = [t for t in DATA["tools"] if t["cats"][0] == c["id"]]
        if ts:
            secs.append(f'<section data-sec><h2>{e(c["title"])}</h2><p class="blurb">{e(c["blurb"])}</p><div class="grid">{"".join(tile(t) for t in ts)}</div></section>')
    n = len(DATA["tools"])
    body = (f'<div class="hero"><h1>{n} أداة عربية في تطبيق واحد</h1>'
            '<p>حوّل ملفات PDF وWord، وعدّل الصور، وأنشئ الاختبارات وأوراق العمل، واحسب الراتب ونهاية الخدمة والضريبة. كل ذلك على جهازك وبالعربية.</p>'
            '<span class="soon">التطبيق قريبًا على Google Play وApp Store</span>'
            '<input class="search" type="search" placeholder="ابحث عن أداة، مثل: ضغط PDF" aria-label="ابحث عن أداة" oninput="f(this.value)"></div>'
            + "".join(secs) +
            '<script>function f(q){q=q.trim().toLowerCase();document.querySelectorAll(".tool").forEach(function(a){a.hidden=q&&a.dataset.k.toLowerCase().indexOf(q)<0});'
            'document.querySelectorAll("[data-sec]").forEach(function(s){s.hidden=!s.querySelector(".tool:not([hidden])")})}</script>')
    ld = [{"@context": "https://schema.org", "@type": "SoftwareApplication", "name": "مُيسّر", "applicationCategory": "UtilitiesApplication",
           "operatingSystem": "Android, iOS", "inLanguage": "ar", "url": BASE + "/", "description": f"{n} أداة عربية لتحويل الملفات والصور والتعليم والخدمات السعودية، تعمل على الجهاز.",
           "offers": {"@type": "Offer", "price": "0", "priceCurrency": "SAR"}}]
    return page("مُيسّر: أدوات PDF والصور والمعلم والحاسبات السعودية بالعربية", f"{n} أداة عربية في تطبيق واحد: تحويل PDF وWord، الصور، الاختبارات وأوراق العمل، الراتب ونهاية الخدمة والضريبة. تعمل على جهازك.", "/", body, ld)


@router.get("/t/{tid}", response_class=HTMLResponse)
def tool(tid: str):
    t = TOOLS.get(tid)
    if not t:
        return HTMLResponse(page("الأداة غير موجودة", "", "/", f'<div class="card" style="text-align:center"><h1>الأداة غير موجودة</h1><p><a href="{BASE}/" style="color:#3346D3">كل أدوات مُيسّر</a></p></div>', []), 404)
    c = CATS[t["cats"][0]]
    steps = "".join(f"<li>{e(s)}</li>" for s in STEPS.get(c["id"], STEPS["smart"]))
    related = [x for x in DATA["tools"] if x["id"] != tid and c["id"] in x["cats"]][:6]
    note = f'<div class="card note"><b>تنبيه</b><p style="margin:4px 0 0">{e(NOTE[c["id"]])}</p></div>' if c["id"] in NOTE else ""
    body = (f'<p class="crumbs"><a href="{BASE}/">مُيسّر</a> / {e(c["title"])}</p>'
            f'<div class="hero" style="padding-top:8px"><h1>{e(t["title"])}</h1><p>{e(t["sub"])}، بالعربية وعلى جهازك، ضمن قسم {e(c["title"])} في تطبيق مُيسّر.</p>'
            '<span class="soon">التطبيق قريبًا على Google Play وApp Store</span></div>'
            f'<div class="card"><h2 style="text-align:start">طريقة الاستخدام</h2><ol>{steps}</ol></div>{note}'
            '<div class="card privacy"><b>خصوصيتك</b><p style="margin:4px 0 0">تعمل الأداة على جهازك، ولا تُرفع ملفاتك أو بياناتك إلى أي خادم. لا حاجة إلى حساب.</p></div>'
            + (f'<section><h2>أدوات قريبة</h2><p class="blurb">{e(c["blurb"])}</p><div class="grid">{"".join(tile(x) for x in related)}</div></section>' if related else ""))
    ld = [{"@context": "https://schema.org", "@type": "SoftwareApplication", "name": f'{t["title"]} في مُيسّر', "applicationCategory": "UtilitiesApplication",
           "operatingSystem": "Android, iOS", "inLanguage": "ar", "url": f'{BASE}/t/{tid}', "description": t["sub"], "offers": {"@type": "Offer", "price": "0", "priceCurrency": "SAR"}},
          {"@context": "https://schema.org", "@type": "BreadcrumbList", "itemListElement": [
              {"@type": "ListItem", "position": 1, "name": "مُيسّر", "item": BASE + "/"},
              {"@type": "ListItem", "position": 2, "name": t["title"], "item": f'{BASE}/t/{tid}'}]}]
    return page(f'{t["title"]} بالعربية | مُيسّر', f'{t["title"]}: {t["sub"]}. أداة عربية مجانية تعمل على جهازك دون رفع ملفاتك.', f"/t/{tid}", body, ld)


@router.get("/sitemap.xml")
def sitemap():
    urls = [BASE + "/"] + [f"{BASE}/t/{t['id']}" for t in DATA["tools"]]
    xml = '<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">' + "".join(f"<url><loc>{u}</loc></url>" for u in urls) + "</urlset>"
    return Response(xml, media_type="application/xml")
