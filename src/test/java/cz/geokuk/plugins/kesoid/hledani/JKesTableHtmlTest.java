package cz.geokuk.plugins.kesoid.hledani;

import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.kesoid.Wpt;
import cz.geokuk.plugins.kesoid.kind.kes.Kes;

/** Výsledky hledání ukážou text z GPX jako text i ve sloupcích bez shody. */
public class JKesTableHtmlTest {

	@Test
	public void sloupecBezShodyNeniHtml() {
		final Kes kes = new Kes();
		kes.setIdentifier("<html><img src=http://sledovac/kod>");
		kes.setAuthor("<html><img src=http://sledovac/autor>");
		final Wpt wpt = new Wpt();
		wpt.setName("GC1");
		wpt.setNazev("<html><img src=http://sledovac/nazev>");
		wpt.setSym("Geocache|Traditional Cache");
		kes.addWpt(wpt);
		final Nalezenec nal = new Nalezenec();
		nal.setKes(kes);
		nal.setKdeNalezeno("jinde");
		final JKesTable tabulka = new JKesTable();
		tabulka.setKeslist(Collections.singletonList(nal));
		for (final int sloupec : new int[] { 3, 4, 5 }) {
			final String hodnota = String.valueOf(tabulka.tableModel.getValueAt(0, sloupec));
			Assert.assertFalse(hodnota, hodnota.contains("<img"));
			Assert.assertTrue(hodnota, hodnota.startsWith("<html>&lt;html&gt;"));
		}
	}
}
