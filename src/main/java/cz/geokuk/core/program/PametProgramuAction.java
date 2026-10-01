package cz.geokuk.core.program;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Arrays;

import javax.swing.JOptionPane;

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
		putValue(SHORT_DESCRIPTION, "Kolik paměti si GeoKuk vezme při příštím spuštění.");
		putValue(MNEMONIC_KEY, KeyEvent.VK_M);
		setEnabled(new File(FConst.JAR_DIR, "start.jar").isFile());
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
			popisy[pocet] = mb == 0 ? "Automaticky (polovina paměti počítače, 1 až 3 GB)" : mb / 1024 + " GB";
			if (mb == ted) {
				vybrana = pocet;
			}
			pocet++;
		}
		final String[] nabidka = Arrays.copyOf(popisy, pocet);
		final Object volba = JOptionPane.showInputDialog(Dlg.parentFrame(),
				"Paměť pro GeoKuk, platí od příštího spuštění.\nVíc paměti pomůže při velkém počtu keší.\nTeď má program "
						+ Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB, počítač " + fyzicka + " MB.",
				"Paměť programu", JOptionPane.QUESTION_MESSAGE, null, nabidka, nabidka[vybrana]);
		if (volba == null) {
			return;
		}
		for (int i = 0; i < nabidka.length; i++) {
			if (nabidka[i].equals(volba)) {
				pref.putInt(Start.PAMET_KLIC, VOLBY_MB[i]);
			}
		}
	}
}
