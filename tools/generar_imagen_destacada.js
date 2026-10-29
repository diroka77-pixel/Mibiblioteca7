// Genera la imagen destacada de Google Play (1024x500) para cada idioma de la ficha.
// Uso: node tools/generar_imagen_destacada.js   (necesita Playwright con Chromium)
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '..');
const base = path.join(root, 'fastlane/metadata/android');
const lemas = {
  'es-ES': ['Tu biblioteca y tu lector,', 'en un solo rincón'],
  'en-US': ['Your library and your reader,', 'in one cozy nook'],
  'pt-BR': ['Sua biblioteca e seu leitor,', 'num só cantinho'],
  'fr-FR': ['Votre bibliothèque et votre liseuse,', 'dans un même coin'],
  'de-DE': ['Deine Bibliothek und dein Reader,', 'an einem gemütlichen Ort'],
  'it-IT': ['La tua biblioteca e il tuo lettore,', 'in un unico angolo'],
};
(async () => {
  const icon = fs.readFileSync(path.join(base, 'es-ES/images/icon.png')).toString('base64');
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1024, height: 500 } });
  for (const [locale, [l1, l2]] of Object.entries(lemas)) {
    await page.setContent(`<!doctype html><html><body style="margin:0">
      <div style="width:1024px;height:500px;display:flex;align-items:center;gap:56px;padding:0 84px;box-sizing:border-box;
        background:radial-gradient(circle at 22% 50%, #4a3a2c 0%, #2b2019 55%, #1d1612 100%);font-family:Georgia,'Times New Roman',serif">
        <img src="data:image/png;base64,${icon}" style="width:300px;height:300px;border-radius:64px;box-shadow:0 18px 50px rgba(0,0,0,.45)">
        <div style="color:#f6ead6">
          <div style="font-size:84px;font-weight:bold;letter-spacing:.5px;line-height:1">Bookhollow</div>
          <div style="width:96px;height:4px;background:#d9a35b;border-radius:2px;margin:26px 0 22px"></div>
          <div style="font-size:31px;line-height:1.35;color:#ead7bb">${l1}<br>${l2}</div>
        </div>
      </div></body></html>`);
    const out = path.join(base, locale, 'images/featureGraphic.png');
    await page.screenshot({ path: out });
    console.log('OK', out);
  }
  await browser.close();
})();
