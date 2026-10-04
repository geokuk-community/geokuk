package cz.geokuk.plugins.geocoding;

import java.lang.reflect.Field;
import java.util.Collections;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.plaf.basic.BasicHTML;

import org.junit.Assert;
import org.junit.Test;

/** Adresa z Nominatimu, která začíná {@code <html>}, se v tabulce výsledků ukáže jako text. */
public class JAdrTableHtmlTest {

	@Test
	public void adresaZNominatimuNeniHtml() throws Exception {
		final JAdrTable tabulka = new JAdrTable();
		final Nalezenec n = new Nalezenec();
		n.adresa = "<html><img src=http://sledovac/x.png>";
		n.locationType = "<html><img src=http://sledovac/y.png>";
		tabulka.setNalezenci(Collections.singletonList(n));
		final Field f = JAdrTable.class.getDeclaredField("table");
		f.setAccessible(true);
		final JTable table = (JTable) f.get(tabulka);
		for (final int sloupec : new int[] { 0, 3 }) {
			final JLabel popisek = (JLabel) table.prepareRenderer(table.getCellRenderer(0, sloupec), 0, sloupec);
			Assert.assertFalse(popisek.getText(), popisek.getClientProperty(BasicHTML.propertyKey) != null && popisek.getText().contains("<img"));
		}
	}
}
