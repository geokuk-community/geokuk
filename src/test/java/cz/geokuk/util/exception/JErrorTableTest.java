package cz.geokuk.util.exception;

import org.junit.Assert;
import org.junit.Test;

/** Popis problému z dat se v tabulce nevykreslí jako HTML. */
public class JErrorTableTest {

	@Test
	public void popisSHtmlSeNevykresli() {
		final JErrorTable tabulka = new JErrorTable();
		tabulka.addProblem("<html><img src=http://example.invalid/x>", null);
		tabulka.addProblem("Soubor nejde číst", null);
		final Object html = tabulka.tableModel.getValueAt(0, 2);
		Assert.assertFalse(String.valueOf(html), String.valueOf(html).contains("<img"));
		Assert.assertEquals("Soubor nejde číst", tabulka.tableModel.getValueAt(1, 2));
	}
}
