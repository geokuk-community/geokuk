package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.table.SqlJetDb;

/** Spojení s cache dlaždic; novou cache zakládá s větší stránkou, SqlJet to veřejně neumí. */
final class CacheDb extends SqlJetDb {

	/** Stránka nové cache; velké dlaždice pak neleží na dlouhých řetězech malých stránek. */
	static final int STRANKA_NOVE = 4096;

	/** Velikost stránkové paměti jednoho spojení. */
	private static final int PAMET_SPOJENI = 4 << 20;

	private CacheDb(final File f, final boolean zapis) {
		super(f, zapis);
	}

	static CacheDb otevri(final File f, final boolean zapis) throws SqlJetException {
		final boolean nova = zapis && (!f.exists() || f.length() == 0);
		final CacheDb db = new CacheDb(f, zapis);
		try {
			db.open();
			if (nova) {
				db.runSynchronized(engine -> {
					db.btree.setPageSize(STRANKA_NOVE, -1);
					return null;
				});
			}
			db.setCacheSize(Math.max(500, PAMET_SPOJENI / db.btree.getPageSize()));
			return db;
		} catch (final SqlJetException | RuntimeException e) {
			try {
				db.close();
			} catch (final SqlJetException e1) {
				e.addSuppressed(e1);
			}
			throw e;
		}
	}
}
