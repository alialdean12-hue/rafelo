# image-tool

FLUX.1 [schnell] through Cloudflare Workers AI, served on `http://localhost:8000` (started by a SessionStart hook).

No key lives in this repo: the session proxy injects the Cloudflare token saved in the environment's network secrets for `api.cloudflare.com`.

    curl -o out.jpg "http://localhost:8000/generate?prompt=a+red+cat&width=768&height=1344"

Prompts in English. Text inside images comes out garbled; add text afterwards.
