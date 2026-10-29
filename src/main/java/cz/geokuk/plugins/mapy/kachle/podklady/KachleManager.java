package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.Image;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

import cz.geokuk.plugins.mapy.kachle.data.Ka;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;

/**
 * Common interface for the tiles storage.
 *
 * @author Danstahr
 */
public interface KachleManager {

	public static class ItemToSave {
		public final Ka key;
		/** Typ dlaždice v úložišti, u offline mapy podle souborů map a tématu. */
		public final String typ;
		public final byte[] imageData;

		public ItemToSave(final Ka key, final byte[] imageData) {
			this(key, key.typToString(), imageData);
		}

		public ItemToSave(final Ka key, final String typ, final byte[] imageData) {
			this.key = key;
			this.typ = typ;
			this.imageData = imageData;
		}
	}

	/**
	 * Checks whether the requested tile exists in the storage.
	 *
	 * @param ki
	 *            The identifier of the tile.
	 * @return True if it can be retrieved from the storage, false otherwise
	 */
	public boolean exists(Ka ki);

	/** Dlaždice daného typu je v úložišti. */
	default boolean exists(final Ka ki, final String typ) {
		return load(ki, typ) != null;
	}

	/**
	 * Loads the requested tile from the storage.
	 *
	 * @param ki
	 *            The identifier of the tile.
	 * @return The requested tile as Image or null if the tile couldn't be loaded or isn't present in the storage.
	 */
	public Image load(Ka ki);

	/** Načte dlaždici uloženou pod zadaným typem, viz {@link ItemToSave#typ}. */
	public default Image load(final Ka ki, final String typ) {
		return load(ki);
	}

	/**
	 * Save many tiles to the storage. Should be atomic ("all or nothing")
	 *
	 * @param itemsToSave
	 *            Collection of items (identifier of the tile -> image dump)
	 * @return true if the saving of ALL items was successful, false otherwise
	 */
	public boolean save(Collection<ItemToSave> itemsToSave);

	/**
	 * Smaže dlaždice offline mapy se stejným písmenem jako zadané globální klíče (písmeno a 8 šestnáctkových číslic), kromě nich a všechny ve starém tvaru bez oblastní části; vrátí jejich počet, nebo -1, když se úklid nedokončil (cache nejde otevřít, je zaneprázdněná, mazání selhalo)
	 * a má se zopakovat. Podklad ({@code o}) a překryv ({@code t}) se tak uklízejí každý zvlášť.
	 */
	public default int smazOfflineKrome(final Set<String> ponechat) {
		return 0;
	}

	/**
	 * Smaže dlaždice offline mapy se zadaným globálním klíčem, jejichž typ není ten, který pro ně očekává aktuální sestava souborů (zastaralé po změně
	 * mapy, vrstvy nebo DEM); vrátí jejich počet, nebo -1, když se úklid nedokončil.
	 *
	 * @param ocekavany
	 *            typ dlaždice v cache pro aktuální sestavu, viz {@link OblastniKlic#klic}
	 */
	public default int smazOfflineNesedici(final String globalni, final Function<KaLoc, String> ocekavany) {
		return 0;
	}

	/** Připraví úložiště pro čtení z volajícího vlákna (třeba otevře spojení), ať první dlaždice nečeká. */
	public default void predotevri() {
	}

	/** Optimalizace databáze cache, null, když úložiště nic takového nemá. */
	public default OptimalizaceDatabaze optimalizace() {
		return null;
	}
}
