package cz.geokuk.plugins.kesoid.hledani;

import static org.junit.Assert.*;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableColumnModel;

import org.junit.Assume;
import org.junit.Test;

import cz.geokuk.framework.JMyDialog0;

/** Sloupce tabulky výsledků hledání se neořezávají: krátké mají pevnou šířku podle písma, Id se vejde i zastávka Adventure Lab. */
public class JKesTableSloupceTest {

	private static FontMetrics metrika(final float velikost, final int styl) {
		return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics().getFontMetrics(new Font(Font.DIALOG, styl, 1).deriveFont(velikost));
	}

	@Test
	public void sirkaSloupceVezmeNejsirsiZHlavickyHodnotyAIkony() {
		final FontMetrics m = metrika(12, Font.PLAIN);
		final int hlavicka = m.stringWidth("Vzdálenost");
		final int hodnota = m.stringWidth("9999.9");
		assertEquals(Math.max(hlavicka, hodnota) + JKesTable.PODLOZENI, JKesTable.sirkaSloupce(m, "Vzdálenost", m, "9999.9", 0));
		assertEquals("záhlaví širší než hodnota", hlavicka + JKesTable.PODLOZENI, JKesTable.sirkaSloupce(m, "Vzdálenost", m, "1", 0));
		assertEquals("hodnota širší než záhlaví", hodnota + JKesTable.PODLOZENI, JKesTable.sirkaSloupce(m, "Id", m, "9999.9", 0));
		assertEquals("ikona širší než záhlaví", JKesTable.SIRKA_IKONY + JKesTable.PODLOZENI, JKesTable.sirkaSloupce(m, "T", m, null, JKesTable.SIRKA_IKONY));
	}

	@Test
	public void sirkaRosteSPismemPriZvetseniObrazovky() {
		final int sirka100 = JKesTable.sirkaSloupce(metrika(12, Font.PLAIN), "Azimut", metrika(12, Font.BOLD), JKesTable.VZOREK_ID, 0);
		final int sirka150 = JKesTable.sirkaSloupce(metrika(18, Font.PLAIN), "Azimut", metrika(18, Font.BOLD), JKesTable.VZOREK_ID, 0);
		assertTrue(sirka100 + " < " + sirka150, sirka150 > sirka100);
		assertTrue("vejde se nejdelší tvar identifikátoru", sirka100 >= metrika(12, Font.BOLD).stringWidth("GC5PGVC-3") + JKesTable.PODLOZENI);
	}

	@Test
	public void sloupceMajiMinimalniSirkuPodlePisma() {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final JTable vzor = new JTable();
		final FontMetrics bunky = vzor.getFontMetrics(vzor.getFont());
		final FontMetrics tucne = vzor.getFontMetrics(vzor.getFont().deriveFont(Font.BOLD));
		final FontMetrics zahlavi = vzor.getTableHeader().getFontMetrics(vzor.getTableHeader().getFont());
		final TableColumnModel sloupce = new JKesTable().getColumnModel();
		assertTrue(sloupce.getColumn(0).getMinWidth() >= zahlavi.stringWidth("Typ") + JKesTable.PODLOZENI);
		assertTrue("záhlaví Vzdálenost se nesmí ořezat", sloupce.getColumn(1).getMinWidth() >= zahlavi.stringWidth("Vzdálenost") + JKesTable.PODLOZENI);
		assertTrue(sloupce.getColumn(1).getMinWidth() >= bunky.stringWidth("9999.9") + JKesTable.PODLOZENI);
		assertTrue(sloupce.getColumn(2).getMinWidth() >= zahlavi.stringWidth("Azimut") + JKesTable.PODLOZENI);
		assertTrue("vejde se zastávka Adventure Lab", sloupce.getColumn(3).getMinWidth() >= tucne.stringWidth("GC5PGVC-3") + JKesTable.PODLOZENI);
		assertTrue(sloupce.getColumn(3).getMinWidth() >= tucne.stringWidth(JKesTable.VZOREK_ID) + JKesTable.PODLOZENI);
		for (int i = 0; i < 3; i++) {
			assertEquals("krátký sloupec má pevnou šířku", sloupce.getColumn(i).getMinWidth(), sloupce.getColumn(i).getMaxWidth());
		}
	}

	@Test
	public void priZmenseniOknaSeZuziJenJmenoAAutor() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final JKesTable tabulka = new JKesTable();
		final JFrame okno = new JFrame();
		try {
			SwingUtilities.invokeAndWait(() -> {
				okno.add(tabulka);
				okno.pack();
				okno.setSize(new Dimension(1100, 400));
				okno.validate();
			});
			final TableColumnModel sloupce = tabulka.getColumnModel();
			final int[] siroke = new int[6];
			SwingUtilities.invokeAndWait(() -> {
				for (int i = 0; i < 6; i++) {
					siroke[i] = sloupce.getColumn(i).getWidth();
				}
			});
			final int[] uzke = new int[6];
			SwingUtilities.invokeAndWait(() -> {
				okno.setSize(new Dimension(520, 400));
				okno.validate();
				for (int i = 0; i < 6; i++) {
					uzke[i] = sloupce.getColumn(i).getWidth();
				}
			});
			for (int i = 0; i < 4; i++) {
				assertTrue("sloupec " + i + " se nesmí zúžit pod minimum: " + uzke[i], uzke[i] >= sloupce.getColumn(i).getMinWidth());
			}
			for (int i = 0; i < 3; i++) {
				assertEquals("krátký sloupec " + i + " nemění šířku", siroke[i], uzke[i]);
			}
			assertTrue("Jméno se zúžilo", uzke[4] < siroke[4]);
			assertTrue("Jméno nepadne pod minimum", uzke[4] >= sloupce.getColumn(4).getMinWidth());
		} finally {
			SwingUtilities.invokeAndWait(okno::dispose);
		}
	}

	@Test
	public void velikostOknaSeOmeziNaPracovniPlochu() {
		final Rectangle plocha = new Rectangle(0, 0, 1920, 1080);
		assertEquals(new Dimension(950, 700), JMyDialog0.omezNaPlochu(new Dimension(950, 700), plocha));
		assertEquals(new Dimension(720, 540), JMyDialog0.omezNaPlochu(new Dimension(950, 700), new Rectangle(0, 0, 800, 600)));
		assertEquals(new Dimension(950, 450), JMyDialog0.omezNaPlochu(new Dimension(950, 700), new Rectangle(0, 0, 1920, 500)));
		assertEquals(new Dimension(950, 700), JTextoveHledaniDialog.VYCHOZI_VELIKOST);
	}
}
