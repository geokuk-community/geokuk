package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.image.BufferedImage;

import javax.swing.JPanel;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.napoveda.Diagnostika;
import cz.geokuk.core.napoveda.ZobrazeneDlazdice;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;
import cz.geokuk.plugins.mapy.kachle.data.Ka;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;
import cz.geokuk.plugins.mapy.kachle.podklady.KachloStav;
import cz.geokuk.plugins.mapy.kachle.podklady.Priority;

/** Počítadlo zobrazených dlaždic: počítá se konečný obrázek v komponentě jednou, ne náhled, chyba, dávkové stahování ani překreslení. */
public class ZobrazeneDlazdiceTest {

	private static final KaLoc LOC = KaLoc.ofJZ(new Mou(0x23800000, 0x28100000), 13);

	@Before
	public void vynuluj() {
		ZobrazeneDlazdice.vynuluj();
	}

	@After
	public void uklid() {
		ZobrazeneDlazdice.vynuluj();
	}

	private static KachloStav obrazek() {
		return new KachloStav(new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB));
	}

	/** Dlaždice umístěná v rodiči, tedy viditelná. */
	private static JKachle viditelna(final EKaType typ) {
		final JKachle k = new JKachle(null, new Ka(LOC, typ));
		final JPanel rodic = new JPanel(null);
		rodic.setSize(512, 512);
		rodic.add(k);
		k.setLocation(10, 10);
		return k;
	}

	@Test
	public void hotovyObrazekViditelneDlazdiceSeZapocteJednou() {
		final JKachle k = viditelna(EKaType.BASE_M);
		k.prijmi(obrazek(), Priority.KACHLE);
		Assert.assertEquals(1, ZobrazeneDlazdice.celkem());
		Assert.assertEquals(1, ZobrazeneDlazdice.pocet(EKaType.BASE_M.name()));
		final BufferedImage platno = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
		for (int i = 0; i < 5; i++) {
			k.paint(platno.getGraphics());
		}
		k.prijmi(obrazek(), Priority.KACHLE);
		Assert.assertEquals("překreslení ani opakované dodání téže komponenty se nepočítá", 1, ZobrazeneDlazdice.celkem());
	}

	@Test
	public void novaKomponentaProStejnouDlazdiciSeZapocteZnovu() {
		viditelna(EKaType.BASE_M).prijmi(obrazek(), Priority.KACHLE);
		viditelna(EKaType.BASE_M).prijmi(obrazek(), Priority.KACHLE);
		Assert.assertEquals(2, ZobrazeneDlazdice.celkem());
	}

	@Test
	public void dlazdiceMimoViditelnouOblastSeZapocteAzPriPrvnimKresleni() {
		final JKachle k = new JKachle(null, new Ka(LOC, EKaType.TURIST_M));
		k.prijmi(obrazek(), Priority.KACHLE);
		Assert.assertEquals("bez rodiče není vidět", 0, ZobrazeneDlazdice.celkem());
		final BufferedImage platno = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
		k.paint(platno.getGraphics());
		k.paint(platno.getGraphics());
		Assert.assertEquals(1, ZobrazeneDlazdice.celkem());
		Assert.assertEquals(1, ZobrazeneDlazdice.pocet(EKaType.TURIST_M.name()));
	}

	@Test
	public void chybaDavkoveStahovaniAKresleniBezObrazkuSeNepocita() {
		final JKachle chyba = viditelna(EKaType.BASE_M);
		chyba.prijmi(new KachloStav(new RuntimeException("chyba")), Priority.KACHLE);
		viditelna(EKaType.BASE_M).prijmi(obrazek(), Priority.STAHOVANI);
		final JKachle prazdna = viditelna(EKaType.BASE_M);
		prazdna.paint(new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB).getGraphics());
		Assert.assertEquals(0, ZobrazeneDlazdice.celkem());
	}

	@Test
	public void pocitaSeZvlastProKazdyZdrojAVTextuDiagnostiky() {
		viditelna(EKaType.BASE_M).prijmi(obrazek(), Priority.KACHLE);
		viditelna(EKaType.BASE_M).prijmi(obrazek(), Priority.KACHLE);
		viditelna(EKaType.TURIST_M).prijmi(obrazek(), Priority.KACHLE);
		Assert.assertEquals(3, ZobrazeneDlazdice.celkem());
		Assert.assertEquals(2, ZobrazeneDlazdice.pocet(EKaType.BASE_M.name()));
		Assert.assertEquals(1, ZobrazeneDlazdice.pocet(EKaType.TURIST_M.name()));
		final String text = Diagnostika.text();
		Assert.assertTrue(text, text.contains("Zobrazených dlaždic od spuštění: 3 (odhad kreditů při načítání z API: 3)"));
		Assert.assertTrue(text, text.contains("  " + EKaType.BASE_M.getNazev() + ": 2"));
		Assert.assertTrue(text, text.contains("  " + EKaType.TURIST_M.getNazev() + ": 1"));
	}
}
