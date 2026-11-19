# Grafika GeoKuku

Zdrojové vektory loga, ikony programu a úvodního obrázku a skripty, které z nich vyrobí soubory pro program.

## Co kde je
| soubor | co to je |
|---|---|
| `zdroje/logo-zelene.svg`, `zdroje/logo-bile.svg` | logo GeoKuk (obrysy, jedna cesta), zelené a bílé pro tmavý podklad |
| `zdroje/logo-stredove-cary.svg` | totéž logo jako středové čáry písmen s tahem (pro úpravy tvaru) |
| `zdroje/ikona-16.svg` … `ikona-256.svg` | ikona programu, pro každou velikost zvlášť vyladěná na mřížku pixelů |
| `zdroje/ikona-256-upravitelna.svg` | ikona 256 px s pojmenovanými částmi (`dlazdice`, skupina `oko-a-rasy`: `rasy`, `oko`, `panenka`, `odlesk`) |
| `zdroje/splash.svg` | úvodní obrázek při startu (400 × 400) |
| `generator/` | skripty pro vygenerování obrázků |

Barvy: zelená loga `#239C2B`. Ikona má tmavou dlaždici (`#3B4045` → `#1E2124`) a oko s přechodem zelená → žlutá → oranžová (`#5BD13A`, `#C8E62A`, `#FFB400`), řasy `#FFD21F` → `#FFB400` a panenku `#7BE35A` → `#239C2B`. Úvodní obrázek má tmavé pozadí (`#3A3F44` → `#1E2124`) a logo s přechodem `#4CD13A`, `#B9E52A`, `#FFB400`.

## Vygenerování
Potřeba: Node.js s balíčkem `playwright` a prohlížečem Chromium (`npm install playwright && npx playwright install chromium`), Python 3 s balíčky `Pillow` a `numpy`.

```sh
grafika/generator/sestav.sh
```

Do `grafika/vystup/` vzniknou:
| soubor | patří do |
|---|---|
| `geokuk16.png`, `geokuk32.png`, `geokuk48.png`, `geokuk64.png`, `geokuk128.png`, `geokuk256.png` | `src/main/resources/img/` (ikona okna) |
| `geokuk24.png` | rezerva, program ji nepoužívá |
| `geokuk.ico` | `distribuce/windows/` (7 velikostí 16–256, 32 bitů s průhledností) |
| `geokuk-logo.png` | `src/main/resources/img/` (logo při načítání mapy, 400 × 400) |
| `geokuk400.jpg` | `src/main/resources/img/` (úvodní obrázek) |

Kontrola proti souborům v repozitáři: `python3 -I grafika/generator/porovnej.py .` (z kořene repozitáře). Se stejnou verzí Chromia a Pillow vyjdou soubory bajtově stejně, jinak se porovnají po pixelech.

## Úpravy
SVG jsou ploché a bez transformací, dají se upravit v Inkscape. Po úpravě uložte jako Prostý SVG a spusťte `sestav.sh`. U loga se generuje z `logo-zelene.svg`. Po úpravě `logo-stredove-cary.svg` je potřeba převést tahy na obrysy (Cesta → Tah na cestu, sjednotit) a uložit jako `logo-zelene.svg` / `logo-bile.svg`.

## Původ a licence
Logo GeoKuk: původní motiv © Martin Veverka a spoluautoři; překreslená vektorová podoba GeoKuk community, 2026. Podklad návrhu vznikl pomocí ChatGPT (OpenAI); vektorové zpracování a úpravy proběhly s pomocí AI asistenta (Claude, Anthropic).

Licence: GNU General Public License v3.0 (GPL-3.0), stejně jako zbytek projektu. Zdrojové soubory a generátor jsou ve složkách `zdroje/` a `generator/`, aby je mohl použít a upravit kdokoli.
