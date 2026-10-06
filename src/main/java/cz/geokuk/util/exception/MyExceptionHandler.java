/**
 *
 */
package cz.geokuk.util.exception;

import java.lang.Thread.UncaughtExceptionHandler;

import javax.swing.JOptionPane;

import cz.geokuk.core.program.PametProgramuAction;

/**
 * @author Martin Veverka
 *
 */
public class MyExceptionHandler implements UncaughtExceptionHandler {

	@SuppressWarnings("unused") // to je jen špunt, aby se daly zobrazot okna
	private static byte[] spunt = new byte[500000];

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Thread.UncaughtExceptionHandler#uncaughtException(java.lang.Thread, java.lang.Throwable)
	 */
	@Override
	public void uncaughtException(final Thread vlakno, final Throwable t) {
		try {
			final OutOfMemoryError oome = najdiOom(t);
			if (oome != null) {
				zpracujMaloPameti(oome);
			}
			final AExcId excId = FExceptionDumper.dump(t, EExceptionSeverity.DISPLAY, "Neošetřená chyba ve vlákně " + vlakno.getName());
			System.err.println("Exception: " + excId);
		} catch (final Throwable tt) {
			// Tak když výjimku nešlo ani vypsat
			t.printStackTrace();
			tt.printStackTrace();
		}
	}

	/** Došlá paměť i tehdy, když ji vlákno na pozadí zabalilo do jiné výjimky. */
	public static OutOfMemoryError najdiOom(final Throwable t) {
		// Bez alokace, paměť už došla; limit hloubky chrání před zacyklenými příčinami.
		int hloubka = 0;
		for (Throwable x = t; x != null && hloubka < 100; x = x.getCause(), hloubka++) {
			if (x instanceof OutOfMemoryError) {
				return (OutOfMemoryError) x;
			}
		}
		return null;
	}

	private void zpracujMaloPameti(final OutOfMemoryError oome) {
		System.err.println("Málo paměti!");
		final Runtime runtime = Runtime.getRuntime();
		final long freeMemory = runtime.freeMemory() / 1024;
		final long totalMemory = runtime.totalMemory() / 1024;
		spunt = null; // uvolníme špunt, čímž umožníme ještě zobrazit okno a ukončit program
		final AExcId excId = FExceptionDumper.dump(oome, EExceptionSeverity.DISPLAY, "Nedostatek paměti");
		System.err.println("Exception: " + excId);
		System.err.println("Paměť: total=" + totalMemory + " KiB, free=" + freeMemory + " KiB");
		JOptionPane.showMessageDialog(null, "Programu došla paměť, GeoKuk se ukončí.\n" + PametProgramuAction.jakZvysitPamet() + "\n\nHlášení chyby: " + excId, "GeoKuk",
		        JOptionPane.ERROR_MESSAGE);
		System.exit(1);
	}

}
