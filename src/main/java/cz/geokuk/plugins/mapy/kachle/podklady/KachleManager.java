package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.Image;
import java.util.Collection;
import java.util.Set;

import cz.geokuk.plugins.mapy.kachle.data.Ka;

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

	/** Smaže dlaždice offline mapy (typ {@code o} a 8 šestnáctkových číslic) kromě zadaných typů; vrátí jejich počet. */
	public default int smazOfflineKrome(final Set<String> ponechat) {
		return 0;
	}
}
