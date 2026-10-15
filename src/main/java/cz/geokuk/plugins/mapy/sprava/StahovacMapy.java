package cz.geokuk.plugins.mapy.sprava;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mapsforge.map.reader.MapFile;

import cz.geokuk.plugins.mapy.sprava.StavMapy.Faze;
import cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni;
import cz.geokuk.plugins.mapy.sprava.StavMapy.Server;
import lombok.extern.slf4j.Slf4j;

/**
 * Kontrola a stažení jedné katalogové mapy. Rozpracovaný soubor leží vedle mapy s příponou {@value #PRIPONA}, takže ho hlídání složky nebere za mapu; na
 * místo mapy se přesune až ověřený. Volá se na pracovním vlákně, metody blokují.
 */
@Slf4j
final class StahovacMapy {

	static final String PRIPONA = ".stahuje";
	static final String PRIPONA_INFO = ".stahuje.info";
	/** Rezerva volného místa, aby stažení nezaplnilo disk úplně. */
	static final long REZERVA_MISTA = 50L * 1024 * 1024;
	private static final Pattern CONTENT_RANGE = Pattern.compile("bytes (\\d{1,18})-(\\d{1,18})/(\\d{1,18})");
	private static final int POKUSY_PRESUNU = 5;
	private static final int PRESMEROVANI = 5;

	interface Posluchac {
		void zmena(StavMapy stav);
	}

	private final String userAgent;
	private final Runnable uvolniMapy;
	int casovyLimitMs = (int) TimeUnit.SECONDS.toMillis(15);
	long pauzaPresunuMs = 1000;
	long volneMisto = -1;

	StahovacMapy(final String userAgent, final Runnable uvolniMapy) {
		this.userAgent = userAgent;
		this.uvolniMapy = uvolniMapy;
	}

	static File cil(final File slozka, final KatalogovaMapa mapa) {
		return new File(slozka, mapa.getNazevSouboru());
	}

	static File rozpracovany(final File slozka, final KatalogovaMapa mapa) {
		return new File(slozka, mapa.getNazevSouboru() + PRIPONA);
	}

	static File info(final File slozka, final KatalogovaMapa mapa) {
		return new File(slozka, mapa.getNazevSouboru() + PRIPONA_INFO);
	}

	/** Doplní stav podle souborů na disku. */
	static StavMapy mistniStav(final StavMapy stav, final File slozka) {
		final File cil = cil(slozka, stav.getMapa());
		final boolean rozpracovano = rozpracovany(slozka, stav.getMapa()).isFile();
		StavMapy s;
		if (!cil.isFile()) {
			s = stav.sMistni(Mistni.NEMAME, null, -1, rozpracovano);
		} else {
			try {
				final HlavickaMapy h = HlavickaMapy.precti(cil);
				s = stav.sMistni(Mistni.MAME, h.getDatumDat(), h.getVelikostSouboru(), rozpracovano);
			} catch (final IOException e) {
				s = stav.sMistni(Mistni.POSKOZENA, null, cil.length(), rozpracovano);
			}
		}
		if (rozpracovano && !s.isBezi()) {
			s = s.sPrubehem(rozpracovany(slozka, stav.getMapa()).length(), nactiInfo(slozka, stav.getMapa()).celkem, true);
		}
		return s.sServerem(s.porovnej());
	}

	/** Podmíněný HEAD: když server řekne „nezměněno“, platí naposledy zjištěné údaje. */
	StavMapy zkontroluj(final StavMapy stav, final File slozka, final Posluchac posluchac) {
		StavMapy s = mistniStav(stav, slozka).sFazi(Faze.KONTROLUJE, null);
		posluchac.zmena(s);
		HttpURLConnection c = null;
		try {
			final Map<String, String> hlavicky = new HashMap<>();
			if (s.getEtag() != null) {
				hlavicky.put("If-None-Match", s.getEtag());
			} else if (s.getLastModified() != null) {
				hlavicky.put("If-Modified-Since", s.getLastModified());
			}
			c = spojeniNaServer(stav.getMapa().getUrlyMapy(), "HEAD", hlavicky).spojeni;
			final int kod = c.getResponseCode();
			final Instant ted = Instant.now();
			if (kod == HttpURLConnection.HTTP_NOT_MODIFIED) {
				s = s.sServerem(s.getServer(), s.getDatumNaServeru(), s.getVelikostNaServeru(), s.getEtag(), s.getLastModified(), ted);
			} else if (kod == HttpURLConnection.HTTP_OK) {
				s = s.sServerem(Server.NEZNAMY, datum(c), c.getContentLengthLong(), c.getHeaderField("ETag"), c.getHeaderField("Last-Modified"), ted);
			} else if (kod == HttpURLConnection.HTTP_NOT_FOUND || kod == HttpURLConnection.HTTP_GONE) {
				return s.sServerem(Server.CHYBI, null, -1, null, null, ted).sFazi(Faze.CHYBA, "Mapa na serveru není.");
			} else {
				return s.sServerem(Server.NEDOSTUPNY).sFazi(Faze.CHYBA, "Server odpověděl " + kod + ".");
			}
			s = s.sServerem(s.getVelikostNaServeru() < 0 ? Server.NEZNAMY : Server.AKTUALNI);
			return s.sServerem(s.getMistni() == Mistni.MAME ? s.porovnej() : s.getServer()).sFazi(Faze.NIC, null);
		} catch (final IOException e) {
			log.info("Kontrola mapy {} selhala: {}", stav.getMapa(), e.toString());
			return s.sServerem(Server.NEDOSTUPNY).sFazi(Faze.CHYBA, e instanceof ChybaProUzivatele ? e.getMessage() : "Server s mapami není dostupný.");
		} finally {
			if (c != null) {
				c.disconnect();
			}
		}
	}

	/** Stáhne, ověří a přesune mapu na místo; po zrušení nebo přerušení nechá rozpracovaný soubor pro pokračování. */
	StavMapy stahni(final StavMapy stav, final File slozka, final BooleanSupplier zruseno, final Posluchac posluchac) {
		final KatalogovaMapa mapa = stav.getMapa();
		final File cast = rozpracovany(slozka, mapa);
		final File infoSoubor = info(slozka, mapa);
		StavMapy s = mistniStav(stav, slozka).sFazi(Faze.STAHUJE, null);
		posluchac.zmena(s);
		if (!slozka.isDirectory() || !Files.isWritable(slozka.toPath())) {
			return s.sFazi(Faze.CHYBA, "Do složky offline map " + slozka + " nejde zapisovat.");
		}
		Info info = nactiInfo(slozka, mapa);
		final String puvodniUrl = info.url;
		if (!cast.isFile() || mapa.getUrlyMapy().stream().noneMatch(u -> u.toString().equals(puvodniUrl))) {
			info = new Info();
			smaz(cast);
		}
		try {
			if (info.celkem < 0 || cast.length() < info.celkem) {
				try {
					s = stahniData(s, slozka, cast, infoSoubor, info, zruseno, posluchac);
				} catch (final ZacitZnovu e) {
					s = stahniData(s, slozka, cast, infoSoubor, new Info(), zruseno, posluchac);
				}
				if (s.getFaze() != Faze.STAHUJE) {
					return s;
				}
			}
			s = s.sFazi(Faze.OVERUJE, null);
			posluchac.zmena(s);
			final String vada = over(cast, nactiInfo(slozka, mapa).celkem);
			if (vada != null) {
				smaz(cast);
				smaz(infoSoubor);
				return mistniStav(s, slozka).sFazi(Faze.CHYBA, vada);
			}
			final String chybaPresunu = presun(cast, cil(slozka, mapa));
			if (chybaPresunu != null) {
				return mistniStav(s, slozka).sFazi(Faze.CHYBA, chybaPresunu);
			}
			smaz(infoSoubor);
			log.info("Offline mapa {} stažena do {}", mapa, cil(slozka, mapa));
			s = mistniStav(s, slozka);
			return s.sServerem(Server.AKTUALNI).sPrubehem(s.getVelikostMistni(), s.getVelikostMistni(), false).sFazi(Faze.HOTOVO, null);
		} catch (final IOException e) {
			if (zruseno.getAsBoolean()) {
				return mistniStav(s, slozka).sFazi(Faze.ZRUSENO, null);
			}
			log.info("Stahování mapy {} selhalo: {}", mapa, e.toString());
			final String text;
			if (e instanceof ChybaProUzivatele) {
				text = e.getMessage();
			} else if (slozka.getUsableSpace() < REZERVA_MISTA) {
				text = "Na disku došlo místo. Rozpracovanou mapu můžete zahodit.";
			} else {
				text = "Stahování se přerušilo, lze ho dokončit.";
			}
			return mistniStav(s, slozka).sFazi(Faze.CHYBA, text);
		}
	}

	/** Vrátí stav ve fázi STAHUJE, když jsou data celá na disku; jinak výsledný stav (zrušeno, chyba). */
	private StavMapy stahniData(StavMapy s, final File slozka, final File cast, final File infoSoubor, final Info info, final BooleanSupplier zruseno,
			final Posluchac posluchac) throws IOException {
		final KatalogovaMapa mapa = s.getMapa();
		final String validator = info.etag != null ? info.etag : info.lastModified;
		// Bez validátoru by server mohl navázat jinou verzí mapy, proto se pak začíná od nuly.
		long od = cast.isFile() && validator != null ? cast.length() : 0;
		final Map<String, String> hlavicky = new HashMap<>();
		if (od > 0) {
			hlavicky.put("Range", "bytes=" + od + "-");
			hlavicky.put("If-Range", validator);
		}
		// Navazuje se u serveru, od kterého je rozpracovaná část; validátor jiného serveru by nesouhlasil.
		final List<URI> adresy = new ArrayList<>(mapa.getUrlyMapy());
		adresy.sort(Comparator.comparing(u -> !u.toString().equals(info.url)));
		final Odpoved odpoved = spojeniNaServer(adresy, "GET", hlavicky);
		final HttpURLConnection c = odpoved.spojeni;
		try {
			final int kod = c.getResponseCode();
			final long celkem;
			if (kod == HttpURLConnection.HTTP_PARTIAL && od > 0) {
				celkem = celkemZRozsahu(c.getHeaderField("Content-Range"), od);
				if (celkem != info.celkem || !stejnaVerze(info, c.getHeaderField("ETag"), c.getHeaderField("Last-Modified"))) {
					// Server navázal jinou verzí mapy (If-Range nerespektoval); slepenec dvou verzí by prošel ověřením.
					log.info("Mapa {} se na serveru změnila, rozpracovaná část se zahodí", mapa);
					smaz(cast);
					smaz(infoSoubor);
					throw new ZacitZnovu();
				}
			} else if (kod == HttpURLConnection.HTTP_OK) {
				od = 0;
				celkem = c.getContentLengthLong();
			} else if (kod == 416) {
				// Rozpracovaný soubor nesedí se serverem.
				smaz(cast);
				smaz(infoSoubor);
				throw new ZacitZnovu();
			} else if (kod == HttpURLConnection.HTTP_NOT_FOUND || kod == HttpURLConnection.HTTP_GONE) {
				return s.sServerem(Server.CHYBI).sFazi(Faze.CHYBA, "Mapa na serveru není.");
			} else {
				return s.sServerem(Server.NEDOSTUPNY).sFazi(Faze.CHYBA, "Server odpověděl " + kod + ".");
			}
			if (celkem <= 0) {
				throw new ChybaProUzivatele("Server neuvedl velikost mapy.");
			}
			final long volno = volneMisto >= 0 ? volneMisto : slozka.getUsableSpace();
			if (volno < celkem - od + REZERVA_MISTA) {
				return s.sFazi(Faze.CHYBA, "Na disku je málo místa: mapa potřebuje " + (celkem - od) / (1024 * 1024) + " MB.");
			}
			if (od == 0) {
				info.url = odpoved.adresa.toString();
				info.etag = c.getHeaderField("ETag");
				info.lastModified = c.getHeaderField("Last-Modified");
				info.celkem = celkem;
				ulozInfo(infoSoubor, info);
			}
			s = s.sServerem(Server.AKTUALNI, datum(c), celkem, info.etag, info.lastModified, Instant.now()).sPrubehem(od, celkem, true);
			posluchac.zmena(s);
			final StavMapy zaklad = s;
			final long mame = prenes(c, cast, od, celkem, zruseno, n -> posluchac.zmena(zaklad.sPrubehem(n, celkem, true)));
			s = s.sPrubehem(mame, celkem, true);
			if (zruseno.getAsBoolean()) {
				return s.sFazi(Faze.ZRUSENO, null);
			}
			if (mame < celkem) {
				return s.sFazi(Faze.CHYBA, "Spojení se přerušilo, stahování lze dokončit.");
			}
			return s;
		} finally {
			c.disconnect();
		}
	}

	/**
	 * Zapíše data odpovědi do souboru od bajtu {@code od} (od nuly soubor přepíše), nejvýš do velikosti {@code celkem}. Vrátí počet bajtů v souboru; méně než
	 * {@code celkem} po zrušení nebo předčasném konci dat.
	 */
	static long prenes(final HttpURLConnection c, final File cast, final long od, final long celkem, final BooleanSupplier zruseno, final LongConsumer prubeh)
			throws IOException {
		final byte[] buf = new byte[64 * 1024];
		long mame = od;
		try (InputStream in = c.getInputStream();
				OutputStream out = Files.newOutputStream(cast.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE,
						od > 0 ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING)) {
			// Zrušení se projeví u dalšího úseku dat; zaseknutý server ukončí časový limit čtení.
			while (mame < celkem && !zruseno.getAsBoolean()) {
				final int n = in.read(buf, 0, (int) Math.min(buf.length, celkem - mame));
				if (n < 0) {
					break;
				}
				out.write(buf, 0, n);
				mame += n;
				prubeh.accept(mame);
			}
		}
		return mame;
	}

	/** Celková velikost z Content-Range odpovědi na pokračování od bajtu {@code od}. */
	static long celkemZRozsahu(final String contentRange, final long od) throws ChybaProUzivatele {
		final Matcher m = CONTENT_RANGE.matcher(String.valueOf(contentRange));
		try {
			if (m.matches() && Long.parseLong(m.group(1)) == od) {
				return Long.parseLong(m.group(3));
			}
		} catch (final NumberFormatException e) {
			// spadne do chyby níže
		}
		throw new ChybaProUzivatele("Server poslal jinou část mapy, než o kterou byl požádán.");
	}

	/** Odpověď na pokračování patří ke stejné verzi mapy jako rozpracovaná část. */
	static boolean stejnaVerze(final Info info, final String etag, final String lastModified) {
		if (info.etag != null) {
			return info.etag.equals(etag);
		}
		return info.lastModified != null && info.lastModified.equals(lastModified);
	}

	/** Null když je stažený soubor platná mapa očekávané velikosti, jinak popis vady. */
	static String over(final File soubor, final long ocekavanaVelikost) {
		if (ocekavanaVelikost >= 0 && soubor.length() != ocekavanaVelikost) {
			return "Stažená mapa má jinou velikost, než uvedl server.";
		}
		if (!soubor.isFile()) {
			return "Rozpracovaná mapa zmizela z disku.";
		}
		try {
			HlavickaMapy.precti(soubor);
		} catch (final IOException e) {
			return "Stažená mapa je poškozená: " + e.getMessage();
		}
		try {
			new MapFile(soubor).close();
		} catch (final RuntimeException e) {
			log.info("Stažená mapa {} nejde otevřít: {}", soubor, e.toString());
			return "Stažená mapa je poškozená.";
		}
		return null;
	}

	/** Přesune ověřenou mapu na místo staré; otevřenou starou mapu napřed uvolní. Null = povedlo se. */
	String presun(final File cast, final File cil) {
		FileSystemException posledni = null;
		for (int pokus = 0; pokus < POKUSY_PRESUNU; pokus++) {
			if (pokus > 0 || cil.exists()) {
				uvolniMapy.run();
			}
			try {
				try {
					Files.move(cast.toPath(), cil.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				} catch (final AtomicMoveNotSupportedException e) {
					Files.move(cast.toPath(), cil.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
				return null;
			} catch (final FileSystemException e) {
				posledni = e;
			} catch (final IOException e) {
				log.info("Mapu {} nejde přesunout na {}: {}", cast, cil, e.toString());
				return "Mapu nejde uložit do složky offline map.";
			}
			try {
				Thread.sleep(pauzaPresunuMs);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		log.info("Mapu {} nejde přesunout na {}: {}", cast, cil, String.valueOf(posledni));
		return "Starou mapu " + cil.getName() + " nejde nahradit, asi ji používá jiný program. Stažení dokončíte znovu tlačítkem Stáhnout.";
	}

	static final class Odpoved {
		final HttpURLConnection spojeni;
		final URI adresa;

		Odpoved(final HttpURLConnection spojeni, final URI adresa) {
			this.spojeni = spojeni;
			this.adresa = adresa;
		}
	}

	/** Zkouší servery po řadě; další zkusí, když předchozí není dostupný, mapu nemá nebo hlásí chybu serveru. */
	Odpoved spojeniNaServer(final List<URI> adresy, final String metoda, final Map<String, String> hlavicky) throws IOException {
		IOException chyba = null;
		for (int i = 0; i < adresy.size(); i++) {
			final boolean posledni = i == adresy.size() - 1;
			final HttpURLConnection c;
			try {
				c = spojeni(adresy.get(i).toURL(), metoda, hlavicky);
			} catch (final ChybaProUzivatele e) {
				throw e;
			} catch (final IOException e) {
				log.info("Server {} není dostupný: {}", adresy.get(i).getHost(), e.toString());
				chyba = e;
				if (posledni) {
					throw new ChybaProUzivatele("Server s mapami není dostupný.");
				}
				continue;
			}
			final int kod = c.getResponseCode();
			if (posledni || kod < 500 && kod != HttpURLConnection.HTTP_NOT_FOUND && kod != HttpURLConnection.HTTP_GONE) {
				return new Odpoved(c, adresy.get(i));
			}
			log.info("Server {} odpověděl {}, zkusí se další", adresy.get(i).getHost(), kod);
			c.disconnect();
		}
		throw chyba != null ? chyba : new IOException("Žádný server");
	}

	/** Z http jen na http nebo https, ze zabezpečeného https zase jen na https. */
	static boolean smiPresmerovat(final URL z, final URL na) {
		final String kam = na.getProtocol();
		return "https".equals(kam) || "http".equals(kam) && "http".equals(z.getProtocol());
	}

	/** Spojení s odpovědí; přesměrování (i z http na https, třeba na zrcadlo) následuje samo, nejvýš {@value #PRESMEROVANI}×. */
	private HttpURLConnection spojeni(final URL url, final String metoda, final Map<String, String> hlavicky) throws IOException {
		URL adresa = url;
		for (int i = 0;; i++) {
			final HttpURLConnection c = (HttpURLConnection) adresa.openConnection();
			c.setRequestMethod(metoda);
			c.setInstanceFollowRedirects(false);
			c.setConnectTimeout(casovyLimitMs);
			c.setReadTimeout(casovyLimitMs);
			c.setUseCaches(false);
			c.setRequestProperty("User-Agent", userAgent);
			c.setRequestProperty("Accept-Encoding", "identity");
			hlavicky.forEach(c::setRequestProperty);
			final int kod = c.getResponseCode();
			final String kam = c.getHeaderField("Location");
			if (kod < 300 || kod > 308 || kod == HttpURLConnection.HTTP_NOT_MODIFIED || kam == null) {
				return c;
			}
			c.disconnect();
			final URL dalsi = new URL(adresa, kam);
			if (i >= PRESMEROVANI || !smiPresmerovat(adresa, dalsi)) {
				throw new ChybaProUzivatele("Server mapu přesměroval na neplatnou nebo nezabezpečenou adresu.");
			}
			adresa = dalsi;
		}
	}

	private static Instant datum(final HttpURLConnection c) {
		final long ms = c.getLastModified();
		return ms > 0 ? Instant.ofEpochMilli(ms) : null;
	}

	static final class Info {
		String url;
		String etag;
		String lastModified;
		/** Jen u vrstev: kontrolní součet souboru, ke kterému rozpracovaná část patří. */
		String sha256;
		long celkem = -1;
	}

	static Info nactiInfo(final File slozka, final KatalogovaMapa mapa) {
		return nactiInfo(info(slozka, mapa));
	}

	static Info nactiInfo(final File f) {
		final Info info = new Info();
		if (!f.isFile()) {
			return info;
		}
		final Properties p = new Properties();
		try (Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
			p.load(r);
			info.url = p.getProperty("url");
			info.etag = p.getProperty("etag");
			info.lastModified = p.getProperty("lastModified");
			info.sha256 = p.getProperty("sha256");
			info.celkem = Long.parseLong(p.getProperty("celkem", "-1"));
		} catch (final IOException | RuntimeException e) {
			log.info("Údaje o rozpracované mapě {} nejde přečíst: {}", f, e.toString());
			return new Info();
		}
		return info;
	}

	static void ulozInfo(final File f, final Info info) throws IOException {
		final Properties p = new Properties();
		p.setProperty("url", info.url);
		if (info.etag != null) {
			p.setProperty("etag", info.etag);
		}
		if (info.lastModified != null) {
			p.setProperty("lastModified", info.lastModified);
		}
		if (info.sha256 != null) {
			p.setProperty("sha256", info.sha256);
		}
		p.setProperty("celkem", Long.toString(info.celkem));
		try (Writer w = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8)) {
			p.store(w, null);
		}
	}

	/** Zahodí rozpracované stahování mapy. */
	static void zahod(final File slozka, final KatalogovaMapa mapa) {
		smaz(rozpracovany(slozka, mapa));
		smaz(info(slozka, mapa));
	}

	static void smaz(final File f) {
		try {
			Files.deleteIfExists(f.toPath());
		} catch (final IOException e) {
			log.info("Soubor {} nejde smazat: {}", f, e.toString());
		}
	}

	/** Rozpracovaná část nepatří k mapě na serveru; stahuje se znovu od začátku. */
	private static final class ZacitZnovu extends IOException {
		private static final long serialVersionUID = 1L;
	}

	/** Chyba, jejíž text je pro uživatele. */
	static final class ChybaProUzivatele extends IOException {
		private static final long serialVersionUID = 1L;

		ChybaProUzivatele(final String zprava) {
			super(zprava);
		}
	}
}
