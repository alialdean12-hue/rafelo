# studio-kit
- `AGENT_INSTRUCTIONS_v2.md` : الصقه في تعليمات الـ Agent (9 أساليب).
- `styles/<style>/` : قوالب جاهزة تعمل كما هي (انسخها ثم عدّل النص والتوقيت).
- `scripts/` : tts_edge.py (صوت) · sfx_lib.py (مؤثرات) · mix.py (دمج) · pixabay.py (لقطات) · render.sh (إخراج).
- `anime/` : مولّد الأنمي ومؤثراته.
ضع المجلد في `~/my-films/studio-kit/`. يحتاج: node 22+، ffmpeg، pip install edge-tts numpy.
قالب vox-real يحتاج لقطة (assets/v1c.mp4) وصورة (img/p1.jpg): ولّدهما بـ pixabay.py.
