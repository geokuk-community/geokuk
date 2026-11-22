package cz.geokuk.plugins.kesoid.mvc;

import java.io.File;

import cz.geokuk.plugins.kesoid.importek.TypZdroje;

/** Co přepínače zdrojů mění; nic z toho nečeká na načítání. */
public interface OvladaniZdroju {

	void setNacitatTyp(TypZdroje typ, boolean nacitat);

	void setNacitatVseVTypu(TypZdroje typ, boolean nacitat);

	void setNacitatVse(boolean nacitat);

	void setNacitatPolozku(File soubor, boolean nacitat);

	/** Zapne typ a z jeho položek nechá vybranou jen tuto. */
	void setNacitatJenPolozku(TypZdroje typ, File soubor);

	/** Zapne typ (bez výběru se všemi položkami) a ostatní typy vypne. */
	void setNacitatJenTyp(TypZdroje typ);

	/** Typ s volbou „Nové databáze načíst až po vybrání“: bez vybrané databáze se nezapínají všechny. */
	default boolean isNacitatAzPoVybrani(final TypZdroje typ) {
		return false;
	}

	/** Uživatel zapnul typ, který nemá vybranou žádnou databázi a všechny se zapnout nemají. */
	default void zadnaVybranaDatabaze(final TypZdroje typ) {}
}
