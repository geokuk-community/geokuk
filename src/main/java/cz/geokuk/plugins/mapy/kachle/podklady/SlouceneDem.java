package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mapsforge.map.layer.hills.DemFile;
import org.mapsforge.map.layer.hills.DemFolder;

/**
 * DEM dlaždice ze složky i ze všech zipů a podsložek v jedné rovině. Dlaždice téhož jména (stejné místo) z více zdrojů, třeba sousedních zemí oříznutých
 * hranicí, se skládají bod po bodu: platí první platná výška, prázdné body ({@link Short#MIN_VALUE}) doplní další zdroj. Skládají se jen soubory největší
 * velikosti (nejpodrobnější); bez skládání by mapsforge vzal jen jeden z nich.
 */
class SlouceneDem implements DemFolder {

	static final short PRAZDNA = Short.MIN_VALUE;

	static final Pattern DLAZDICE = Pattern.compile("(?:.*[/\\\\])?([ns]\\d{1,2}[ew]\\d{1,3})\\.hgt", Pattern.CASE_INSENSITIVE);

	private final DemFolder koren;

	SlouceneDem(final DemFolder koren) {
		this.koren = koren;
	}

	@Override
	public Iterable<DemFolder> subs() {
		return Collections.emptyList();
	}

	@Override
	public Iterable<DemFile> files() {
		final Map<String, List<DemFile>> podleDlazdice = new LinkedHashMap<>();
		final List<DemFile> ostatni = new ArrayList<>();
		sesbirej(koren, podleDlazdice, ostatni);
		final List<DemFile> vysledek = new ArrayList<>(ostatni);
		for (final List<DemFile> zdroje : podleDlazdice.values()) {
			final long nejvetsi = zdroje.stream().mapToLong(DemFile::getSize).max().getAsLong();
			final List<DemFile> skladane = new ArrayList<>();
			for (final DemFile f : zdroje) {
				if (f.getSize() == nejvetsi) {
					skladane.add(f);
				}
			}
			vysledek.add(skladane.size() == 1 ? skladane.get(0) : new SlozenaDlazdice(skladane));
		}
		return vysledek;
	}

	private static void sesbirej(final DemFolder slozka, final Map<String, List<DemFile>> podleDlazdice, final List<DemFile> ostatni) {
		for (final DemFile f : slozka.files()) {
			final Matcher m = DLAZDICE.matcher(f.getName());
			if (m.matches()) {
				podleDlazdice.computeIfAbsent(m.group(1).toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(f);
			} else {
				ostatni.add(f);
			}
		}
		for (final DemFolder sub : slozka.subs()) {
			sesbirej(sub, podleDlazdice, ostatni);
		}
	}

	/** Dlaždice složená z více souborů stejné velikosti. */
	static final class SlozenaDlazdice implements DemFile {
		private final List<DemFile> zdroje;

		SlozenaDlazdice(final List<DemFile> zdroje) {
			this.zdroje = zdroje;
		}

		@Override
		public String getName() {
			return zdroje.get(0).getName();
		}

		@Override
		public long getSize() {
			return zdroje.get(0).getSize();
		}

		@Override
		public InputStream openInputStream(final int bufferSize) throws IOException {
			return new SkladanyVstup(zdroje);
		}

		@Override
		public InputStream asStream() throws IOException {
			return new SkladanyVstup(zdroje);
		}

		@Override
		public InputStream asRawStream() throws IOException {
			return new SkladanyVstup(zdroje);
		}
	}

	/** Čte všechny zdroje po blocích a skládá je bod po bodu. */
	static final class SkladanyVstup extends InputStream {
		private static final int BLOK = 64 * 1024;

		private final List<InputStream> vstupy = new ArrayList<>();
		private final byte[] blok = new byte[BLOK];
		private final byte[] cteny = new byte[BLOK];
		private int delka;
		private int pozice;

		SkladanyVstup(final List<DemFile> zdroje) throws IOException {
			try {
				for (final DemFile f : zdroje) {
					vstupy.add(f.asRawStream());
				}
			} catch (final IOException | RuntimeException e) {
				close();
				throw e;
			}
		}

		private boolean nactiBlok() throws IOException {
			delka = nacti(vstupy.get(0), blok);
			pozice = 0;
			for (int z = 1; z < vstupy.size(); z++) {
				final int n = nacti(vstupy.get(z), cteny);
				for (int i = 0; i + 1 < Math.min(n, delka); i += 2) {
					if (blok[i] == (byte) 0x80 && blok[i + 1] == 0) {
						blok[i] = cteny[i];
						blok[i + 1] = cteny[i + 1];
					}
				}
			}
			return delka > 0;
		}

		private static int nacti(final InputStream in, final byte[] kam) throws IOException {
			int n = 0;
			while (n < kam.length) {
				final int r = in.read(kam, n, kam.length - n);
				if (r < 0) {
					break;
				}
				n += r;
			}
			return n;
		}

		@Override
		public int read() throws IOException {
			if (pozice >= delka && !nactiBlok()) {
				return -1;
			}
			return blok[pozice++] & 0xff;
		}

		@Override
		public int read(final byte[] b, final int off, final int len) throws IOException {
			if (len == 0) {
				return 0;
			}
			if (pozice >= delka && !nactiBlok()) {
				return -1;
			}
			final int n = Math.min(len, delka - pozice);
			System.arraycopy(blok, pozice, b, off, n);
			pozice += n;
			return n;
		}

		@Override
		public void close() throws IOException {
			IOException chyba = null;
			for (final InputStream in : vstupy) {
				try {
					in.close();
				} catch (final IOException e) {
					chyba = e;
				}
			}
			if (chyba != null) {
				throw chyba;
			}
		}
	}
}
