package cz.geokuk.plugins.mapy.kachle.gui;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.podklady.TemaOfflineMapy;
import cz.geokuk.plugins.mapy.kachle.podklady.VolbaVrstev;
import cz.geokuk.plugins.mapy.kachle.podklady.VrstvyTematu;
import cz.geokuk.util.process.BrowserOpener;

/** Podmenu s tématy offline mapy: vestavěná a soubory .zip a .xml ve složce offline map. Sestaví se při každém otevření. */
public class JTemaOfflineMapyMenu extends JMenu {

	private static final long serialVersionUID = 1L;

	static final String LEGENDA_PAWS = "https://osm.paws.cz/legend_paws.php";

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
		if (temata.stream().noneMatch(aktualni::stejnyZdroj)) {
			temata.add(aktualni);
		}
		final ButtonGroup skupina = new ButtonGroup();
		for (int i = 0; i < temata.size(); i++) {
			if (i == TemaOfflineMapy.VESTAVENA.size()) {
				addSeparator();
			}
			final TemaOfflineMapy tema = temata.get(i);
			final JRadioButtonMenuItem polozka = new JRadioButtonMenuItem(tema.getNazev(), tema.stejnyZdroj(aktualni));
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
		addSeparator();
		add(vrstvy(aktualni));
		final URL legenda = legenda(aktualni);
		if (legenda != null) {
			final JMenuItem jLegenda = new JMenuItem("Legenda tématu…");
			jLegenda.setToolTipText(legenda.toString());
			jLegenda.addActionListener(e -> BrowserOpener.displayURL(legenda));
			add(jLegenda);
		}
	}

	/** Podmenu se styly (vybírá se jeden) a vrstvami zvoleného stylu; bez nabídky vrstev jen zašedlá položka s vysvětlením. */
	JMenu vrstvy(final TemaOfflineMapy aktualni) {
		final JMenu menu = new JMenu("Vrstvy tématu");
		final VrstvyTematu nabidka = kachleModel.getVrstvyTematu();
		if (nabidka == null) {
			menu.setEnabled(false);
			menu.setToolTipText("Téma nemá vrstvy, nebo ještě není načtené (načte se s offline mapou).");
			return menu;
		}
		final VolbaVrstev volba = aktualni.getVrstvy();
		final String styl = nabidka.zvolenyStyl(volba);
		if (nabidka.getStyly().size() > 1) {
			final ButtonGroup skupina = new ButtonGroup();
			for (final VrstvyTematu.Polozka s : nabidka.getStyly()) {
				final JRadioButtonMenuItem polozka = new JRadioButtonMenuItem(s.getNazev(), s.getId().equals(styl));
				polozka.addActionListener(e -> kachleModel.setVrstvyTematu(volba.seStylem(s.getId(), nabidka.getVychoziStyl())));
				skupina.add(polozka);
				menu.add(polozka);
			}
			menu.addSeparator();
		}
		for (final VrstvyTematu.Polozka v : nabidka.getVrstvy(styl)) {
			final JCheckBoxMenuItem polozka = new JCheckBoxMenuItem(v.getNazev(), volba.jeZapnuta(v.getId(), v.isZapnutaAutorem()));
			polozka.addActionListener(e -> kachleModel.setVrstvyTematu(volba.sVrstvou(v.getId(), polozka.isSelected(), v.isZapnutaAutorem())));
			menu.add(polozka);
		}
		menu.addSeparator();
		final JMenuItem obnovit = new JMenuItem("Obnovit výchozí vrstvy");
		obnovit.setToolTipText("Vrstvy, jak je má zapnuté autor tématu.");
		obnovit.setEnabled(!volba.isAutor());
		obnovit.addActionListener(e -> kachleModel.setVrstvyTematu(VolbaVrstev.AUTOR));
		menu.add(obnovit);
		return menu;
	}

	/** Stránka s legendou tématu u autora, null když ji neznáme. */
	static URL legenda(final TemaOfflineMapy tema) {
		if (tema.isVestavene() || !tema.getSoubor().getName().toLowerCase(Locale.ROOT).startsWith("paws")) {
			return null;
		}
		try {
			return new URL(LEGENDA_PAWS);
		} catch (final MalformedURLException e) {
			throw new IllegalStateException(e);
		}
	}

	private void vyberSoubor() {
		final JFileChooser chooser = new JFileChooser(slozka());
		chooser.setFileFilter(new FileNameExtensionFilter("Téma offline mapy (.zip, .xml)", "zip", "xml"));
		if (chooser.showOpenDialog(SwingUtilities.getWindowAncestor(this)) == JFileChooser.APPROVE_OPTION) {
			kachleModel.setTemaOfflineMapy(TemaOfflineMapy.zeSouboru(chooser.getSelectedFile(), null));
		}
	}
}
