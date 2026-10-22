package cz.geokuk.api.mapicon;

import java.awt.Color;
import java.net.URL;
import java.util.Deque;
import java.util.Properties;

import cz.geokuk.plugins.kesoid.mapicon.IkonDrawingProperties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class Drawer0 {

	private IkonDrawingProperties idp;

	/**
	 * Vykreslí zdrojový obrázek do dané vrstvy. Obrázek přidá do seznamu,
	 *
	 */
	public abstract void draw(Deque<Imagant> imaganti);

	/**
	 * @param aIdp
	 *            the idp to set
	 */
	public void setIdp(final IkonDrawingProperties aIdp) {
		idp = aIdp;
	}

	protected final Color getColor(final String propname, final Color def) {
		final int[] ii = getInts(propname, null);
		if (ii == null || ii.length == 0) {
			return def;
		}
		if (ii.length < 3) {
			return new Color(ii[0], ii[0], ii[0]);
		} else if (ii.length == 3) {
			return new Color(ii[0], ii[1], ii[2]);
		} else {
			return new Color(ii[0], ii[1], ii[2], ii[3]);
		}
	}

	protected final float getFloat(final String propname, final float def) {
		try {
			return Float.parseFloat(getString(propname, def + ""));
		} catch (final NumberFormatException e) {
			return neplatne(propname, def);
		}
	}

	protected final float[] getFloats(final String propname, final float[] def) {
		final String[] ss = getStrings(propname, null);
		if (ss == null) {
			return def;
		}
		final float[] ff = new float[ss.length];
		try {
			for (int i = 0; i < ss.length; i++) {
				ff[i] = Float.parseFloat(ss[i]);
			}
		} catch (final NumberFormatException e) {
			return neplatne(propname, def);
		}
		return ff;
	}

	protected final int getInt(final String propname, final int def) {
		try {
			return Integer.parseInt(getString(propname, def + "").trim());
		} catch (final NumberFormatException e) {
			return neplatne(propname, def);
		}
	}

	protected final int[] getInts(final String propname, final int[] def) {
		final String[] ss = getStrings(propname, null);
		if (ss == null) {
			return def;
		}
		final int[] ii = new int[ss.length];
		try {
			for (int i = 0; i < ss.length; i++) {
				ii[i] = Integer.parseInt(ss[i].trim());
			}
		} catch (final NumberFormatException e) {
			return neplatne(propname, def);
		}
		return ii;
	}

	/** Nesmyslná hodnota v nastavení sady ikon nesmí shodit kreslení keší; kreslí se s výchozí. */
	private <T> T neplatne(final String propname, final T def) {
		log.warn("Sada ikon: neplatná hodnota {}={}, použije se výchozí", propname, getString(propname, null));
		return def;
	}

	protected final Properties getProperties() {
		return idp.properties;
	}

	protected final String getString(final String propname, final String def) {
		final String s = (String) idp.properties.get(propname);
		return s == null ? def : s;
	}

	protected final String[] getStrings(final String propname, final String[] def) {
		final String s = getString(propname, null);
		if (s == null) {
			return def;
		}
		final String[] ss = s.split(" *, *");
		return ss;
	}

	/**
	 * @return
	 */
	protected final URL getUrl() {
		return idp.url;
	}

	/**
	 * @return
	 */
	protected final int getXoffset() {
		return idp.xoffset;
	}

	/**
	 * @return
	 */
	protected final int getYoffset() {
		return idp.yoffset;
	}

}
