// node render.mjs preview <outdir> | node render.mjs video <out.mp4>
import { createRequire } from 'module';
const { chromium } = createRequire(import.meta.url)('playwright');
import { spawn } from 'child_process';
import { pathToFileURL } from 'url';
import path from 'path';
const [mode, out] = process.argv.slice(2);
const FPS = 30, DUR = 60;
const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: 1080, height: 1920 } });
p.on('pageerror', e => console.error('PAGEERR', e.message));
await p.goto(pathToFileURL(path.resolve('scenes.html')).href);
await p.evaluate(() => window.ready);
const grab = async T => Buffer.from((await p.evaluate(T => { render(T); return document.getElementById('c').toDataURL('image/jpeg', 0.93); }, T)).split(',')[1], 'base64');
if (mode === 'preview') {
  const fs = await import('fs'); fs.mkdirSync(out, { recursive: true });
  for (const T of process.argv.slice(4).map(Number)) fs.writeFileSync(`${out}/f${T}.jpg`, await grab(T));
} else {
  const ff = spawn('ffmpeg', ['-v', 'error', '-y', '-f', 'image2pipe', '-framerate', String(FPS), '-c:v', 'mjpeg', '-i', '-', '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p', out], { stdio: ['pipe', 'inherit', 'inherit'] });
  for (let f = 0; f < FPS * DUR; f++) { const buf = await grab(f / FPS); if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r)); if (f % 300 === 0) console.log('frame', f); }
  ff.stdin.end(); await new Promise(r => ff.on('close', r));
}
await b.close();
