package cz.geokuk.plugins.mapy.sprava.gui;

/** Akce z popupu Mapy; volá se na EDT, práce se sítí a diskem běží jinde. */
public interface OvladaniMap {

	default void stahni(final String id) {}

	/** Stáhne vrstvu (trasy, vrstevnice, stínování) podle id z katalogu vrstev. */
	default void stahniVrstvu(final String id) {}

	/** Po potvrzení smaže soubory vrstvy (včetně kontrolních součtů). */
	default void smazVrstvu(final String id) {}

	/** Po potvrzení smaže regiony, nebo celou zemi, která je ve složce zároveň s regiony; id je id celé země. */
	default void smazDvojite(final String idCelku, final boolean regiony) {}

	/** Otevře souhrn „Stáhnout vše“ k zemi: klíče řádků země (celek a regiony), celekKlic je null, když země má jen regiony. */
	default void stahniVse(final String nazevZeme, final String celekKlic, final java.util.List<String> klice) {}
	/** Otevře souhrn „Dostáhnout vrstvy“ ke všem staženým základním mapám (jen vrstvy z GitHubu). */
	default void dostahnoutVrstvy() {}
	default void zrusStahovani() {}

	/** Zjistí jen u této mapy, jestli je na serveru novější. */
	default void zkontroluj(final String id) {}

	/** Zjistí, jestli je na serveru novější seznam vrstev. */
	default void zkontrolujKatalogVrstev() {}

	void otevriSlozku();

	/** Obrys pokrytí map jde kreslit, jen když jsou známé hranice map. */
	default boolean isObrysDostupny() {
		return false;
	}

	default void setObrysPokryti(final boolean zapnuto) {}

	default boolean isObrysPokryti() {
		return false;
	}

	/** Myš je nad řádkem mapy (klíč řádku), nebo z něj odešla (null). */
	default void zvyrazniMapu(final String klic) {}

	/** Ve složce vrstev jsou DEM dlaždice, takže má smysl nabídnout přepínač stínování terénu. */
	default boolean isStinovaniDostupne() {
		return false;
	}

	default boolean isStinovani() {
		return true;
	}

	default void setStinovani(final boolean zapnuto) {}
}
