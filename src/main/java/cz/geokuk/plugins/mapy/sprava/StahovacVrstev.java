package cz.geokuk.plugins.mapy.sprava;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

import cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva.Format;
import cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva.Soubor;
import cz.geokuk.plugins.mapy.sprava.StahovacMapy.ChybaProUzivatele;
import cz.geokuk.plugins.mapy.sprava.StahovacMapy.Info;
import cz.geokuk.plugins.mapy.sprava.StavMapy.Faze;
import cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni;
import lombok.extern.slf4j.Slf4j;

/**
 * Stažení vrstvy z katalogu: všechny její soubory se stáhnou vedle cíle s příponou {@value StahovacMapy#PRIPONA}, ověří podle velikosti a SHA-256 a teprve
 * pak se přesunou na místo, takže vrstva je buď celá, nebo žádná. Volá se na pracovním vlákně, metody blokují.
 */
@Slf4j
final class StahovacVrstev {

	interface Posluchac {
		void zmena(StavVrstvy stav);
	}

	private final StahovacMapy http;
	long volneMisto = -1;

	/** Spojení, časové limity a přesun souborů sdílí se stahovačem map. */
	StahovacVrstev(final StahovacMapy http) {
		this.http = http;
	}

	static File cil(final File slozka, final Soubor s) {
		return new File(slozka, s.getNazev());
	}

	static File rozpracovany(final File slozka, final Soubor s) {
		return new File(slozka, s.getNazev() + StahovacMapy.PRIPONA);
	}

	static File info(final File slozka, final Soubor s) {
		return new File(slozka, s.getNazev() + StahovacMapy.PRIPONA_INFO);
	}

	/** Doplní stav podle souborů na disku; soubor sedí s katalogem, když má jeho velikost. */
	static StavVrstvy mistniStav(final StavVrstvy stav, final File slozka) {
		int mame = 0;
		int jine = 0;
		long stazeno = 0;
		boolean rozpracovano = false;
		for (final Soubor s : stav.getVrstva().getSoubory()) {
			final File c = cil(slozka, s);
			if (c.isFile() && c.length() == s.getVelikost()) {
				mame++;
			} else if (c.exists()) {
				jine++;
			}
			final File r = rozpracovany(slozka, s);
			if (r.isFile()) {
				rozpracovano = true;
				stazeno += r.length();
			} else if (c.isFile() && c.length() == s.getVelikost()) {
				stazeno += s.getVelikost();
			}
		}
		final Mistni m;
		if (mame == stav.getVrstva().getSoubory().size()) {
			m = Mistni.MAME;
		} else if (mame == 0 && jine == 0) {
			m = Mistni.NEMAME;
		} else {
			m = Mistni.JINA_VERZE;
		}
		return stav.sMistni(m, rozpracovano || m == Mistni.MAME ? stazeno : 0, rozpracovano);
	}

	/** Stáhne, ověří a přesune všechny soubory vrstvy; po zrušení nebo přerušení nechá rozpracované soubory pro pokračování. */
	StavVrstvy stahni(final StavVrstvy stav, final File slozka, final BooleanSupplier zruseno, final Posluchac posluchac) {
		final KatalogovaVrstva vrstva = stav.getVrstva();
		StavVrstvy s = mistniStav(stav, slozka).sFazi(Faze.STAHUJE, null);
		posluchac.zmena(s);
		try {
			Files.createDirectories(slozka.toPath());
		} catch (final IOException e) {
			log.info("Složku vrstev {} nejde vytvořit: {}", slozka, e.toString());
		}
		if (!slozka.isDirectory() || !Files.isWritable(slozka.toPath())) {
			return s.sFazi(Faze.CHYBA, "Do složky vrstev " + slozka + " nejde zapisovat.");
		}
		long chybi = 0;
		for (final Soubor so : vrstva.getSoubory()) {
			if (!hotovy(slozka, so)) {
				chybi += so.getVelikost() - Math.min(so.getVelikost(), rozpracovany(slozka, so).length());
			}
		}
		final long volno = volneMisto >= 0 ? volneMisto : slozka.getUsableSpace();
		if (volno < chybi + StahovacMapy.REZERVA_MISTA) {
			return s.sFazi(Faze.CHYBA, "Na disku je málo místa: vrstva potřebuje " + chybi / (1024 * 1024) + " MB.");
		}
		try {
			long predchozi = 0;
			for (final Soubor so : vrstva.getSoubory()) {
				if (!hotovy(slozka, so)) {
					final StavVrstvy zaklad = s;
					final long pred = predchozi;
					final long mame = stahniSoubor(so, slozka, zruseno, n -> posluchac.zmena(zaklad.sPrubehem(pred + n)));
					s = s.sPrubehem(predchozi + mame);
					if (zruseno.getAsBoolean()) {
						return s.sFazi(Faze.ZRUSENO, null);
					}
					if (mame < so.getVelikost()) {
						return s.sFazi(Faze.CHYBA, "Spojení se přerušilo, stahování lze dokončit.");
					}
				}
				predchozi += so.getVelikost();
			}
			s = s.sFazi(Faze.OVERUJE, null);
			posluchac.zmena(s);
			for (final Soubor so : vrstva.getSoubory()) {
				if (!cilSedi(slozka, so)) {
					final String vada = over(rozpracovany(slozka, so), so, vrstva.getFormat(), zruseno);
					if (zruseno.getAsBoolean()) {
						return mistniStav(s, slozka).sFazi(Faze.ZRUSENO, null);
					}
					if (vada != null) {
						StahovacMapy.smaz(rozpracovany(slozka, so));
						StahovacMapy.smaz(info(slozka, so));
						return mistniStav(s, slozka).sFazi(Faze.CHYBA, vada);
					}
				}
			}
			for (final Soubor so : vrstva.getSoubory()) {
				if (rozpracovany(slozka, so).isFile()) {
					final String chybaPresunu = http.presun(rozpracovany(slozka, so), cil(slozka, so));
					if (chybaPresunu != null) {
						return mistniStav(s, slozka).sFazi(Faze.CHYBA, chybaPresunu);
					}
				}
				StahovacMapy.smaz(info(slozka, so));
			}
			log.info("Vrstva {} stažena do {}", vrstva, slozka);
			return mistniStav(s, slozka).sFazi(Faze.HOTOVO, null);
		} catch (final IOException e) {
			if (zruseno.getAsBoolean()) {
				return mistniStav(s, slozka).sFazi(Faze.ZRUSENO, null);
			}
			log.info("Stahování vrstvy {} selhalo: {}", vrstva, e.toString());
			final String text;
			if (e instanceof ChybaProUzivatele) {
				text = e.getMessage();
			} else if (slozka.getUsableSpace() < StahovacMapy.REZERVA_MISTA) {
				text = "Na disku došlo místo. Rozpracovanou vrstvu můžete zahodit.";
			} else {
				text = "Stahování se přerušilo, lze ho dokončit.";
			}
			return mistniStav(s, slozka).sFazi(Faze.CHYBA, text);
		}
	}

	/** Soubor už nemusí přes síť: je na místě, nebo celý stažený a čeká na ověření. */
	private static boolean hotovy(final File slozka, final Soubor so) {
		if (cilSedi(slozka, so)) {
			return true;
		}
		final File r = rozpracovany(slozka, so);
		return r.isFile() && r.length() == so.getVelikost() && so.getSha256().equals(StahovacMapy.nactiInfo(info(slozka, so)).sha256);
	}

	/** Na místě je soubor této verze (velikost podle katalogu) a nic se pro něj nestahuje. */
	private static boolean cilSedi(final File slozka, final Soubor so) {
		final File c = cil(slozka, so);
		return c.isFile() && c.length() == so.getVelikost() && !rozpracovany(slozka, so).exists();
	}

	/** Stáhne jeden soubor do rozpracovaného souboru, od místa, kde skončil. Vrátí počet bajtů na disku. */
	private long stahniSoubor(final Soubor so, final File slozka, final BooleanSupplier zruseno, final LongConsumer prubeh) throws IOException {
		final File cast = rozpracovany(slozka, so);
		final File infoSoubor = info(slozka, so);
		Info info = StahovacMapy.nactiInfo(infoSoubor);
		final String puvodniUrl = info.url;
		if (!cast.isFile() || !so.getSha256().equals(info.sha256) || so.getUrly().stream().noneMatch(u -> u.toString().equals(puvodniUrl))) {
			info = new Info();
			StahovacMapy.smaz(cast);
		}
		final Info i = info;
		final String validator = info.etag != null ? info.etag : info.lastModified;
		long od = cast.isFile() && validator != null ? Math.min(cast.length(), so.getVelikost()) : 0;
		final Map<String, String> hlavicky = new HashMap<>();
		if (od > 0) {
			hlavicky.put("Range", "bytes=" + od + "-");
			hlavicky.put("If-Range", validator);
		}
		final List<URI> adresy = new ArrayList<>(so.getUrly());
		adresy.sort(Comparator.comparing(u -> !u.toString().equals(i.url)));
		final StahovacMapy.Odpoved odpoved = http.spojeniNaServer(adresy, "GET", hlavicky);
		final HttpURLConnection c = odpoved.spojeni;
		try {
			final int kod = c.getResponseCode();
			if (kod == HttpURLConnection.HTTP_PARTIAL && od > 0) {
				final long celkem = StahovacMapy.celkemZRozsahu(c.getHeaderField("Content-Range"), od);
				if (celkem != so.getVelikost() || !StahovacMapy.stejnaVerze(info, c.getHeaderField("ETag"), c.getHeaderField("Last-Modified"))) {
					throw zacitZnovu(cast, infoSoubor, "Soubor " + so.getNazev() + " se na serveru změnil, stáhněte vrstvu znovu.");
				}
			} else if (kod == HttpURLConnection.HTTP_OK) {
				od = 0;
				if (c.getContentLengthLong() >= 0 && c.getContentLengthLong() != so.getVelikost()) {
					throw new ChybaProUzivatele("Soubor " + so.getNazev() + " má na serveru jinou velikost než v katalogu.");
				}
			} else if (kod == 416) {
				throw zacitZnovu(cast, infoSoubor, "Stahování nejde dokončit, stáhněte vrstvu znovu.");
			} else if (kod == HttpURLConnection.HTTP_NOT_FOUND || kod == HttpURLConnection.HTTP_GONE) {
				throw new ChybaProUzivatele("Soubor " + so.getNazev() + " na serveru není.");
			} else {
				throw new ChybaProUzivatele("Server odpověděl " + kod + ".");
			}
			if (od == 0) {
				info.url = odpoved.adresa.toString();
				info.etag = c.getHeaderField("ETag");
				info.lastModified = c.getHeaderField("Last-Modified");
				info.sha256 = so.getSha256();
				info.celkem = so.getVelikost();
				StahovacMapy.ulozInfo(infoSoubor, info);
			}
			return StahovacMapy.prenes(c, cast, od, so.getVelikost(), zruseno, prubeh);
		} finally {
			c.disconnect();
		}
	}

	private static ChybaProUzivatele zacitZnovu(final File cast, final File info, final String text) {
		StahovacMapy.smaz(cast);
		StahovacMapy.smaz(info);
		return new ChybaProUzivatele(text);
	}

	/** Null když soubor odpovídá katalogu (velikost, SHA-256, u .map platná mapa), jinak popis vady. */
	static String over(final File soubor, final Soubor so, final Format format, final BooleanSupplier zruseno) throws IOException {
		if (!soubor.isFile()) {
			return "Rozpracovaný soubor " + so.getNazev() + " zmizel z disku.";
		}
		if (soubor.length() != so.getVelikost()) {
			return "Stažený soubor " + so.getNazev() + " má jinou velikost, než uvádí katalog.";
		}
		final String sha = sha256(soubor, zruseno);
		if (sha == null) {
			return null;
		}
		if (!sha.equals(so.getSha256())) {
			return "Stažený soubor " + so.getNazev() + " je poškozený, stáhněte vrstvu znovu.";
		}
		if (format == Format.MAP) {
			return StahovacMapy.over(soubor, so.getVelikost());
		}
		return null;
	}

	/** SHA-256 souboru malými písmeny; null po zrušení. */
	static String sha256(final File soubor, final BooleanSupplier zruseno) throws IOException {
		final MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-256");
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
		final byte[] buf = new byte[64 * 1024];
		try (InputStream in = Files.newInputStream(soubor.toPath())) {
			for (int n; (n = in.read(buf)) > 0;) {
				if (zruseno.getAsBoolean()) {
					return null;
				}
				md.update(buf, 0, n);
			}
		}
		final StringBuilder sb = new StringBuilder(64);
		for (final byte b : md.digest()) {
			sb.append(Character.forDigit(b >> 4 & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
		}
		return sb.toString();
	}

	/** Zahodí rozpracované soubory vrstvy. */
	static void zahod(final File slozka, final KatalogovaVrstva vrstva) {
		for (final Soubor so : vrstva.getSoubory()) {
			StahovacMapy.smaz(rozpracovany(slozka, so));
			StahovacMapy.smaz(info(slozka, so));
		}
	}
}
