/**
 *
 */
package cz.geokuk.plugins.kesoid.mvc;

import java.io.File;
import java.util.Objects;

import cz.geokuk.core.program.FConst;
import cz.geokuk.core.program.UmisteniSouboru0;
import cz.geokuk.util.file.Filex;

/**
 * @author Martin Veverka
 *
 */
public class KesoidUmisteniSouboru extends UmisteniSouboru0 {

	/** Výchozí složka s keškami z Geogetu nebo jiného programu (GPX). */
	public static final Filex KES_DIR = new Filex(new File(FConst.DATA_DIR, "gpx"), false, true);
	public static final Filex CESTY_DIR = new Filex(new File(FConst.DATA_DIR, "cesty"), false, true);
	public static final Filex GEOGET_DATA_DIR = new Filex(new File("C:\\geoget\\data"), false, false);
	public static final Filex GSAK_DATA_DIR = new Filex(new File(FConst.HOME_DIR, "AppData/Roaming/gsak/data"), false, false);
	public static final Filex OPENSAK_DATA_DIR = new Filex(vychoziSlozkaOpensaku(System.getProperty("os.name", ""), FConst.HOME_DIR, System.getenv("APPDATA")), false, false);

	public static final File IKONY_DIR = new File(FConst.DATA_DIR, "ikony");
	public static final Filex IMAGE_3RDPARTY_DIR = new Filex(new File(IKONY_DIR, "ostatni"), false, true);
	public static final Filex IMAGE_MY_DIR = new Filex(new File(IKONY_DIR, "moje"), false, true);

	public static final File VYLETY_DIR = new File(FConst.DATA_DIR, "vylety");
	public static final Filex ANO_GGT = new Filex(new File(VYLETY_DIR, "lovim.ggt"), false, true);
	public static final Filex NE_GGT = new Filex(new File(VYLETY_DIR, "tedne.ggt"), false, true);

	private Filex kesDir;
	private Filex cestyDir;

	private Filex geogetDataDir;
	private Filex gsakDataDir;
	private Filex opensakDataDir;
	private Filex image3rdPartyDir;
	private Filex imageMyDir;

	private Filex neGgtFile;
	private Filex anoGgtFile;

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final KesoidUmisteniSouboru that = (KesoidUmisteniSouboru) o;
		return equalsDataLocations(that) && equalsImageLocations(that) && equalsGgtLocations(that) && equalsPathLocations(that);
	}

	/** Porovná, zda se shodují umístění datových složek. */
	public boolean equalsDataLocations(final KesoidUmisteniSouboru that) {
		if (!Objects.equals(kesDir, that == null ? null : that.kesDir)) {
			return false;
		}
		if (!Objects.equals(geogetDataDir, that == null ? null : that.geogetDataDir)) {
			return false;
		}
		if (!Objects.equals(gsakDataDir, that == null ? null : that.gsakDataDir)) {
			return false;
		}
		if (!Objects.equals(opensakDataDir, that == null ? null : that.opensakDataDir)) {
			return false;
		}
		return true;
	}

	/** Porovná, zda se shodují umístění složek s obrázky (ikonami). */
	public boolean equalsImageLocations(final KesoidUmisteniSouboru that) {
		if (!Objects.equals(image3rdPartyDir, that == null ? null : that.image3rdPartyDir)) {
			return false;
		}
		if (!Objects.equals(imageMyDir, that == null ? null : that.imageMyDir)) {
			return false;
		}
		return true;
	}

	/** Porovná, zda se shodují umístění dat výletů. */
	public boolean equalsGgtLocations(final KesoidUmisteniSouboru that) {
		if (!Objects.equals(anoGgtFile, that == null ? null : that.anoGgtFile)) {
			return false;
		}
		if (!Objects.equals(neGgtFile, that == null ? null : that.neGgtFile)) {
			return false;
		}
		return true;
	}

	/** Porovná, zda se shodují umístění složek s cestami. */
	public boolean equalsPathLocations(final KesoidUmisteniSouboru that) {
		if (!Objects.equals(cestyDir, that == null ? null : that.cestyDir)) {
			return false;
		}
		return true;
	}

	/**
	 * @return the neGgtFile
	 */
	public Filex getAnoGgtFile() {
		check(anoGgtFile);
		return anoGgtFile;
	}

	public Filex getCestyDir() {
		check(cestyDir);
		return cestyDir;
	}

	public Filex getGeogetDataDir() {
		return geogetDataDir;
	}

	public Filex getGsakDataDir() {
		return gsakDataDir;
	}

	public Filex getOpensakDataDir() {
		return opensakDataDir;
	}

	public Filex getImage3rdPartyDir() {
		return image3rdPartyDir;
	}

	public Filex getImageMyDir() {
		return imageMyDir;
	}

	/**
	 * @return the kesDir
	 */
	public Filex getKesDir() {
		check(kesDir);
		return kesDir;
	}

	/**
	 * @return the neGgtFile
	 */
	public Filex getNeGgtFile() {
		check(neGgtFile);
		return neGgtFile;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (geogetDataDir == null ? 0 : geogetDataDir.hashCode());
		result = prime * result + (image3rdPartyDir == null ? 0 : image3rdPartyDir.hashCode());
		result = prime * result + (imageMyDir == null ? 0 : imageMyDir.hashCode());
		result = prime * result + (kesDir == null ? 0 : kesDir.hashCode());
		result = prime * result + (cestyDir == null ? 0 : cestyDir.hashCode());
		result = prime * result + (neGgtFile == null ? 0 : neGgtFile.hashCode());
		result = prime * result + (anoGgtFile == null ? 0 : anoGgtFile.hashCode());
		return result;
	}

	/**
	 * @param neGgtFile
	 *            the neGgtFile to set
	 */
	public void setAnoGgtFile(final Filex anoGgtFile) {
		this.anoGgtFile = anoGgtFile;
	}

	public void setCestyDir(final Filex cestyDir) {
		this.cestyDir = cestyDir;
	}

	public void setGeogetDataDir(final Filex geogetDir) {
		geogetDataDir = geogetDir;
	}

	public void setGsakDataDir(final Filex gsakDir) {
		gsakDataDir = gsakDir;
	}

	public void setOpensakDataDir(final Filex opensakDir) {
		opensakDataDir = opensakDir;
	}

	public void setImage3rdPartyDir(final Filex image3rdPartyDir) {
		this.image3rdPartyDir = image3rdPartyDir;
	}

	public void setImageMyDir(final Filex imageMyDir) {
		this.imageMyDir = imageMyDir;
	}

	/**
	 * @param kesDir
	 *            the kesDir to set
	 */
	public void setKesDir(final Filex kesDir) {
		this.kesDir = kesDir;
	}

	/**
	 * @param neGgtFile
	 *            the neGgtFile to set
	 */
	public void setNeGgtFile(final Filex neGgtFile) {
		this.neGgtFile = neGgtFile;
	}

	/** Instalace z Microsoft Store má data v Dokumentech, ostatní ve složce AppData. */
	static File vychoziSlozkaOpensaku(final String os, final File home, final String appData) {
		if (os.startsWith("Windows")) {
			final File dokumenty = new File(home, "Documents/opensak");
			if (dokumenty.isDirectory()) {
				return dokumenty;
			}
			return appData == null ? new File(home, "AppData/Roaming/opensak") : new File(appData, "opensak");
		}
		if (os.startsWith("Mac")) {
			return new File(home, "Library/Application Support/opensak");
		}
		return new File(home, ".local/share/opensak");
	}

	/**
	 * @param aKesDir
	 */
	private void check(final Filex file) {
		if (file == null) {
			throw new RuntimeException("Jmena souboru jeste nebyla inicializovana.");
		}
	}
}
