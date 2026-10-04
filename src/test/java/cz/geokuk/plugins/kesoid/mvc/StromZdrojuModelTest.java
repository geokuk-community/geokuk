package cz.geokuk.plugins.kesoid.mvc;

import java.io.File;
import java.util.*;

import javax.swing.event.TableModelEvent;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import cz.geokuk.plugins.kesoid.importek.InformaceOZdroji;
import cz.geokuk.util.file.*;

/** Přehled zdrojů: strom jako tabulka bez SwingX. */
public class StromZdrojuModelTest {

	private static final File KOREN = new File("/data/gpx");
	private static final Root ROOT = new Root(KOREN, new Root.Def(0, null, null));

	private final Set<KeFile> blokovane = new HashSet<>();
	private final Set<KeFile> zamcene = new HashSet<>();
	private final List<String> zmeny = new ArrayList<>();
	private StromZdrojuModel model;
	private InformaceOZdroji skrytyKoren;
	private InformaceOZdroji slozka;

	private static KeFile soubor(final String cesta) {
		return new KeFile(new FileAndTime(new File(KOREN, cesta), 0), ROOT);
	}

	private static InformaceOZdroji uzel(final InformaceOZdroji rodic, final KeFile soubor, final int wpt) {
		final InformaceOZdroji u = new InformaceOZdroji(soubor, true);
		u.pocetWaypointuCelkem = wpt;
		u.pocetWaypointuBranych = wpt;
		u.parent = rodic;
		if (rodic != null) {
			rodic.addChild(u);
		}
		return u;
	}

	@Before
	public void setUp() {
		skrytyKoren = uzel(null, new KeFile(new FileAndTime(new File("[gc]"), 0), new Root(new File("[gc]"), new Root.Def(0, null, null))), 0);
		final InformaceOZdroji gpx = uzel(skrytyKoren, new KeFile(new FileAndTime(KOREN, 0), ROOT), 0);
		slozka = uzel(gpx, soubor("avylet"), 0);
		uzel(slozka, soubor("avylet/a.gpx"), 3);
		uzel(gpx, soubor("b.gpx"), 5);
		model = new StromZdrojuModel(f -> !blokovane.contains(f), zamcene::contains, (f, nacitat) -> zmeny.add(f.getFile().getName() + "=" + nacitat));
		model.setKoren(skrytyKoren);
	}

	private List<String> radky() {
		final List<String> r = new ArrayList<>();
		for (int i = 0; i < model.getRowCount(); i++) {
			r.add(model.getRadek(i).hloubka + ":" + model.getRadek(i).uzel.jmenoZdroje.getFile().getName());
		}
		return r;
	}

	@Test
	public void korenJeSkrytyAPrvniUrovenSbalena() {
		Assert.assertEquals(Collections.singletonList("0:gpx"), radky());
		Assert.assertFalse(model.jeRozbaleny(0));
		Assert.assertTrue(model.getRadek(0).maDeti());
	}

	@Test
	public void rozbaleniASbaleni() {
		model.setRozbaleny(0, true);
		Assert.assertEquals(Arrays.asList("0:gpx", "1:avylet", "1:b.gpx"), radky());
		model.setRozbaleny(1, true);
		Assert.assertEquals(Arrays.asList("0:gpx", "1:avylet", "2:a.gpx", "1:b.gpx"), radky());
		model.setRozbaleny(0, false);
		Assert.assertEquals(Collections.singletonList("0:gpx"), radky());
		model.setRozbaleny(0, true);
		Assert.assertEquals("vnořené rozbalení zůstane", Arrays.asList("0:gpx", "1:avylet", "2:a.gpx", "1:b.gpx"), radky());
	}

	@Test
	public void listNejdeRozbalit() {
		model.setRozbaleny(0, true);
		model.setRozbaleny(2, true);
		Assert.assertFalse(model.jeRozbaleny(2));
		Assert.assertEquals(3, model.getRowCount());
	}

	@Test
	public void rozbaleniVydrziNoveNacteni() {
		model.setRozbaleny(0, true);
		model.setKoren(skrytyKoren);
		Assert.assertEquals(Arrays.asList("0:gpx", "1:avylet", "1:b.gpx"), radky());
	}

	@Test
	public void sloupce() {
		model.setRozbaleny(0, true);
		blokovane.add(soubor("b.gpx"));
		zamcene.add(soubor("avylet"));
		Assert.assertEquals(Arrays.asList("Zdroj", "Načíst", "WP braných", "WP celkem"),
				Arrays.asList(model.getColumnName(0), model.getColumnName(1), model.getColumnName(2), model.getColumnName(3)));
		Assert.assertEquals(Boolean.class, model.getColumnClass(1));
		Assert.assertEquals(Integer.class, model.getColumnClass(3));
		Assert.assertEquals("avylet – čeká na dokončení zápisu", model.getValueAt(1, 0));
		Assert.assertEquals("b.gpx", model.getValueAt(2, 0));
		Assert.assertEquals(Boolean.TRUE, model.getValueAt(1, 1));
		Assert.assertEquals(Boolean.FALSE, model.getValueAt(2, 1));
		Assert.assertEquals(5, model.getValueAt(2, 2));
		Assert.assertEquals(5, model.getValueAt(2, 3));
	}

	@Test
	public void htmlVNazvuSeZobraziJakoText() {
		final InformaceOZdroji gpx = skrytyKoren.getChildren().get(0);
		uzel(gpx, soubor("<html><b>x.gpx"), 1);
		model.setKoren(skrytyKoren);
		model.setRozbaleny(0, true);
		final String nazev = (String) model.getValueAt(1, 0);
		Assert.assertTrue(nazev, nazev.startsWith("<html>&lt;html&gt;"));
	}

	@Test
	public void jenNacistJdeMenit() {
		Assert.assertFalse(model.isCellEditable(0, 0));
		Assert.assertTrue(model.isCellEditable(0, 1));
		Assert.assertFalse(model.isCellEditable(0, 2));
		Assert.assertFalse(model.isCellEditable(0, 3));
	}

	@Test
	public void zmenaNacitaniSePredaAObnoviVsechnyRadky() {
		model.setRozbaleny(0, true);
		final List<TableModelEvent> udalosti = new ArrayList<>();
		model.addTableModelListener(udalosti::add);
		model.setValueAt(false, 1, 1);
		Assert.assertEquals(Collections.singletonList("avylet=false"), zmeny);
		Assert.assertEquals(1, udalosti.size());
		Assert.assertEquals(0, udalosti.get(0).getFirstRow());
		Assert.assertEquals(2, udalosti.get(0).getLastRow());
	}

	@Test
	public void bezKorenePrazdne() {
		model.setKoren(null);
		Assert.assertEquals(0, model.getRowCount());
	}
}
