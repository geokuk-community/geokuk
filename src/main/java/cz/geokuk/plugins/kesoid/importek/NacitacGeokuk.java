package cz.geokuk.plugins.kesoid.importek;

import java.io.*;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.util.exception.EExceptionSeverity;
import cz.geokuk.util.exception.FExceptionDumper;
import cz.geokuk.util.file.TextovySoubor;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Martin Veverka
 *
 */
@Slf4j
public class NacitacGeokuk extends NacitacInputStream0 {

	/**
	 *
	 */
	private static final String HLAVICKA = "*geokuk:exportversion=2";

	@Override
	protected void nacti(final InputStream aIstm, final String name, final IImportBuilder builder, final Future<?> future) throws IOException {
		final ByteArrayOutputStream bajty = new ByteArrayOutputStream();
		final byte[] buf = new byte[65536];
		for (int n; (n = aIstm.read(buf)) != -1;) {
			bajty.write(buf, 0, n);
		}
		// Starší exporty jsou v cp1250; neplatné UTF-8 se nesmí tiše změnit na náhradní znaky.
		final BufferedReader rdr = new BufferedReader(new StringReader(TextovySoubor.dekoduj(bajty.toByteArray())));

		String hlavicka = rdr.readLine();
		if (hlavicka == null) {
			return; // prázdný soubor, nic k načtení
		}
		if (!hlavicka.isEmpty() && hlavicka.charAt(0) == 0xfeff) {
			hlavicka = hlavicka.substring(1);
		}
		if (!HLAVICKA.equals(hlavicka)) {
			throw new RuntimeException("Přečtena hlavička: \"" + hlavicka + "\", ale má tam být\"" + HLAVICKA + "\"");
		}
		String line;
		int linenumber = 1; // máme přeci přečtnou hlavičku
		int ncaches = 0;
		int nwpts = 0;
		int bezKese = 0;
		String jmenoAktualniKese = null;
		// AWptType xxxxxxxx = AWptType.TRAILHEAD;

		GpxWpt gpxwpt = null;
		while ((line = rdr.readLine()) != null) {
			if (future != null && future.isCancelled()) {
				return;
			}
			try {
				linenumber++;
				line = line.trim();
				if (line.length() == 0) {
					continue;
				}
				final char cmd = line.charAt(0);
				final String[] pp = line.substring(1).split("\\|", 19);

				switch (cmd) {
				case ':': {
					gpxwpt = new GpxWpt();
					gpxwpt.groundspeak = new Groundspeak();
					jmenoAktualniKese = pp[0];
					gpxwpt.groundspeak.type = pp[1].intern();
					gpxwpt.groundspeak.container = pp[2];
					gpxwpt.groundspeak.difficulty = pp[3].intern();
					gpxwpt.groundspeak.terrain = pp[4].intern();
					gpxwpt.groundspeak.archived = Boolean.parseBoolean(pp[5]);
					gpxwpt.groundspeak.availaible = !Boolean.parseBoolean(pp[6]);
					gpxwpt.explicitneUrcenoVlastnictvi = Boolean.parseBoolean(pp[8]);
					gpxwpt.sym = Boolean.parseBoolean(pp[7]) ? KesoidImportBuilder.GEOCACHE_FOUND : KesoidImportBuilder.GEOCACHE;
					gpxwpt.groundspeak.owner = pp[9].trim().intern();
					gpxwpt.groundspeak.placedBy = pp[9].trim().intern();
					gpxwpt.time = pp[10];
					// kes.setCountry(pp[11].intern());
					// kes.setState(pp[12].intern());
					gpxwpt.gpxg.bestOf = parseCislo(pp[13]);
					gpxwpt.gpxg.hodnoceni = parseCislo(pp[14]);
					gpxwpt.gpxg.hodnoceniPocet = parseCislo(pp[15]);
					gpxwpt.gpxg.znamka = parseCislo(pp[16]);
					gpxwpt.groundspeak.encodedHints = pp[17];
					gpxwpt.link.href = pp[18];
					break;
				}
				case '-': {
					if (jmenoAktualniKese == null) {
						bezKese++; // jméno waypointu se skládá ze jména keše
						gpxwpt = null;
						break;
					}
					if (gpxwpt == null) {
						gpxwpt = new GpxWpt();
					}
					gpxwpt.name = pp[0] + jmenoAktualniKese.substring(2);
					if (gpxwpt.sym == null) {
						gpxwpt.sym = pp[1];
					}
					gpxwpt.wgs = new Wgs(Double.parseDouble(pp[2]), Double.parseDouble(pp[3]));
					final String nazev = pp.length > 4 ? pp[4] : "";
					gpxwpt.cmt = nazev;
					if (gpxwpt.groundspeak != null) {
						gpxwpt.groundspeak.name = nazev;
					}
					builder.addGpxWpt(gpxwpt);
					gpxwpt = null;
					nwpts++;
					break;
				}
				case '/':
					ncaches++;
					break;
				case '*': // ignorovat druhou hlavičku
					break;
				}
			} catch (final Exception e) {
				final String positionInfo = "ERORR on line=" + linenumber + " while reading cache " + jmenoAktualniKese;
				FExceptionDumper.dump(e, EExceptionSeverity.WORKARROUND, positionInfo);
			}
		}
		if (bezKese > 0) {
			log.warn("Soubor {}: {} waypointů před první keší se přeskočilo.", name, bezKese);
		}
		log.debug("Přečteno: {} keší a {} waypointů.", ncaches, nwpts);
	}

	@Override
	boolean umiNacist(final File file) {
		return umiNacist(file.getName());
	}

	@Override
	boolean umiNacist(final ZipEntry zipEntry) {
		return umiNacist(zipEntry.getName());
	}

	private boolean umiNacist(final String resourceName) {
		return resourceName.toLowerCase().trim().endsWith(".geokuk");
	}
}
