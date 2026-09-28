# Změny

## 6.0.0

Navazuje na verzi 5.1.1c Jiřího Bílka.

### Opravy
- Oddálení mapy na nejmenší měřítko už nevyhazuje chybové okno.
- Mapa při přibližování ke kurzoru neskáče jinam, ani na širokých
  monitorech.
- Mapu nejde oddálit víc, než kolik program spolehlivě zvládne.
- Mřížky se nezacyklí při přetočení mapy přes okraj světa.
- Program spuštěný z jaru pozná svou složku, takže funguje nastavení
  uložené u programu a cesty relativní k programu.

### Změny
- Ve Windows Geokuk novou verzi stáhne a spouštěč `geokuk.cmd` ji při
  dalším spuštění nainstaluje. Předchozí verze zůstane jako
  `geokuk.jar.bak`.
- Kontrola nové verze a stažení míří na GitHub Releases tohoto repa,
  „Zadat problém“ a webová stránka programu na jeho GitHub.
- V dialogu o nové verzi tlačítko „Připomenout za měsíc“ odloží další
  automatickou kontrolu o 30 dní.
- Ve zprávách uživatelům přibylo tlačítko „Označit vše jako přečtené“.
- Nová instalace má vypnuté datové složky GeoGet a GSAK, popisky keší
  a mřížky Dd a DdMmMmm. Uložené nastavení se nemění.

### Vývoj
- Build na JDK 21 přes Maven Wrapper, CI na GitHub Actions.
- Release obsahuje `geokuk.jar`, `geokuk.jar.sha256` a `geokuk.cmd`.
- Odstraněn build přes Ant a NetBeans a zdroj webu geokuk.cz.
