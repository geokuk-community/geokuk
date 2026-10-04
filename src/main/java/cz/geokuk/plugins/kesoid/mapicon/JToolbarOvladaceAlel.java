/**
 *
 */
package cz.geokuk.plugins.kesoid.mapicon;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
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

	/** Výška ikon standardní sady, vyšší ikony se na toolbaru zmenší. */
	static final int VYSKA_IKONY = 24;

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

	/** Pevná výška, ať se toolbar a s ním mapa nemění podle toho, co je v datech a kdy se načtou. */
	@Override
	public Dimension getPreferredSize() {
		final Dimension d = super.getPreferredSize();
		d.height = vyskaOvladace();
		return d;
	}

	static JIconCheckBox novyOvladac() {
		final JIconCheckBox cb = new JIconCheckBox();
		cb.setFocusable(false);
		cb.setMaxVyskaIkony(VYSKA_IKONY);
		return cb;
	}

	static int vyskaOvladace() {
		final JIconCheckBox cb = new JIconCheckBox();
		cb.setIcon(new ImageIcon(new BufferedImage(1, VYSKA_IKONY, BufferedImage.TYPE_INT_ARGB)));
		return cb.getPreferredSize().height;
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
			cb = novyOvladac();
			action.join(cb);
			tb.add(cb);
			cb.setText(null);
			mapka.put(alela.qualName(), cb);
		}
		final boolean jetam = vsechny.getPouziteAlely().contains(alela);
		cb.setVisible(jetam);

	}

}
