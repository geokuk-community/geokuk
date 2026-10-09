package cz.geokuk.core.program;

import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.*;

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
	static final String OSTROST_SYSTEM = "podle zvětšení systému";
	static final String OSTROST_100 = "100 % – rychlejší";
	static final String DIRECT3D = "Hardwarové vykreslování (Direct3D)";

	private KachleModel kachleModel;

	public void inject(final KachleModel kachleModel) {
		this.kachleModel = kachleModel;
	}

	public ZobrazeniProgramuAction() {
		super("Zobrazení programu...");
		putValue(SHORT_DESCRIPTION, "Zvětšení a hardwarové vykreslování při příštím spuštění.");
		putValue(MNEMONIC_KEY, KeyEvent.VK_Z);
		setEnabled(PametProgramuAction.lzeNastavit());
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
		return meritko > 1.001 ? ZVETSENI + " (teď " + Math.round(meritko * 100) + " %)" : ZVETSENI;
	}

	private static double meritkoObrazovky() {
		if (GraphicsEnvironment.isHeadless()) {
			return 1;
		}
		return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getDefaultTransform().getScaleX();
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		final MyPreferences pref = MyPreferences.current().node(FPref.VSEOBECNE_node);
		final JCheckBox zvetseni = new JCheckBox(popisZvetseni(meritkoObrazovky()), pref.getBoolean(Start.ZVETSENI_KLIC, true));
		zvetseni.setToolTipText("Vypnuté: menší, ale ostré písmo a mapa.");
		final JCheckBox direct3d = new JCheckBox(DIRECT3D, pref.getBoolean(Start.DIRECT3D_KLIC, true));
		direct3d.setToolTipText("Vypněte, když mapa nebo menu sekají.");
		final JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(new JLabel("<html>Platí od příštího spuštění.<br>Když je program pomalý nebo seká, zkuste vypnout jedno z nich.</html>"));
		panel.add(Box.createVerticalStrut(8));
		panel.add(zvetseni);
		if (jeWindows()) {
			panel.add(direct3d);
		}
		final JComboBox<String> ostrost = new JComboBox<>(new String[] { OSTROST_SYSTEM, OSTROST_100 });
		final JSpinner pismo = new JSpinner(new SpinnerNumberModel(100, 80, 150, 5));
		if (kachleModel != null) {
			ostrost.setSelectedIndex(kachleModel.isOfflineMapaOstra() ? 0 : 1);
			ostrost.setToolTipText("Offline mapa se kreslí rychleji, když se dlaždice nekreslí ve zvětšení systému.");
			pismo.setValue(kachleModel.getOfflineMapaPismoProcent());
			panel.add(Box.createVerticalStrut(8));
			panel.add(new JLabel("Ostrost offline mapy (platí hned):"));
			panel.add(ostrost);
			panel.add(Box.createVerticalStrut(4));
			panel.add(new JLabel("Velikost písma a ikon na mapě v % (platí hned):"));
			panel.add(pismo);
		}
		if (JOptionPane.showConfirmDialog(Dlg.parentFrame(), panel, "Zobrazení programu", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
			return;
		}
		if (kachleModel != null) {
			kachleModel.setVzhledOfflineMapy(ostrost.getSelectedIndex() == 0, ((Number) pismo.getValue()).intValue());
		}
		if (uloz(pref, zvetseni.isSelected(), direct3d.isSelected()) && Restart.lze()
				&& JOptionPane.showConfirmDialog(Dlg.parentFrame(), "Změna se projeví po restartu GeoKuku. Restartovat teď?", "Zobrazení programu",
						JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
			Restart.restartuj();
		}
	}
}
