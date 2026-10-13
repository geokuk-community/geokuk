package cz.geokuk.plugins.cesty;

import java.util.Arrays;

import org.junit.*;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.plugins.cesty.data.*;
import cz.geokuk.plugins.cesty.routovani.Trasa;

/** Panel cesty: délka po cestách, úseky bez trasy a přepínač nových úseků. */
public class JPanelCestyPoCesteTest {

	private static final Wgs A = new Wgs(50.00, 14.00);
	private static final Wgs X = new Wgs(50.005, 14.004);
	private static final Wgs B = new Wgs(50.01, 14.00);
	private static final Wgs C = new Wgs(50.01, 14.02);

	private final MyPreferences pref = MyPreferences.current().node("test-panel-po-ceste");
	private final Updator updator = new Updator();
	private Cesta cesta;
	private JPanelCesty panel;
	private CestyModel model;

	@Before
	public void setUp() {
		cesta = Cesta.create();
		updator.xadd(new Doc(), cesta);
		for (final Wgs w : new Wgs[] { A, B, C }) {
			updator.pridejNaKonec(cesta, w.toMou());
		}
		model = new CestyModel() {
			@Override
			public Cesta getCurta() {
				return cesta;
			}

			@Override
			protected MyPreferences currPrefe() {
				return pref;
			}
		};
		model.inject((cz.geokuk.framework.EventFirer) event -> {});
		model.inject(new cz.geokuk.core.coord.PoziceModel() {
			@Override
			public void refreshPozice() {}
		});
		panel = new JPanelCesty();
		panel.inject(model);
	}

	@After
	public void uklid() throws Exception {
		pref.removeNode();
	}

	private Usek usek(final int i) {
		return i == 0 ? cesta.getStart().getUvpred() : cesta.getCil().getUvzad();
	}

	@Test
	public void bezUsekuPoCesteNicNavic() {
		panel.obnov();
		Assert.assertFalse(panel.jPoCestach.isVisible());
		Assert.assertFalse(panel.jBezTrasy.getParent().isVisible());
		Assert.assertTrue("výchozí jsou nové úseky po cestě", panel.jNovePoCeste.isSelected());
	}

	@Test
	public void delkaPoCestach() {
		updator.setTyp(usek(0), TypUseku.PO_CESTE);
		updator.setTrasa(usek(0), usek(0).getBvzad().getMou(), usek(0).getBvpred().getMou(), Trasa.nalezena(Arrays.asList(A, X, B)));
		panel.obnov();
		Assert.assertTrue(panel.jPoCestach.isVisible());
		Assert.assertEquals("z toho po cestách " + JPanelCesty.km(usek(0).dalka()), panel.jPoCestach.getText());
		Assert.assertEquals("Délka " + JPanelCesty.km(usek(0).dalka() + usek(1).dalka()), panel.jDelka.getText());
	}

	@Test
	public void usekyBezTrasy() {
		updator.setTyp(usek(1), TypUseku.PO_CESTE);
		updator.setTrasa(usek(1), usek(1).getBvzad().getMou(), usek(1).getBvpred().getMou(), Trasa.bez(Trasa.Stav.BEZ_MAPY));
		panel.obnov();
		Assert.assertTrue(panel.jBezTrasy.getParent().isVisible());
		Assert.assertEquals("Úseky bez trasy po cestách: 1", panel.jBezTrasy.getText());
		Assert.assertTrue(panel.jBezTrasy.getToolTipText(), panel.jBezTrasy.getToolTipText().contains("offline mapě"));
	}

	@Test
	public void prepinacNovychUseku() {
		model.setTypNovychUseku(TypUseku.ROVNE);
		panel.obnov();
		Assert.assertTrue(panel.jNoveRovne.isSelected());
		panel.jNovePoCeste.doClick();
		Assert.assertEquals(TypUseku.PO_CESTE, model.getTypNovychUseku());
	}
}
