package cz.geokuk.plugins.mapy.sprava.gui;

/** Akce z popupu Mapy; volá se na EDT, práce se sítí a diskem běží jinde. */
public interface OvladaniMap {

	default void stahni(final String id) {}

	default void zrusStahovani() {}

	/** Zjistí jen u této mapy, jestli je na serveru novější. */
	default void zkontroluj(final String id) {}

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
}
