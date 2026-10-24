package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.GraphicsEnvironment;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import cz.geokuk.framework.Dlg;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.podklady.OptimalizaceDatabaze;
import cz.geokuk.plugins.mapy.kachle.podklady.OptimalizaceDatabaze.Doporuceni;
import cz.geokuk.plugins.mapy.kachle.podklady.StavSouboruCache;

/** Při startu doporučí optimalizaci databáze; odložení si pamatuje v nastavení. */
public final class PripominkaOptimalizace {

	/** Odložení po vypršení odpočtu. */
	static final long ODLOZENI_VYPRSENI_MS = TimeUnit.DAYS.toMillis(3);

	/** Odložení po volbě „Spustit bez optimalizace“. */
	static final long ODLOZENI_BEZ_MS = TimeUnit.DAYS.toMillis(7);

	/** Odložení u kritického stavu. */
	static final long ODLOZENI_KRITICKY_MS = TimeUnit.DAYS.toMillis(1);

	/** Kritický stav: podíl volných stránek nad tuto hranici a soubor větší než {@link OptimalizaceDatabaze#PRAH_VELIKOSTI}. */
	static final double PRAH_KRITICKY = 0.60;

	static boolean kriticky(final StavSouboruCache stav) {
		return stav != null && stav.podilVolnych() > PRAH_KRITICKY && stav.velikost() > OptimalizaceDatabaze.PRAH_VELIKOSTI;
	}

	/** Na jak dlouho odložit; {@code vyprsel} = vypršel odpočet, jinak uživatel zvolil „Spustit bez optimalizace“. */
	static long odlozeniMs(final StavSouboruCache stav, final boolean vyprsel) {
		if (kriticky(stav)) {
			return ODLOZENI_KRITICKY_MS;
		}
		return vyprsel ? ODLOZENI_VYPRSENI_MS : ODLOZENI_BEZ_MS;
	}

	/** O kolik se musí podíl volných stránek zhoršit, aby se okno ukázalo i během odložení. */
	static final double ZHORSENI = 0.15;

	/** Zda okno ukázat: cache to potřebuje a uživatel ho neodložil, nebo se podíl volných výrazně zhoršil. */
	static boolean zobrazit(final Doporuceni doporuceni, final double podil, final long nyni, final long odlozenoDo, final double podilPriOdlozeni) {
		if (doporuceni == Doporuceni.NENI) {
			return false;
		}
		return nyni >= odlozenoDo || podil >= podilPriOdlozeni + ZHORSENI;
	}

	/** Ve vlákně na pozadí: levně z hlavičky souboru, a jen když je co doporučit, otevře okno. */
	public static void zkontroluj(final KachleModel model) {
		if (GraphicsEnvironment.isHeadless()) {
			return;
		}
		final OptimalizaceDatabaze optimalizace = model.kachleManager.optimalizace();
		if (optimalizace == null) {
			return;
		}
		final StavSouboruCache stav = optimalizace.stav();
		final long volne = optimalizace.volneMisto();
		final Doporuceni doporuceni = OptimalizaceDatabaze.posud(stav, volne);
		if (!zobrazit(doporuceni, stav == null ? 0 : stav.podilVolnych(), System.currentTimeMillis(), model.getOptimalizaceOdlozenaDo(), model.getOptimalizacePodilPriOdlozeni())) {
			return;
		}
		SwingUtilities.invokeLater(() -> OknoDoporuceniOptimalizace.zobraz(Dlg.parentFrame(), stav, doporuceni, volne, new OknoDoporuceniOptimalizace.Volba() {
			@Override
			public void optimalizovat() {
				DialogOptimalizace.zobraz(Dlg.parentFrame(), optimalizace, true);
			}

			@Override
			public void bezOptimalizace() {
				model.odlozOptimalizaci(System.currentTimeMillis() + odlozeniMs(stav, false), stav.podilVolnych());
			}

			@Override
			public void vyprseni() {
				model.odlozOptimalizaci(System.currentTimeMillis() + odlozeniMs(stav, true), stav.podilVolnych());
			}
		}));
	}

	private PripominkaOptimalizace() {}
}
