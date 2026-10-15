package cz.geokuk.plugins.mapy.sprava;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;

/** Začátek hlavičky souboru .map (mapsforge): kontrola, že jde o mapu, verze, velikost, datum dat a obdélník pokrytí. */
public final class HlavickaMapy {

	private static final byte[] MAGIE = "mapsforge binary OSM".getBytes(StandardCharsets.US_ASCII);
	private static final int NEJMENSI_HLAVICKA = 70;
	private static final int NEJVETSI_HLAVICKA = 1_000_000;

	private final int verze;
	private final long velikostSouboru;
	private final Instant datumDat;
	private final double jih, zapad, sever, vychod;

	private HlavickaMapy(final int verze, final long velikostSouboru, final Instant datumDat, final double jih, final double zapad, final double sever,
			final double vychod) {
		this.verze = verze;
		this.velikostSouboru = velikostSouboru;
		this.datumDat = datumDat;
		this.jih = jih;
		this.zapad = zapad;
		this.sever = sever;
		this.vychod = vychod;
	}

	/** Přečte hlavičku a ověří, že velikost v ní sedí se skutečnou velikostí souboru. */
	public static HlavickaMapy precti(final File soubor) throws IOException {
		final HlavickaMapy h;
		try (InputStream in = Files.newInputStream(soubor.toPath())) {
			h = precti(in);
		}
		final long skutecna = soubor.length();
		if (skutecna != h.velikostSouboru) {
			throw new IOException("Mapa " + soubor.getName() + " je neúplná: má " + skutecna + " B, podle hlavičky " + h.velikostSouboru + " B");
		}
		return h;
	}

	/** Přečte hlavičku z proudu (bez kontroly skutečné velikosti). */
	public static HlavickaMapy precti(final InputStream proud) throws IOException {
		final DataInputStream in = new DataInputStream(proud);
		try {
			final byte[] magie = new byte[MAGIE.length];
			in.readFully(magie);
			for (int i = 0; i < MAGIE.length; i++) {
				if (magie[i] != MAGIE[i]) {
					throw new IOException("Soubor není mapa mapsforge");
				}
			}
			final int delkaHlavicky = in.readInt();
			if (delkaHlavicky < NEJMENSI_HLAVICKA || delkaHlavicky > NEJVETSI_HLAVICKA) {
				throw new IOException("Mapa má neplatnou délku hlavičky " + delkaHlavicky);
			}
			final int verze = in.readInt();
			if (verze < 3 || verze > 5) {
				throw new IOException("Nepodporovaná verze mapy " + verze);
			}
			final long velikost = in.readLong();
			final long datum = in.readLong();
			final double jih = in.readInt() / 1e6;
			final double zapad = in.readInt() / 1e6;
			final double sever = in.readInt() / 1e6;
			final double vychod = in.readInt() / 1e6;
			if (velikost < 24L + delkaHlavicky || datum < 0 || !(jih >= -90 && jih < sever && sever <= 90) || !(zapad >= -180 && zapad < vychod && vychod <= 180)) {
				throw new IOException("Mapa má neplatnou hlavičku");
			}
			return new HlavickaMapy(verze, velikost, Instant.ofEpochMilli(datum), jih, zapad, sever, vychod);
		} catch (final EOFException e) {
			throw new IOException("Mapa je kratší než její hlavička", e);
		}
	}

	public boolean obsahuje(final double lat, final double lon) {
		return lat >= jih && lat <= sever && lon >= zapad && lon <= vychod;
	}

	public int getVerze() {
		return verze;
	}

	public long getVelikostSouboru() {
		return velikostSouboru;
	}

	public Instant getDatumDat() {
		return datumDat;
	}

	public double getJih() {
		return jih;
	}

	public double getZapad() {
		return zapad;
	}

	public double getSever() {
		return sever;
	}

	public double getVychod() {
		return vychod;
	}
}
