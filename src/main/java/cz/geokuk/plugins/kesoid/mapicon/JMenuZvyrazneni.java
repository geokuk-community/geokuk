package cz.geokuk.plugins.kesoid.mapicon;

import java.util.ArrayList;
import java.util.List;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import cz.geokuk.framework.Factory;
import cz.geokuk.plugins.kesoid.mvc.KesoidModel;

/** Podmenu Kešoidy > Zvýraznění: vestavěné a vlastní předvolby, úprava pravidel. Sestaví se při každém otevření. */
public class JMenuZvyrazneni extends JMenu {

	private static final long serialVersionUID = 1L;

	private KesoidModel kesoidModel;
	private Factory factory;

	public JMenuZvyrazneni() {
		super("Zvýraznění");
		setToolTipText("Barevný čtverec pod ikonou keší, které chcete na mapě odlišit.");
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

	public void inject(final KesoidModel kesoidModel) {
		this.kesoidModel = kesoidModel;
	}

	public void inject(final Factory factory) {
		this.factory = factory;
	}

	void sestav() {
		removeAll();
		final Zvyrazneni aktualni = kesoidModel.getZvyrazneni();
		final List<Zvyrazneni> predvolby = new ArrayList<>(Zvyrazneni.VESTAVENA);
		predvolby.addAll(kesoidModel.getPredvolbyZvyrazneni());
		if (!predvolby.contains(aktualni)) {
			predvolby.add(aktualni);
		}
		final ButtonGroup skupina = new ButtonGroup();
		for (int i = 0; i < predvolby.size(); i++) {
			if (i == Zvyrazneni.VESTAVENA.size()) {
				addSeparator();
			}
			final Zvyrazneni z = predvolby.get(i);
			final JRadioButtonMenuItem polozka = new JRadioButtonMenuItem(z.getNazev(), z.equals(aktualni));
			polozka.addActionListener(e -> kesoidModel.setZvyrazneni(z));
			skupina.add(polozka);
			add(polozka);
		}
		addSeparator();
		final JMenuItem upravit = new JMenuItem("Upravit zvýraznění…");
		upravit.addActionListener(e -> factory.init(new JZvyrazneniDialog()).setVisible(true));
		add(upravit);
	}
}
