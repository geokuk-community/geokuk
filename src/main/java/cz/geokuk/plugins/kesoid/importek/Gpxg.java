/**
 *
 */
package cz.geokuk.plugins.kesoid.importek;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Martin Veverka
 *
 */
public class Gpxg {

	public int elevation;
	public String found;
	public int flag;
	/** Hodnota, která ve zdroji není uvedená. */
	public static final int NEUVEDENO = -1;

	public int hodnoceni = NEUVEDENO;
	public int hodnoceniPocet = NEUVEDENO;
	public int bestOf = NEUVEDENO;
	public int favorites = NEUVEDENO;
	public int znamka = NEUVEDENO;
	public String czkraj;
	public String czokres;
	public Map<String, String> userTags = new HashMap<>();

	public void putUserTag(final String genname, final String alelaname) {
		userTags.put(genname, alelaname);
	}

}
