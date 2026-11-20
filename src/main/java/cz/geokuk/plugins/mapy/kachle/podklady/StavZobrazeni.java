package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Soubor s dlaždicemi naposledy zobrazeného výřezu ({@value #SOUBOR} ve složce cache dlaždic): hlavička a záznamy s bajty obrázku tak, jak jsou v cache. Každá
 * část má kontrolní součet; soubor jiné verze, poškozený, useknutý nebo větší než {@link #MAX_SOUBOR} neplatí celý.
 */
final class StavZobrazeni {

	static final String SOUBOR = "posledni-zobrazeni.bin";
	static final int MAGIC = 0x474B535A; // "GKSZ"
	static final int VERZE = 1;
	/** Bajty obrázků v jednom souboru; záznamy nad strop se nezapíšou. */
	static final int MAX_DATA = 8 * 1024 * 1024;
	/** Větší soubor se při čtení nebere. */
	static final long MAX_SOUBOR = MAX_DATA + 1024 * 1024;

	/** Výřez, ke kterému dlaždice patří. */
	static final class Hlavicka {
		final long cas;
		final String podklad;
		final int moumer;
		final int mouX;
		final int mouY;

		Hlavicka(final long cas, final String podklad, final int moumer, final int mouX, final int mouY) {
			this.cas = cas;
			this.podklad = podklad;
			this.moumer = moumer;
			this.mouX = mouX;
			this.mouY = mouY;
		}
	}

	/** Dlaždice: typ mapy ({@code EKaType.name()}), z/x/y (XYZ), typ v cache a bajty obrázku. */
	static final class Zaznam {
		final String typ;
		final int z;
		final int x;
		final int y;
		final String typVCache;
		final byte[] data;

		Zaznam(final String typ, final int z, final int x, final int y, final String typVCache, final byte[] data) {
			this.typ = typ;
			this.z = z;
			this.x = x;
			this.y = y;
			this.typVCache = typVCache;
			this.data = data;
		}
	}

	static final class Stav {
		final Hlavicka hlavicka;
		final List<Zaznam> zaznamy;

		Stav(final Hlavicka hlavicka, final List<Zaznam> zaznamy) {
			this.hlavicka = hlavicka;
			this.zaznamy = Collections.unmodifiableList(zaznamy);
		}
	}

	private StavZobrazeni() {}

	/**
	 * Zapíše stav atomicky (dočasný soubor, zápis na disk, přejmenování); při chybě zůstane dosavadní soubor. Záznamy se berou v daném pořadí, dokud se bajty
	 * vejdou do {@link #MAX_DATA}. Vrátí počet zapsaných záznamů.
	 */
	static int zapis(final File slozka, final Hlavicka h, final List<Zaznam> zaznamy) throws IOException {
		final List<Zaznam> vybrane = new ArrayList<>();
		long data = 0;
		for (final Zaznam z : zaznamy) {
			if (data + z.data.length > MAX_DATA) {
				break;
			}
			data += z.data.length;
			vybrane.add(z);
		}
		Files.createDirectories(slozka.toPath());
		final Path cil = slozka.toPath().resolve(SOUBOR);
		final Path docasny = slozka.toPath().resolve(SOUBOR + ".tmp");
		try (FileChannel kanal = FileChannel.open(docasny, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
			final DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(kanal), 64 * 1024));
			out.writeInt(MAGIC);
			out.writeInt(VERZE);
			final ByteArrayOutputStream hb = new ByteArrayOutputStream();
			final DataOutputStream hd = new DataOutputStream(hb);
			hd.writeLong(h.cas);
			hd.writeUTF(h.podklad);
			hd.writeInt(h.moumer);
			hd.writeInt(h.mouX);
			hd.writeInt(h.mouY);
			hd.writeInt(vybrane.size());
			zapisSCrc(out, hb.toByteArray());
			for (final Zaznam z : vybrane) {
				final ByteArrayOutputStream zb = new ByteArrayOutputStream(z.data.length + 64);
				final DataOutputStream zd = new DataOutputStream(zb);
				zd.writeUTF(z.typ);
				zd.writeInt(z.z);
				zd.writeInt(z.x);
				zd.writeInt(z.y);
				zd.writeUTF(z.typVCache);
				zd.writeInt(z.data.length);
				zd.write(z.data);
				zapisSCrc(out, zb.toByteArray());
			}
			out.flush();
			kanal.force(true);
		} catch (final IOException e) {
			Files.deleteIfExists(docasny);
			throw e;
		}
		try {
			Files.move(docasny, cil, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (final AtomicMoveNotSupportedException e) {
			Files.move(docasny, cil, StandardCopyOption.REPLACE_EXISTING);
		}
		return vybrane.size();
	}

	private static void zapisSCrc(final DataOutputStream out, final byte[] cast) throws IOException {
		out.writeInt(cast.length);
		out.write(cast);
		final CRC32 crc = new CRC32();
		crc.update(cast);
		out.writeLong(crc.getValue());
	}

	/** Uložený stav; null, když soubor není, má jinou verzi nebo je poškozený či příliš velký. */
	static Stav nacti(final File slozka) {
		final File f = new File(slozka, SOUBOR);
		if (!f.isFile() || f.length() > MAX_SOUBOR) {
			return null;
		}
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f), 64 * 1024))) {
			if (in.readInt() != MAGIC || in.readInt() != VERZE) {
				return null;
			}
			final DataInputStream hd = new DataInputStream(new ByteArrayInputStream(ctiSCrc(in)));
			final Hlavicka h = new Hlavicka(hd.readLong(), hd.readUTF(), hd.readInt(), hd.readInt(), hd.readInt());
			final int pocet = hd.readInt();
			if (pocet < 0 || hd.available() != 0) {
				return null;
			}
			final List<Zaznam> zaznamy = new ArrayList<>();
			for (int i = 0; i < pocet; i++) {
				final DataInputStream zd = new DataInputStream(new ByteArrayInputStream(ctiSCrc(in)));
				final String typ = zd.readUTF();
				final int z = zd.readInt();
				final int x = zd.readInt();
				final int y = zd.readInt();
				final String typVCache = zd.readUTF();
				final int delka = zd.readInt();
				if (delka < 0 || delka != zd.available()) {
					return null;
				}
				final byte[] data = new byte[delka];
				zd.readFully(data);
				zaznamy.add(new Zaznam(typ, z, x, y, typVCache, data));
			}
			return in.read() == -1 ? new Stav(h, zaznamy) : null;
		} catch (final IOException | RuntimeException e) {
			return null;
		}
	}

	private static byte[] ctiSCrc(final DataInputStream in) throws IOException {
		final int delka = in.readInt();
		if (delka < 0 || delka > MAX_SOUBOR) {
			throw new IOException("Neplatná délka části: " + delka);
		}
		final byte[] cast = new byte[delka];
		in.readFully(cast);
		final CRC32 crc = new CRC32();
		crc.update(cast);
		if (in.readLong() != crc.getValue()) {
			throw new IOException("Nesouhlasí kontrolní součet");
		}
		return cast;
	}
}
