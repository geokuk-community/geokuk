package cz.geokuk.plugins.cesty.akce.soubor;

import java.io.File;
import java.io.IOException;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import cz.geokuk.framework.Action0;
import cz.geokuk.framework.Dlg;
import cz.geokuk.plugins.cesty.CestyModel;
import cz.geokuk.plugins.cesty.data.Doc;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class SouboeCestaAction0 extends Action0 {

	private static final long serialVersionUID = -2637836928166450446L;

	protected CestyModel cestyModel;

	public SouboeCestaAction0(final String string) {
		super(string);
	}

	public void inject(final CestyModel cestyModel) {
		this.cestyModel = cestyModel;

	}

	public boolean ulozitSDotazem() {
		if (!cestyModel.getDoc().isChanged()) {
			return true; // nezměna znamená uloženo
		}
		final Object[] options = { "Uložit změny", "Zahodit změny", "Zrušit" };
		final String hlaska = cestyModel.getDoc().getFile() != null ? "<html>Soubor s výletem byl změněn <b>" + cestyModel.getDoc().getFile() + "</b> "
		        : "Byl vytvořen nový výlet, ale nebyl doposud uložen do souboru." + ".";
		final int n = JOptionPane.showOptionDialog(Dlg.parentFrame(), hlaska, "Uložení změn ve výletu", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[2]);
		System.out.println(n);
		if (n == 0) {
			return ulozit();
		} else {
			return n == 1;
		}

	}

	protected boolean ulozit() {
		final Doc xdoc = cestyModel.getDoc();
		File kam = xdoc.getFile();
		if (kam == null) { // ještě nebyl určen soubor, musíme se zeptat
			final JFileChooser fc = new JFileChooser();
			fc.addChoosableFileFilter(new GpxFilter());
			fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
			fc.setSelectedFile(cestyModel.getImplicitniVyletNovyFile());
			final int result = fc.showDialog(Dlg.parentFrame(), "Uložit");
			if (result == JFileChooser.APPROVE_OPTION) {
				final File selectedFile = doplnGpxPriponuProUkladani(fc.getSelectedFile());
				if (selectedFile.exists()) { // dtaz na přepsání
					if (!Dlg.prepsatSoubor(selectedFile)) {
						return false;
					}
				}
				// Soubor dokumentu se změní až po úspěšném uložení.
				kam = selectedFile;
			} else {
				return false;
			}
		}
		// TODO ukládat na pozadí a také mít jinde ukládací dialog
		try {
			cestyModel.uloz(kam, xdoc, true);
		} catch (final IOException e) {
			oznamNeulozeno(kam, e);
			return false;
		}
		return true;
	}

	/** Neuložené cesty se nesmí ztratit mlčky. */
	static void oznamNeulozeno(final File file, final IOException e) {
		log.error("Cestu nelze uložit do " + file, e);
		Dlg.error("Cesty se nepodařilo uložit do " + file + ":\n" + e.getMessage() + "\nPůvodní soubor zůstal beze změny.");
	}

	File doplnGgtPriponuProUkladani(final File file) {
		if (file == null) {
			return null;
		}
		if (file.getName().toLowerCase().endsWith(".ggt")) {
			return file;
		}
		return new File(file.getPath() + ".ggt");
	}

	File doplnGpxPriponuProUkladani(final File file) {
		if (file == null) {
			return null;
		}
		if (file.getName().toLowerCase().endsWith(".gpx")) {
			return file;
		}
		return new File(file.getPath() + ".gpx");
	}

}
