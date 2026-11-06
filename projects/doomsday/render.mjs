// node render.mjs preview <dir> t1 t2 ... | node render.mjs video <out.mp4>
import { createRequire } from 'module';
import { spawn } from 'child_process';
import { pathToFileURL } from 'url';
import fs from 'fs';
const { chromium } = createRequire(import.meta.url)('playwright');
const [mode, out, ...ts] = process.argv.slice(2);
const FPS = 30, DUR = 60, F0 = +(process.env.F0 || 0);
const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: 1080, height: 1920 } });
p.on('pageerror', e => console.error('PAGEERR', e.message));
await p.goto(pathToFileURL('film.html').href);
await p.evaluate(() => window.ready);
const grab = async T => { await p.evaluate(T => render(T), T); return p.screenshot({ type: 'jpeg', quality: 92 }); };
if (mode === 'preview') { fs.mkdirSync(out, { recursive: true }); for (const T of ts) fs.writeFileSync(`${out}/p${T}.jpg`, await grab(+T)); }
else {
  fs.writeFileSync('impacts.json', JSON.stringify(await p.evaluate(() => IMPACTS)));
  const ff = spawn('ffmpeg', ['-v', 'error', '-y', '-f', 'image2pipe', '-framerate', String(FPS), '-c:v', 'mjpeg', '-i', '-', '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p', out], { stdio: ['pipe', 'inherit', 'inherit'] });
  for (let f = F0; f < FPS * DUR; f++) { const buf = await grab(f / FPS); if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r)); if (f % 300 === 0) console.log('frame', f, new Date().toISOString()); }
  ff.stdin.end(); await new Promise(r => ff.on('close', r));
}
await b.close();
