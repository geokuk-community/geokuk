package cz.geokuk.plugins.mapy.pruvodce;

import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.JOptionPane;

import cz.geokuk.framework.Action0;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.plugins.mapy.MapyModel;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.sprava.SpravaMapModel;

/** Otevře průvodce nastavením map. */
public class PruvodceMapAction extends Action0 {

	private static final long serialVersionUID = 1L;

	static final String NAZEV = "Průvodce nastavením map…";

	private SpravaMapModel spravaMap;
	private MapyModel mapyModel;
	private KachleModel kachleModel;

	public PruvodceMapAction() {
		super(NAZEV);
		putValue(SHORT_DESCRIPTION, "Vybere země, podklad a vrstvy, stáhne, co chybí, a přepne mapu.");
		putValue(MNEMONIC_KEY, KeyEvent.VK_P);
	}

	public void inject(final SpravaMapModel spravaMap) {
		this.spravaMap = spravaMap;
	}

	public void inject(final MapyModel mapyModel) {
		this.mapyModel = mapyModel;
	}

	public void inject(final KachleModel kachleModel) {
		this.kachleModel = kachleModel;
	}

	private static final String NODE = "mapy-pruvodce";
	private static final String VIDEL = "videl";

	/** Při prvním spuštění nové instalace průvodce otevře sám; jinak se jen zapamatuje, že už se nenabízí. Nic se neotevírá při automatizaci a bez displeje. */
	public void nabidniPriPrvnimSpusteni(final boolean automatizace) {
		final MyPreferences pref = MyPreferences.current().node(NODE);
		if (automatizace || GraphicsEnvironment.isHeadless() || pref.getBoolean(VIDEL, false)) {
			return;
		}
		pref.putBoolean(VIDEL, true);
		if (mapyModel != null && mapyModel.isPrvniSpusteni()) {
			actionPerformed(null);
		}
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		if (SpousteniPruvodce.bezi()) {
			JOptionPane.showMessageDialog(getMainFrame(), "Z průvodce se právě stahuje. Průběh je vidět v bloku Mapy ve stavovém řádku.", "Nastavení map", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		spravaMap.obnovStavy();
		new JPruvodceMap(getMainFrame(), new ProgramPruvodceKontext(spravaMap, mapyModel, kachleModel)).setVisible(true);
	}
}
