package cz.geokuk.core.program;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;

import javax.swing.*;

import cz.geokuk.core.napoveda.NapovedaWiki;
import cz.geokuk.core.napoveda.Restart;
import cz.geokuk.framework.Action0;
import cz.geokuk.framework.Dlg;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.start.Start;

/** Paměť, kterou spouštěč start.jar dá programu při příštím spuštění. */
public class PametProgramuAction extends Action0 {

	private static final long serialVersionUID = 1L;
	private static final int[] VOLBY_MB = { 0, 1024, 2048, 3072, 4096, 6144, 8192, 12288, 16384 };

	public PametProgramuAction() {
		super("Paměť programu...");
		putValue(SHORT_DESCRIPTION, "Kolik paměti může GeoKuk použít.");
		putValue(MNEMONIC_KEY, KeyEvent.VK_M);
		setEnabled(lzeNastavit());
	}

	static boolean lzeNastavit() {
		return new File(FConst.JAR_DIR, "start.jar").isFile();
	}

	/** Rada pro hlášky o nedostatku paměti. */
	public static String jakZvysitPamet() {
		return lzeNastavit() ? "Paměť zvýšíte v Soubor > Paměť programu." : "Spusťte GeoKuk s větší pamětí, třeba java -Xmx2g -jar geokuk.jar.";
	}

	static String gb(final long mb) {
		return mb % 1024 == 0 ? mb / 1024 + " GB" : String.format(new Locale("cs"), "%.1f GB", mb / 1024.0);
	}

	static String popisAutomaticky(final long fyzickaMb) {
		return "Automaticky (teď " + gb(Start.automatickaPametMb(fyzickaMb)) + ")";
	}

	static String stav(final long programMb, final long fyzickaMb) {
		return String.format(new Locale("cs"), "Teď %,d MB z %d GB. Platí po restartu.", programMb, Math.round(fyzickaMb / 1024.0));
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		final MyPreferences pref = MyPreferences.current().node(FPref.VSEOBECNE_node);
		final int ted = pref.getInt(Start.PAMET_KLIC, 0);
		final long fyzicka = Start.fyzickaPametMb();
		final String[] popisy = new String[VOLBY_MB.length];
		int vybrana = 0;
		int pocet = 0;
		for (final int mb : VOLBY_MB) {
			if (mb > 0 && mb > fyzicka * 3 / 4) {
				break;
			}
			popisy[pocet] = mb == 0 ? popisAutomaticky(fyzicka) : gb(mb);
			if (mb == ted) {
				vybrana = pocet;
			}
			pocet++;
		}
		final JComboBox<String> vyber = new JComboBox<>(Arrays.copyOf(popisy, pocet));
		vyber.setSelectedIndex(vybrana);
		vyber.setToolTipText("Víc paměti potřebujete jen při statisících keší. Když paměť dojde, GeoKuk to ohlásí.");
		final ListCellRenderer<? super String> renderer = vyber.getRenderer();
		vyber.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
			final Component bunka = renderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			if (bunka instanceof JComponent) {
				((JComponent) bunka).setToolTipText(index == 0 ? "Polovina paměti počítače, nejvýš 3 GB; od 16 GB 4 GB." : null);
			}
			return bunka;
		});
		final JLabel popisek = new JLabel("Paměť programu:");
		popisek.setLabelFor(vyber);
		final JLabel jStav = new JLabel(stav(Runtime.getRuntime().maxMemory() / (1024 * 1024), fyzicka));
		final Color seda = UIManager.getColor("Label.disabledForeground");
		jStav.setForeground(seda != null ? seda : Color.GRAY);
		final JPanel panel = new JPanel(new GridBagLayout());
		final GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(2, 0, 2, 6);
		panel.add(popisek, c);
		c.gridx = 1;
		panel.add(vyber, c);
		c.gridx = 0;
		c.gridy = 1;
		c.gridwidth = 2;
		panel.add(jStav, c);
		final Object[] tlacitka = { "OK", "Zrušit", NapovedaWiki.tlacitko("PametProgramu") };
		if (JOptionPane.showOptionDialog(Dlg.parentFrame(), panel, "Paměť programu", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, tlacitka, tlacitka[0]) != 0) {
			return;
		}
		final int nova = VOLBY_MB[vyber.getSelectedIndex()];
		if (nova == ted) {
			return;
		}
		pref.putInt(Start.PAMET_KLIC, nova);
		if (Restart.lze() && JOptionPane.showConfirmDialog(Dlg.parentFrame(), "Změna se projeví po restartu GeoKuku. Restartovat teď?", "Paměť programu",
				JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
			Restart.restartuj();
		}
	}
}
