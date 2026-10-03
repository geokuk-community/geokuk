/**
 *
 */
package cz.geokuk.plugins.kesoid.mapicon;

import java.awt.Component;
import java.awt.Dimension;
import java.util.*;

import javax.swing.*;

import cz.geokuk.framework.Factory;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.genetika.Alela;
import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.mvc.KeskyNactenyEvent;
import cz.geokuk.plugins.kesoid.mvc.SwitchKesoidUrciteAlelyAction;
import cz.geokuk.util.gui.JIconCheckBox;

/**
 * @author Martin Veverka
 *
 */
public class JToolbarOvladaceAlel extends JPanel {

	private static final long serialVersionUID = 2858792073950044988L;

	private Genom genom;
	protected Factory factory;

	private final JToolbarOvladaceAlel tb;

	//////////////////////////////////////////
	// TODO Celkově nějak refactorovat
	private final Map<String, JIconCheckBox> mapka = new HashMap<>();
	private int vyska;

	/**
	 *
	 */
	public JToolbarOvladaceAlel(final JToolBar tb) {
		// super(BoxLayout.LINE_AXIS);
		this.tb = this;
	}

	/**
	 * @return
	 */
	public Set<String> getAlely() {
		return mapka.keySet();
	}

	public void inject(final Factory factory) {
		this.factory = factory;
	}

	@SuppressWarnings("deprecation")
	public void onEvent(final KeskyNactenyEvent event) {
		// TODO Nějak ty separátory a layoutování lépe řešit.
		removeAll();
		final KesBag vsechny = event.getVsechny();
		genom = vsechny.getGenom();
		ovladac(vsechny, genom.ALELA_hnf);
		ovladac(vsechny, genom.ALELA_fnd);
		ovladac(vsechny, genom.ALELA_own);
		ovladac(vsechny, genom.ALELA_dsbl);
		ovladac(vsechny, genom.ALELA_arch);
		ovladac(vsechny, genom.ALELA_cpt);
		ovladac(vsechny, genom.ALELA_dpl);
		// add(new JToolBar.Separator());
		add(new JSeparator(SwingConstants.VERTICAL));
		ovladac(vsechny, genom.ALELA_gc);
		ovladac(vsechny, genom.ALELA_mz);
		ovladac(vsechny, genom.ALELA_wm);
		ovladac(vsechny, genom.ALELA_gb);
		ovladac(vsechny, genom.ALELA_wp);
		add(new JSeparator(SwingConstants.VERTICAL));
		ovladac(vsechny, genom.ALELA_nevyluste);
		add(new JSeparator(SwingConstants.VERTICAL));
		if (genom.GRUPA_gc != null) {
			for (final Alela alela : genom.GRUPA_gc.getAlely()) {
				ovladac(vsechny, alela);
			}
		}
		// add(new JToolBar.Separator());
		add(new JSeparator(SwingConstants.VERTICAL));
		if (genom.GRUPA_gcawp != null) {
			for (final Alela alela : genom.GRUPA_gcawp.getAlely()) {
				ovladac(vsechny, alela);
			}
		}
		setVisible(true);
	}

	/** Výška podle nejvyšší ikony všech alel, i skrytých, ať se toolbar a s ním mapa nemění podle toho, co je v datech. */
	@Override
	public Dimension getPreferredSize() {
		final Dimension d = super.getPreferredSize();
		for (final Component c : getComponents()) {
			vyska = Math.max(vyska, c.getPreferredSize().height);
		}
		d.height = Math.max(d.height, vyska);
		return d;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.awt.Container#removeAll()
	 */
	@Override
	public void removeAll() {
		super.removeAll();
		mapka.clear();
	}

	private void ovladac(final KesBag vsechny, final Alela alela) {
		JIconCheckBox cb = mapka.get(alela.qualName());
		if (cb == null) {
			final SwitchKesoidUrciteAlelyAction action = factory.init(new SwitchKesoidUrciteAlelyAction(alela));
			cb = new JIconCheckBox();
			cb.setFocusable(false);
			action.join(cb);
			tb.add(cb);
			cb.setText(null);
			mapka.put(alela.qualName(), cb);
		}
		final boolean jetam = vsechny.getPouziteAlely().contains(alela);
		cb.setVisible(jetam);

	}

}
