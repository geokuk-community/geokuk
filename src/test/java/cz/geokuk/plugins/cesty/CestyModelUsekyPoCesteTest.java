package cz.geokuk.plugins.cesty;

import static org.junit.Assert.*;

import java.io.File;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.coord.PoziceModel;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.core.program.FPref;
import cz.geokuk.framework.EventFirer;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.plugins.cesty.data.*;
import cz.geokuk.plugins.cesty.routovani.Trasa;

/** Typ nových úseků a hledání tras úseků po cestě přes {@link CestyModel}. */
public class CestyModelUsekyPoCesteTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final MyPreferences pref = MyPreferences.current().node("test-cesty-po-ceste");
	private File slozkaMap;

	private static final Wgs A = new Wgs(49.99, 14.38);
	private static final Wgs B = new Wgs(50.01, 14.40);
	private static final Wgs C = new Wgs(50.01, 14.42);

	private final CestyModel model = new CestyModel() {
		@Override
		protected MyPreferences currPrefe() {
			return pref;
		}

		@Override
		File slozkaOfflineMap() {
			return slozkaMap;
		}
	};

	@Before
	public void setUp() {
		model.inject((EventFirer) event -> {});
		model.inject(new PoziceModel() {
			@Override
			public void refreshPozice() {}
		});
		model.inject(new CestyZperzistentnovac());
	}

	@After
	public void uklid() throws Exception {
		pref.removeNode();
	}

	private static Usek prvni(final Cesta cesta) {
		return cesta.getStart().getUvpred();
	}

	private static void naEdt(final Runnable r) throws Exception {
		SwingUtilities.invokeAndWait(r);
	}

	/** Počká, až podmínka platí; kontroluje ji na EDT, kde model mění data. */
	private static void pockej(final java.util.function.BooleanSupplier podminka) throws Exception {
		final long konec = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		final boolean[] plati = new boolean[1];
		while (System.nanoTime() < konec) {
			naEdt(() -> plati[0] = podminka.getAsBoolean());
			if (plati[0]) {
				return;
			}
			Thread.sleep(20);
		}
		fail("podmínka nenastala");
	}

	@Test
	public void predNactenimUmisteniMapSeTrasyNehledaji() {
		final CestyModel m = new CestyModel() {
			@Override
			protected MyPreferences currPrefe() {
				return pref;
			}
		};
		m.inject((EventFirer) event -> {});
		m.inject(new PoziceModel() {
			@Override
			public void refreshPozice() {}
		});
		// Při startu ještě mapy nemají umístění souborů.
		m.inject(new cz.geokuk.plugins.mapy.kachle.KachleModel());
		assertNull(m.slozkaOfflineMap());
		m.pridejBodNaKonec(A.toMou());
		m.pridejBodNaKonec(B.toMou());
		assertEquals(TypUseku.PO_CESTE, prvni(m.getCurta()).getTyp());
		assertFalse(m.isHledaSeTrasa(prvni(m.getCurta())));
	}

	@Test
	public void noveUsekyPoCesteNeboRovne() {
		assertEquals(TypUseku.PO_CESTE, model.getTypNovychUseku());
		model.pridejBodNaKonec(A.toMou());
		model.pridejBodNaKonec(B.toMou());
		assertEquals(TypUseku.PO_CESTE, prvni(model.getCurta()).getTyp());

		model.setTypNovychUseku(TypUseku.ROVNE);
		assertEquals(TypUseku.ROVNE.name(), pref.node(FPref.VYLET_node).get(FPref.TYP_NOVYCH_USEKU_value, null));
		model.pridejBodNaKonec(C.toMou());
		assertEquals(TypUseku.ROVNE, model.getCurta().getCil().getUvzad().getTyp());
		assertEquals(TypUseku.PO_CESTE, prvni(model.getCurta()).getTyp());
	}

	@Test
	public void vlozenyBodPrevezmeTypUseku() {
		model.pridejBodNaKonec(A.toMou());
		model.pridejBodNaKonec(C.toMou());
		model.setTypNovychUseku(TypUseku.ROVNE);
		model.pridejBodNaMisto(new Wgs(50.0, 14.40).toMou());
		for (final Usek u : model.getCurta().getUseky()) {
			assertEquals(TypUseku.PO_CESTE, u.getTyp());
		}
	}

	@Test
	public void vypnutiVzdusnostiVratiTypNovychUseku() {
		model.pridejBodNaKonec(A.toMou());
		model.pridejBodNaKonec(B.toMou());
		final Usek usek = prvni(model.getCurta());
		model.prepniVzdusnostUseku(usek, true);
		assertEquals(TypUseku.VZDUSNY, usek.getTyp());
		model.prepniVzdusnostUseku(usek, false);
		assertEquals(TypUseku.PO_CESTE, usek.getTyp());
		model.nastavTypUseku(usek, TypUseku.ROVNE);
		assertEquals(TypUseku.ROVNE, usek.getTyp());
	}

	@Test
	public void sMapouSeTrasaSpocita() throws Exception {
		slozkaMap = tmp.newFolder("mapy");
		cz.geokuk.plugins.cesty.routovani.RoutovacTestPristup.zkopirujMapu(slozkaMap);
		naEdt(() -> {
			model.pridejBodNaKonec(A.toMou());
			model.pridejBodNaKonec(B.toMou());
		});
		final Usek usek = prvni(model.getCurta());
		pockej(() -> usek.getStavTrasy() != null);
		assertEquals(Trasa.Stav.NALEZENO, usek.getStavTrasy());
		assertEquals(usek.dalka(), model.getCurta().dalkaPoCestach(), 1e-9);
	}

	@Test
	public void bezMapyJsouNoveUsekyRovne() throws Exception {
		slozkaMap = tmp.newFolder("prazdna");
		naEdt(() -> {
			model.pridejBodNaKonec(A.toMou());
			model.pridejBodNaKonec(B.toMou());
		});
		final Usek usek = prvni(model.getCurta());
		pockej(() -> usek.getTyp() == TypUseku.ROVNE);
	}

	@Test
	public void uzivateluvUsekPoCesteZustaneBezMapy() throws Exception {
		slozkaMap = tmp.newFolder("prazdna");
		final Usek[] usek = new Usek[1];
		naEdt(() -> {
			model.setTypNovychUseku(TypUseku.ROVNE);
			model.pridejBodNaKonec(A.toMou());
			model.pridejBodNaKonec(B.toMou());
			usek[0] = prvni(model.getCurta());
			model.nastavTypUseku(usek[0], TypUseku.PO_CESTE);
		});
		pockej(() -> usek[0].getStavTrasy() != null);
		assertEquals(TypUseku.PO_CESTE, usek[0].getTyp());
		assertEquals(Trasa.Stav.BEZ_MAPY, usek[0].getStavTrasy());
	}
}
