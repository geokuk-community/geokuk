package cz.geokuk.plugins.kesoid.mapicon;

import java.awt.event.ItemEvent;
import java.util.HashSet;
import java.util.Set;

import javax.swing.*;
import javax.swing.border.BevelBorder;

import cz.geokuk.plugins.kesoid.genetika.*;
import cz.geokuk.util.gui.FComponent;
import cz.geokuk.util.lang.CounterMap;
import cz.geokuk.util.lang.FString;

public abstract class JVyberIkon0 extends Box {

	private static final long serialVersionUID = -6496737194139718970L;
	private JComponent jvyber1;
	private JComponent jvyber2;
	private final boolean iOdskrtnutiVybira;
	private final boolean iRadioButton;
	/** Vybrané alely, které dialog nevykreslil (v datech nejsou), aby se výběrem neztratily. */
	private Set<String> nevykreslenaVybrana = new HashSet<>();

	public JVyberIkon0(final boolean aRadioButton, final boolean aOdskrtnutiVybira) {
		super(BoxLayout.LINE_AXIS);
		iRadioButton = aRadioButton;
		iOdskrtnutiVybira = aOdskrtnutiVybira;
		initComponents();
	}

	protected void initComponents() {

		jvyber1 = Box.createVerticalBox();
		final JScrollPane sp1 = new JScrollPane(jvyber1);
		sp1.setViewportBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		// sp1.setAutoscrolls(true);
		FComponent.enableMouseSroll(jvyber1);

		jvyber2 = Box.createVerticalBox();
		final JScrollPane sp2 = new JScrollPane(jvyber2);
		sp2.setViewportBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		FComponent.enableMouseSroll(sp2);

		add(sp1);
		add(Box.createHorizontalStrut(5));
		add(sp2);

	}

	@SuppressWarnings("deprecation")
	protected final void refresh(final IkonBag bag, final QualAlelaNames aJmenaVybranychAlel, final CounterMap<Alela> aPoctyVybranychAlel) {
		// onInitRefreshing();

		final Genom genom = bag.getGenom();
		final QualAlelaNames jmenaVybranychAlel = aJmenaVybranychAlel;
		jvyber1.removeAll();
		jvyber2.removeAll();
		final Set<Alela> vybraneAlely = new HashSet<>();
		final Set<String> nevykreslena = new HashSet<>(jmenaVybranychAlel.getQualNames());

		for (final Gen gen : genom.getGeny()) {
			final ButtonGroup bg = new ButtonGroup();
			if (shouldRender(gen)) {
				// if (pouziteGeny.contains(gen)) {
				// System.out.println("Nas gen: " + gen.getDisplayName());
				for (final Grupa grupa : gen.getGrupy().values()) {
					final Box boxgen = Box.createVerticalBox();
					boxgen.setBorder(BorderFactory.createTitledBorder(grupa.isOther() ? gen.getDisplayName() : grupa.getDisplayName()));
					for (final Alela alela : grupa.getAlely()) {
						final Alela alelax = alela;
						// To tady musí být proto, že už na začátku se musí vytvořit instance
						// filtru, aby se dostaly ikony na toolbár, ale toto vytvoření pro moc ikon dost
						// dlouho trvá, tak se to tady optimalizuje.
						if (shouldRender(alelax)) {
							// System.out.println("Nase alela: " + agen.getDisplayName());
							final Icon ikona = najdiIkonu(alela, bag);
							final JComponent radiosikonou = Box.createHorizontalBox();
							final JLabel ikonilajbl = new JLabel(ikona);
							// ikonilajbl.setAlignmentX(LEFT_ALIGNMENT);
							radiosikonou.add(ikonilajbl);
							final JToggleButton rb = createToggleButton();
							// // FIXME [veverka] genetika: podivné porovnání, co se tady lastně porovnává a proč. Vysvětlit zde -- 11. 12. 2019 9:41:41 veverka
							if (alela.simpleName().equals(alela.getDisplayName()) || !iRadioButton) {
								if (aPoctyVybranychAlel != null) {
									rb.setText("<html>" + FString.html(alela.getDisplayName()) + "  (<i>" + aPoctyVybranychAlel.count(alela) + "</i>)");
								} else {
									rb.setText(FString.text(alela.getDisplayName()));
								}
							} else {
								rb.setText("<html>" + FString.html(alela.getDisplayName()) + "  (<b><tt>" + FString.html(alela.qualName()) + "</tt></b>)");
							}
							rb.setEnabled(shouldEnable(alelax));
							radiosikonou.add(rb);
							radiosikonou.add(Box.createHorizontalGlue());
							if (iRadioButton) {
								bg.add(rb);
							}
							boxgen.add(radiosikonou);
							nevykreslena.remove(alela.qualName());
							if (jmenaVybranychAlel.getQualNames().contains(alela.qualName())) {
								rb.setSelected(!iOdskrtnutiVybira);
								vybraneAlely.add(alela);
							} else {
								rb.setSelected(iOdskrtnutiVybira);
							}
							// nastavit listener
							rb.getModel().addItemListener(e -> {
								if (e.getStateChange() == ItemEvent.DESELECTED ^ iOdskrtnutiVybira) {
									vybraneAlely.remove(alelax);
								}
								if (e.getStateChange() == ItemEvent.SELECTED ^ iOdskrtnutiVybira) {
									vybraneAlely.add(alelax);
								}
								// jen když máme správný počet alel
								if (!iRadioButton || genom.getGeny().size() == vybraneAlely.size()) {
									zmenaVyberu(vybraneAlely);
								}
								// System.out.println(alelax + " -- " + );
							});
						}
					}
					if (boxgen.getComponentCount() > 0) {
						(genom.getSymGen() == gen ? jvyber1 : jvyber2).add(boxgen);
					}
				}
				if (iRadioButton && bg.getSelection() == null) {
					bg.getElements().nextElement().setSelected(true);
				}
			} else {
				if (iRadioButton) {
					vybraneAlely.add(gen.getVychoziAlela());
				}
			}
		}
		// }
		nevykreslenaVybrana = nevykreslena;
		zmenaVyberu(vybraneAlely);

		repaint();
		// onDoneRefreshing();

	}

	// /** PRovede s před refreshem, aby se mohly připravit keše */
	// public abstract void onInitRefreshing();
	//
	//
	// /** PRovede s po refreshi, aby se mohlo uklidit */
	// public abstract void onDoneRefreshing();

	protected abstract boolean shouldEnable(Alela alela);

	/**
	 * @param aAlelax
	 * @return
	 */
	protected abstract boolean shouldRender(Alela aAlela);

	/**
	 * @param aGen
	 * @return
	 */
	protected abstract boolean shouldRender(Gen aGen);

	protected abstract void zmenaVyberu(Set<Alela> aVybraneAlely);

	/** Jména vybraných alel včetně těch, které dialog nevykreslil. */
	protected final QualAlelaNames jmenaVcetneNevykreslenych(final Set<Alela> aVybraneAlely) {
		final Set<String> jmena = new HashSet<>(nevykreslenaVybrana);
		jmena.addAll(Alela.alelyToQualNames(aVybraneAlely).getQualNames());
		return new QualAlelaNames(jmena);
	}

	/**
	 * @return
	 */
	private JToggleButton createToggleButton() {
		return iRadioButton ? new JRadioButton() : new JCheckBox();
	}

	private Icon najdiIkonu(final Alela alela, final IkonBag bag) {
		final Genotyp genotypProAlelu = bag.getGenom().UNIVERZALNI_DRUH.genotypVychozi().with(alela);
		// genotypProAlelu.put(bag.getGenom().ALELA_H);
		return bag.seekIkon(genotypProAlelu);
	}

}
