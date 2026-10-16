package cz.geokuk.plugins.mapy.pruvodce;

import java.util.List;

import cz.geokuk.plugins.mapy.pruvodce.PlanPruvodce.Polozka;
import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.MatriceMap;

/** Co průvodce potřebuje od programu: katalog, aktuální stavy, stahování a přepnutí zobrazení. Odděluje průvodce od modelů, aby šel zkoušet bez nich. */
public interface PruvodceKontext {

	KatalogMap getKatalog();

	/** Aktuální matice stavů map a vrstev. */
	List<MatriceMap.Radek> matice();

	/** Volné místo na disku se složkou offline map v bajtech, -1 když není známé. */
	long volneMisto();

	/** Spustí stažení položky (mapy nebo vrstvy). Nic jiného se v tu chvíli nesmí stahovat. */
	void stahni(Polozka polozka);

	/** Zruší běžící stahování. */
	void zrusStahovani();

	/** Přepne zobrazení na zvolený podklad: nastaví podklad, u offline Turistickou, překryv tras u online podkladu, stínování terénu podle vrstev. */
	void prepni(PruvodceVolba volba);
}
