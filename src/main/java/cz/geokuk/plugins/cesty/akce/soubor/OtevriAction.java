package cz.geokuk.plugins.cesty.akce.soubor;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import cz.geokuk.framework.Dlg;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OtevriAction extends SouboeCestaAction0 {

	private class GpxFilter extends FileFilter {

		@Override
		public boolean accept(final File pathname) {
			if (pathname.isDirectory()) {
				return true;
			}
			if (pathname.getName().toLowerCase().endsWith(".gpx")) {
				return true;
			}
			return false;
		}

		@Override
		public String getDescription() {
			return "Soubory s cestami (*.gpx)";
		}

	}

	private static final long serialVersionUID = 1L;

	private JFileChooser fc;

	public OtevriAction() {
		super("Otevřít cesty (gpx)");
		putValue(SHORT_DESCRIPTION, "Otevře zadaný výlet v GPX a nahradí jim všechyn načtené cesty.");
		putValue(MNEMONIC_KEY, KeyEvent.VK_T);
		// putValue(SMALL_ICON, ImageLoader.seekResIcon("x16/vylet/vyletAno.png"));
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		if (!super.ulozitSDotazem()) {
			return; // mělo se ukládat a řeklo se, že ne
		}
		if (fc == null) { // dlouho to trvá, tak vytvoříme vždy nový
			fc = new JFileChooser();
			fc.addChoosableFileFilter(new GpxFilter());
		}
		fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
		// fc.setCurrentDirectory(new File(jtext.getText()));
		fc.setSelectedFile(cestyModel.defaultAktualnihoVyletuFile());

		final int result = fc.showDialog(Dlg.parentFrame(), "Otevřít");
		if (result == JFileChooser.APPROVE_OPTION) {
			final File selectedFile = fc.getSelectedFile();
			if (!new GpxFilter().accept(selectedFile)) {
				Dlg.error("Soubor \"" + selectedFile + "\" nemá příponu GPX!");
			} else { // je to dobré, otvíráme
				cestyModel.otevri(selectedFile);
				log.debug("Načtena cesta z: {}", selectedFile);
			}
		}
	}

}
