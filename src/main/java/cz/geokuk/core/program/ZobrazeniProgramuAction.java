package cz.geokuk.core.program;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.*;

import cz.geokuk.core.napoveda.NapovedaWiki;
import cz.geokuk.core.napoveda.Restart;
import cz.geokuk.framework.Action0;
import cz.geokuk.framework.Dlg;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.start.Start;

/** Zvětšení a hardwarové vykreslování, které spouštěč start.jar nastaví programu při příštím spuštění. */
public class ZobrazeniProgramuAction extends Action0 {

	private static final long serialVersionUID = 1L;

	static final String ZVETSENI = "Zvětšovat podle Windows";
	static final String OSTRA = "Ostrá";
	static final String RYCHLEJSI = "Rychlejší";
	static final String DIRECT3D = "Hardwarové vykreslování (Direct3D)";

	private KachleModel kachleModel;

	public void inject(final KachleModel kachleModel) {
		this.kachleModel = kachleModel;
	}

	public ZobrazeniProgramuAction() {
		super("Zobrazení programu...");
		putValue(SHORT_DESCRIPTION, "Ostrost a velikost písma offline mapy; zvětšení a hardwarové vykreslování při příštím spuštění.");
		putValue(MNEMONIC_KEY, KeyEvent.VK_Z);
	}

	static boolean jeWindows() {
		return System.getProperty("os.name", "").startsWith("Windows");
	}

	/** Uloží volby; vrací true, když se některá změnila. */
	static boolean uloz(final MyPreferences pref, final boolean zvetseni, final boolean direct3d) {
		final boolean zmena = pref.getBoolean(Start.ZVETSENI_KLIC, true) != zvetseni || pref.getBoolean(Start.DIRECT3D_KLIC, true) != direct3d;
		pref.putBoolean(Start.ZVETSENI_KLIC, zvetseni);
		pref.putBoolean(Start.DIRECT3D_KLIC, direct3d);
		return zmena;
	}

	static String popisZvetseni(final double meritko) {
		return meritko > 1.001 ? ZVETSENI + " (" + Math.round(meritko * 100) + " %)" : ZVETSENI;
	}

	private static double meritkoObrazovky() {
		if (GraphicsEnvironment.isHeadless()) {
			return 1;
		}
		return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getDefaultTransform().getScaleX();
	}

	/** Rámeček s nadpisem a řádky popisek – ovládací prvek. */
	private static JPanel ramecek(final String nadpis) {
		final JPanel p = new JPanel(new GridBagLayout());
		p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(nadpis), BorderFactory.createEmptyBorder(2, 6, 4, 6)));
		return p;
	}

	private static void radek(final JPanel ramecek, final int y, final JComponent popisek, final JComponent prvek) {
		final GridBagConstraints c = new GridBagConstraints();
		c.gridy = y;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(2, 0, 2, 6);
		if (prvek == null) {
			c.gridwidth = 2;
			ramecek.add(popisek, c);
			return;
		}
		ramecek.add(popisek, c);
		c.gridx = 1;
		ramecek.add(prvek, c);
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		final MyPreferences pref = MyPreferences.current().node(FPref.VSEOBECNE_node);
		final JCheckBox zvetseni = new JCheckBox(popisZvetseni(meritkoObrazovky()), pref.getBoolean(Start.ZVETSENI_KLIC, true));
		zvetseni.setToolTipText("Vypnuté: menší, ale ostřejší písmo; pomáhá, když program seká.");
		final JCheckBox direct3d = new JCheckBox(DIRECT3D, pref.getBoolean(Start.DIRECT3D_KLIC, true));
		direct3d.setToolTipText("Vypněte, když mapa nebo menu sekají.");
		final boolean oknoProgramu = PametProgramuAction.lzeNastavit() && jeWindows();
		final JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		final JRadioButton ostra = new JRadioButton(OSTRA);
		final JRadioButton rychlejsi = new JRadioButton(RYCHLEJSI);
		final JSpinner pismo = new JSpinner(new SpinnerNumberModel(100, 80, 150, 5));
		if (kachleModel != null) {
			final ButtonGroup skupina = new ButtonGroup();
			skupina.add(ostra);
			skupina.add(rychlejsi);
			ostra.setSelected(kachleModel.isOfflineMapaOstra());
			rychlejsi.setSelected(!kachleModel.isOfflineMapaOstra());
			final String rada = "Rychlejší kreslí dlaždice v menším rozlišení a systém je zvětší; hodí se pro pomalejší počítače při zvětšení Windows nad 100 %.";
			ostra.setToolTipText(rada);
			rychlejsi.setToolTipText(rada);
			pismo.setValue(kachleModel.getOfflineMapaPismoProcent());
			pismo.setToolTipText("Velikost popisků a symbolů offline mapy.");
			final JPanel volbyOstrosti = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			volbyOstrosti.add(ostra);
			volbyOstrosti.add(Box.createHorizontalStrut(8));
			volbyOstrosti.add(rychlejsi);
			final JPanel velikost = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
			velikost.add(pismo);
			velikost.add(new JLabel("%"));
			final JLabel popisOstrosti = new JLabel("Offline mapa:");
			popisOstrosti.setToolTipText(rada);
			final JLabel popisPisma = new JLabel("Písmo a ikony na mapě:");
			popisPisma.setLabelFor(pismo);
			final JPanel mapa = ramecek("Mapa (platí hned)");
			radek(mapa, 0, popisOstrosti, volbyOstrosti);
			radek(mapa, 1, popisPisma, velikost);
			mapa.setAlignmentX(0);
			panel.add(mapa);
		}
		if (oknoProgramu) {
			final JPanel okno = ramecek("Okno programu (platí po restartu)");
			radek(okno, 0, zvetseni, null);
			radek(okno, 1, direct3d, null);
			okno.setAlignmentX(0);
			panel.add(Box.createVerticalStrut(6));
			panel.add(okno);
		}
		final Object[] tlacitka = { "OK", "Zrušit", NapovedaWiki.tlacitko("ZobrazeniProgramu") };
		if (JOptionPane.showOptionDialog(Dlg.parentFrame(), panel, "Zobrazení programu", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, tlacitka, tlacitka[0]) != 0) {
			return;
		}
		if (kachleModel != null) {
			kachleModel.setVzhledOfflineMapy(ostra.isSelected(), ((Number) pismo.getValue()).intValue());
		}
		if (oknoProgramu && uloz(pref, zvetseni.isSelected(), direct3d.isSelected()) && Restart.lze()
				&& JOptionPane.showConfirmDialog(Dlg.parentFrame(), "Změna se projeví po restartu GeoKuku. Restartovat teď?", "Zobrazení programu",
						JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
			Restart.restartuj();
		}
	}
}
