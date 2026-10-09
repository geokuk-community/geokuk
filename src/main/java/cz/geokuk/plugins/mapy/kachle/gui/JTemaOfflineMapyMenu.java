package cz.geokuk.plugins.mapy.kachle.gui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.podklady.TemaOfflineMapy;

/** Podmenu s tématy offline mapy: vestavěná a soubory .zip a .xml ve složce offline map. Sestaví se při každém otevření. */
public class JTemaOfflineMapyMenu extends JMenu {

	private static final long serialVersionUID = 1L;

	private final KachleModel kachleModel;

	public JTemaOfflineMapyMenu(final KachleModel kachleModel) {
		super("Téma offline mapy");
		this.kachleModel = kachleModel;
		setToolTipText("Vzhled offline mapy. Téma stažené spolu s mapou (.zip nebo .xml) dejte do složky offline map.");
		addMenuListener(new MenuListener() {
			@Override
			public void menuSelected(final MenuEvent e) {
				sestav();
			}

			@Override
			public void menuDeselected(final MenuEvent e) {
			}

			@Override
			public void menuCanceled(final MenuEvent e) {
			}
		});
		add(new JMenuItem()); // bez položky by se podmenu nedalo otevřít
	}

	private File slozka() {
		return kachleModel.getUmisteniSouboru().getOfflineMapyDir().getEffectiveFile();
	}

	void sestav() {
		removeAll();
		final TemaOfflineMapy aktualni = kachleModel.getTemaOfflineMapy();
		final List<TemaOfflineMapy> temata = new ArrayList<>(TemaOfflineMapy.VESTAVENA);
		final List<TemaOfflineMapy> zeSlozky = TemaOfflineMapy.temataVeSlozce(slozka());
		temata.addAll(zeSlozky);
		if (!temata.contains(aktualni)) {
			temata.add(aktualni);
		}
		final ButtonGroup skupina = new ButtonGroup();
		for (int i = 0; i < temata.size(); i++) {
			if (i == TemaOfflineMapy.VESTAVENA.size()) {
				addSeparator();
			}
			final TemaOfflineMapy tema = temata.get(i);
			final JRadioButtonMenuItem polozka = new JRadioButtonMenuItem(tema.getNazev(), tema.equals(aktualni));
			if (!tema.isVestavene()) {
				polozka.setToolTipText(tema.getSoubor().getPath());
			}
			polozka.addActionListener(e -> kachleModel.setTemaOfflineMapy(tema));
			skupina.add(polozka);
			add(polozka);
		}
		addSeparator();
		final JMenuItem jiny = new JMenuItem("Jiný soubor…");
		jiny.addActionListener(e -> vyberSoubor());
		add(jiny);
	}

	private void vyberSoubor() {
		final JFileChooser chooser = new JFileChooser(slozka());
		chooser.setFileFilter(new FileNameExtensionFilter("Téma offline mapy (.zip, .xml)", "zip", "xml"));
		if (chooser.showOpenDialog(SwingUtilities.getWindowAncestor(this)) == JFileChooser.APPROVE_OPTION) {
			kachleModel.setTemaOfflineMapy(TemaOfflineMapy.zeSouboru(chooser.getSelectedFile(), null));
		}
	}
}
