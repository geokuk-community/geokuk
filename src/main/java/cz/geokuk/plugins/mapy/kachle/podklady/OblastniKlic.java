package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.util.MercatorProjection;

import cz.geokuk.plugins.mapy.kachle.data.KaLoc;
import cz.geokuk.plugins.mapy.sprava.PolygonPokryti;

/**
 * Typ dlaždice offline mapy v cache: globální část (téma, měřítko, písmo, verze vykreslování) a oblastní část z otisků jen těch souborů map, vrstev a DEM,
 * které dlaždici pokrývají. Změna souboru tak zneplatní jen dlaždice v jeho oblasti.
 */
final class OblastniKlic {

	/** Vypnutí oblastní části: všechny soubory pokrývají každou dlaždici, klíč se chová jako jeden otisk celé sestavy. */
	static boolean oblastni = true;

	/** Kolik dlaždic kolem se bere do oblasti: popisky se berou z okolí 3×3 a stínování terénu sousedí přes švy DEM. */
	static final int OKOLI = 1;

	/**
	 * Jak daleko za obrysem mapy z katalogu mohou ještě sahat data souboru, ve stupních (naměřeno až asi 0,10°).
	 */
	static final double BUFFER_OBRYSU = 0.15;

	private static final BoundingBox SVET = new BoundingBox(-90, -180, 90, 180);

	/** Soubor, jeho rozsah a otisk. */
	static final class Zdroj {
		private final BoundingBox rozsah;
		private final int otisk;
		/** Obrys z katalogu s bufferem uvnitř obdélníku souboru; null = platí celý obdélník. */
		private final ObrysRaster obrys;

		Zdroj(final BoundingBox rozsah, final String otisk) {
			this(rozsah, otisk, null);
		}

		Zdroj(final BoundingBox rozsah, final String otisk, final PolygonPokryti obrys) {
			this.rozsah = rozsah;
			this.otisk = (int) crc(otisk);
			this.obrys = obrys == null ? null : new ObrysRaster(obrys, BUFFER_OBRYSU);
		}
	}

	private final String globalni;
	private final List<Zdroj> zdroje;
	private final String sestava;

	/**
	 * @param zdroje
	 *            v pořadí, v jakém se kreslí (priorita při překryvu map)
	 */
	OblastniKlic(final String globalni, final List<Zdroj> zdroje) {
		this.globalni = globalni;
		this.zdroje = Collections.unmodifiableList(new ArrayList<>(zdroje));
		final CRC32 crc = new CRC32();
		for (final Zdroj z : this.zdroje) {
			pridej(crc, z.otisk);
		}
		sestava = String.format("%08x", crc.getValue());
	}

	/** Klíč pro mapy bez souborů: oblastní část je prázdná. */
	static OblastniKlic bezSouboru(final String globalni) {
		return new OblastniKlic(globalni, Collections.emptyList());
	}

	/** Globální část klíče, tj. klíč tématu a měřítka bez souborů. */
	String getGlobalni() {
		return globalni;
	}

	/** Otisk všech souborů sestavy, mění se při jakékoli změně některého z nich. */
	String getSestava() {
		return sestava;
	}

	/** Typ dlaždice v cache; mění se jen když se změní soubor, který dlaždici pokrývá, nebo globální část. */
	String klic(final KaLoc loc) {
		final BoundingBox oblast = oblast(loc);
		final CRC32 crc = new CRC32();
		for (final Zdroj z : zdroje) {
			if (!oblastni || z.rozsah.intersects(oblast) && (z.obrys == null || z.obrys.zasahuje(oblast))) {
				pridej(crc, z.otisk);
			}
		}
		return globalni + '.' + String.format("%08x", crc.getValue());
	}

	/** Rozsah dlaždice s okolím, ze kterého se kreslí. */
	static BoundingBox oblast(final KaLoc loc) {
		final byte zoom = (byte) loc.getMoumer();
		final long pocet = 1L << zoom;
		final long x0 = Math.max(0, loc.getFromSzUnsignedX() - OKOLI);
		final long x1 = Math.min(pocet, loc.getFromSzUnsignedX() + 1 + OKOLI);
		final long y0 = Math.max(0, loc.getFromSzUnsignedY() - OKOLI);
		final long y1 = Math.min(pocet, loc.getFromSzUnsignedY() + 1 + OKOLI);
		final double zapad = MercatorProjection.tileXToLongitude(x0, zoom);
		final double vychod = MercatorProjection.tileXToLongitude(x1, zoom);
		final double sever = MercatorProjection.tileYToLatitude(y0, zoom);
		final double jih = MercatorProjection.tileYToLatitude(y1, zoom);
		return new BoundingBox(Math.min(jih, sever), Math.min(zapad, vychod), Math.max(jih, sever), Math.max(zapad, vychod));
	}

	/** Jeden soubor map nebo vrstev. */
	static Zdroj soubor(final String druh, final File f, final BoundingBox rozsah, final long mapDate) {
		return soubor(druh, f, rozsah, mapDate, null);
	}

	/**
	 * @param obrys
	 *            obrys katalogové mapy nebo vrstvy; dlaždice mimo obrys a jeho buffer soubor nezneplatní, i když leží v obdélníku souboru. Null u souboru uživatele.
	 */
	static Zdroj soubor(final String druh, final File f, final BoundingBox rozsah, final long mapDate, final PolygonPokryti obrys) {
		return new Zdroj(rozsah, druh + ':' + f.getName() + ':' + f.length() + ':' + f.lastModified() + ':' + mapDate, obrys);
	}

	/**
	 * Rozsah dlaždice DEM podle jména (N50E014): buňka 1° × 1° od jihozápadního rohu; null, když jméno není ve tvaru dlaždice.
	 */
	static BoundingBox bunkaDem(final String jmeno) {
		final Matcher m = SlouceneDem.DLAZDICE.matcher(jmeno);
		if (!m.matches()) {
			return null;
		}
		final String d = m.group(1).toUpperCase(Locale.ROOT);
		final int e = d.indexOf('E') >= 0 ? d.indexOf('E') : d.indexOf('W');
		final int sirka;
		final int delka;
		try {
			sirka = (d.charAt(0) == 'S' ? -1 : 1) * Integer.parseInt(d.substring(1, e));
			delka = (d.charAt(e) == 'W' ? -1 : 1) * Integer.parseInt(d.substring(e + 1));
		} catch (final NumberFormatException ex) {
			return null;
		}
		if (sirka < -90 || sirka > 89 || delka < -180 || delka > 179) {
			return null;
		}
		return new BoundingBox(sirka, delka, sirka + 1, delka + 1);
	}

	/** Přidá zdroje pro soubor DEM (.hgt nebo .zip s dlaždicemi): jeden na každou buňku, kterou soubor pokrývá. */
	static void dem(final List<Zdroj> zdroje, final File f) {
		final int pred = zdroje.size();
		final String jmeno = f.getName();
		if (jmeno.toLowerCase(Locale.ROOT).endsWith(".zip")) {
			try (ZipFile zip = new ZipFile(f)) {
				final Enumeration<? extends ZipEntry> polozky = zip.entries();
				while (polozky.hasMoreElements()) {
					final BoundingBox bunka = bunkaDem(polozky.nextElement().getName());
					if (bunka != null) {
						zdroje.add(soubor("dem", f, bunka, 0));
					}
				}
			} catch (final IOException | RuntimeException e) {
				// Nečitelný zip platí pro celý svět.
			}
		} else {
			final BoundingBox bunka = bunkaDem(jmeno);
			if (bunka != null) {
				zdroje.add(soubor("dem", f, bunka, 0));
			}
		}
		if (zdroje.size() == pred) {
			zdroje.add(celySvet("dem", f));
		}
	}

	/** Zdroj pro soubor DEM, jehož obsah nejde rozpoznat: platí pro celý svět. */
	static Zdroj celySvet(final String druh, final File f) {
		return soubor(druh, f, SVET, 0);
	}

	private static void pridej(final CRC32 crc, final int hodnota) {
		crc.update(hodnota >>> 24);
		crc.update(hodnota >>> 16);
		crc.update(hodnota >>> 8);
		crc.update(hodnota);
	}

	private static long crc(final String s) {
		final CRC32 crc = new CRC32();
		crc.update(s.getBytes(StandardCharsets.UTF_8));
		return crc.getValue();
	}
}
