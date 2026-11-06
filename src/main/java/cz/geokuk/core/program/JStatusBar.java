/**
 *
 */
package cz.geokuk.core.program;

import java.awt.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.swing.*;

import cz.geokuk.core.coord.*;
import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.framework.*;
import cz.geokuk.plugins.cesty.CestyChangedEvent;
import cz.geokuk.plugins.cesty.CestyModel;
import cz.geokuk.plugins.cesty.data.Doc;
import cz.geokuk.plugins.kesoid.Ikonizer;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.LimityKresleni;
import cz.geokuk.plugins.kesoid.StavVrstev;
import cz.geokuk.plugins.kesoid.StavVrstevEvent;
import cz.geokuk.plugins.kesoid.mvc.*;
import cz.geokuk.plugins.mapy.sprava.gui.JBlokMap;
import cz.geokuk.plugins.vylety.*;
import cz.geokuk.util.gui.ZalamovaciLayout;
import cz.geokuk.util.lang.FString;

/**
 * @author Martin Veverka
 *
 */
public class JStatusBar extends JPanel implements AfterInjectInit {

	private class JSkrtnutaValue extends JValue {

		private static final long serialVersionUID = 4571833579561872745L;

		private boolean skrtnuto = true;

		JSkrtnutaValue(final String prototyp, final String kratkyPrototyp) {
			super(prototyp, kratkyPrototyp);
		}

		public void setSkrtnuto(final boolean skrtnuto) {
			if (this.skrtnuto == skrtnuto) {
				return;
			}
			this.skrtnuto = skrtnuto;
			repaint();
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see javax.swing.JComponent#paintComponent(java.awt.Graphics)
		 */
		@Override
		protected void paintComponent(final Graphics g) {
			super.paintComponent(g);
			if (skrtnuto) {
				g.setColor(Color.RED);
				g.drawLine(0, getHeight(), getWidth(), 0);
			}
		}

	}

	private class JValue extends JTextField {
		private static final long serialVersionUID = 870515243956856500L;

		/** Text nejširšího běžného obsahu, aby šířka pole a tím rozložení řádku nezávisely na datech. */
		private final String prototyp;
		/** Kratší prototyp pro úsporný režim; null, když se pole nezužuje. */
		private final String kratkyPrototyp;
		private final boolean pevnaSirka;
		private boolean kratky;

		public JValue(final String prototyp) {
			this(prototyp, false);
		}

		/** Pole, které se v úsporném režimu zúží na kratší prototyp; delší text si šířku vezme. */
		public JValue(final String prototyp, final String kratkyPrototyp) {
			this.prototyp = prototyp;
			this.kratkyPrototyp = kratkyPrototyp;
			this.pevnaSirka = false;
			setEditable(false);
			setCursor(FKurzory.TEXTOVY_KURZOR);
			setMargin(new Insets(0, 5, 0, 0));
		}

		void setKratky(final boolean kratky) {
			if (this.kratky != kratky && kratkyPrototyp != null) {
				this.kratky = kratky;
				invalidate();
			}
		}

		/** Šířka pole, když text vejde do prototypu: plného nebo kratšího. */
		int sirkaPole(final boolean kratkyPrototypPouzit) {
			final FontMetrics fm = getFontMetrics(getFont());
			final Insets ins = getInsets();
			final String p = kratkyPrototypPouzit && kratkyPrototyp != null ? kratkyPrototyp : prototyp;
			return sirkaPrototypu(fm, p) + ins.left + ins.right + 1;
		}

		/** Pole s pevnou šířkou delší text ořízne, celý je v tooltipu. */
		public JValue(final String prototyp, final boolean pevnaSirka) {
			this.prototyp = prototyp;
			this.kratkyPrototyp = null;
			this.pevnaSirka = pevnaSirka;
			// setFocusable(false);
			setEditable(false);
			setCursor(FKurzory.TEXTOVY_KURZOR);
			// System.out.println("INPUTOVA MAPA: " + getInputMap().keys() );
			setMargin(new Insets(0, 5, 0, 0));
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see javax.swing.JTextField#getPreferredSize()
		 */
		@Override
		public Dimension getPreferredSize() {
			final Dimension preferredSize = super.getPreferredSize();
			final Insets ins = getInsets();
			final FontMetrics fm = getFontMetrics(getFont());
			final int sirkaTextuPrototypu = sirkaPrototypu(fm, kratky && kratkyPrototyp != null ? kratkyPrototyp : prototyp);
			final int sirkaPrototypu = sirkaTextuPrototypu + ins.left + ins.right;
			// Text, který se vejde do prototypu, šířku nemění (JTextField přidává místo pro kurzor).
			final boolean vejdeSe = getText() == null || fm.stringWidth(getText()) <= sirkaTextuPrototypu;
			final int sirka = pevnaSirka || vejdeSe ? sirkaPrototypu : Math.max(preferredSize.width, sirkaPrototypu);
			return new Dimension(sirka + 1, preferredSize.height);
		}

	}

	private static final long serialVersionUID = -6267502844907253041L;

	/** Šířka prototypu, ve kterém každá číslice zabírá tolik jako nejširší číslice písma (číslice nemusí být stejně široké). */
	static int sirkaPrototypu(final FontMetrics fm, final String prototyp) {
		int cislice = 0;
		for (char c = '0'; c <= '9'; c++) {
			cislice = Math.max(cislice, fm.charWidth(c));
		}
		int sirka = 0;
		for (int i = 0; i < prototyp.length(); i++) {
			final char c = prototyp.charAt(i);
			sirka += Character.isDigit(c) ? cislice : fm.charWidth(c);
		}
		return sirka;
	}

	private static final String SOURADNICE = new Wgs(-88.888, -178.888).toString();
	private static final String POCTY = "9999999/9999999";
	private static final String POCET = "999999";
	/** Prototypy úsporného režimu: počty běžné databáze se vejdou, větší si místo vezmou. */
	private static final String POCTY_KRATKE = "99999/9999";
	private static final String POCET_KRATKY = "9999";
	private static final String VYLET_KRATKY = "999";
	private static final String OD_POZICE = "Od pozice:";
	private static final String OD_POZICE_KRATKE = "Od:";
	/** Mezera mezi buňkami {@link #createPanel()}: počítá se kolem každé viditelné buňky. */
	private static final int MEZERA_BUNEK = 2;

	private Mou cur;
	/** Úhel, pro který je nastavená ikona směru. */
	private long uhelSmeru = Long.MIN_VALUE;

	private Poziceq poziceq = new Poziceq();
	private final JValue souradnice = new JValue(SOURADNICE);
	private final JValue celkovePoctyVsude = new JValue(POCTY, POCTY_KRATKE);
	private final JValue filtrovanePocetyVsude = new JValue(POCTY, POCTY_KRATKE);

	private final JSkrtnutaValue celkovePoctyVyrez = new JSkrtnutaValue(POCET, POCET_KRATKY);
	private final JSkrtnutaValue filtrovanePocetyVyrez = new JSkrtnutaValue(POCET, POCET_KRATKY);
	private final JValue vzdalenost = new JValue("9999 km");

	private final JLabel azimutSmer = new JLabel();
	private final JValue azimutCislo = new JValue("359°");

	private final JValue vyletAno = new JValue("9999", VYLET_KRATKY);
	private final JValue vyletNe = new JValue("9999", VYLET_KRATKY);

	private final JValue souradnicePozice = new JValue(SOURADNICE);

	private final JValue meritkoMapy = new JValue("22");

	/** Šířka podle nejdelšího textu, aby zalamování stavového řádku nezáviselo na zobrazeném limitu; na nejvyšším stupni úspor místo jen pro zobrazené varování. */
	private final JLabel varovaniPoctuPrekrocenych = new JLabel() {
		private static final long serialVersionUID = 1L;

		@Override
		public Dimension getPreferredSize() {
			final Dimension d = super.getPreferredSize();
			d.width = sirkaVarovani(stupenUspor, isVisible(), d.width);
			return d;
		}
	};
	/** Proč zapnuté kruhy nejsou vidět; jen když nějaká zapnutá vrstva nic nekreslí. */
	private final JLabel stitekSkrytychKruhu = new JLabel(StavVrstev.TEXT_STITKU);
	private JPanel odPozice;
	private final JLabel popisekOdPozice = new JLabel(OD_POZICE);
	private JPanel poctyKesiPanel;
	private JPanel vyletyPanel;

	private final Map<Progressor, JProgressBar> jFilterProgressMap = new HashMap<>();
	private JPanel jFilterProgressPanel;
	private final JPrepinaceZdroju prepinaceZdroju = new JPrepinaceZdroju();
	private final JPopisekVyletu popisekVyletu = new JPopisekVyletu();
	private final JBlokMap blokMap = new JBlokMap();

	private final JValue jSouborSVyletem = new JValue("vylet-26.ggt", true);
	/** Hvězdička neuloženého výletu má místo i bez hvězdičky, aby blok cest neměnil šířku. */
	private final JLabel jSouborSVyletemPotrebujeUlozit = new JLabel() {
		private static final long serialVersionUID = 1L;

		@Override
		public Dimension getPreferredSize() {
			final FontMetrics fm = getFontMetrics(getFont());
			final Insets ins = getInsets();
			return new Dimension(fm.stringWidth("*") + ins.left + ins.right, fm.getHeight() + ins.top + ins.bottom);
		}
	};

	private KesBag filtrovane;

	private KesBag vsechny;

	private Coord moord;

	private final JValue jPocetKesiVCestach = new JValue("9999/99");

	/** Soubor s cestami a počty; jen když nějaké cesty jsou. */
	private final JPanel cesty = createPanel();

	private final ZalamovaciLayout layout = new ZalamovaciLayout();

	private CestyModel cestyModel;

	/** Výlet, zdroje a mapy vpravo na pevném místě, nezávisle na zalomení zbytku řádku. */
	private final JPanel pravyBlok = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

	public JStatusBar() {
		initComponents();
	}

	/** Stupeň úspor, který je právě použitý: 0 plný řádek, 1 zdroje bez názvů, 2 kratší pole a „Od:“, 3 bez počtů ve výřezu. */
	private int stupenUspor;
	private static final int NEJVYSSI_STUPEN = 3;

	/**
	 * Nevejde-li se řádek do jednoho řádku, ubírá se postupně: zdroje se zúží na zaškrtávátka, pole se zkrátí na běžné velikosti počtů a „Od pozice:“ na „Od:“, nakonec zmizí počty
	 * ve výřezu. Zalomení zůstává pro opravdu úzké okno. Stupeň se počítá z šířky řádku v plné podobě, takže na stavu zobrazení nezávisí.
	 */
	@Override
	public void doLayout() {
		if (getWidth() > 0) {
			final Insets ins = getInsets();
			final int dostupna = getWidth() - ins.left - ins.right;
			final int stupen = vyberStupen(dostupna);
			if (nastavStupen(stupen)) {
				SwingUtilities.invokeLater(this::revalidate);
			}
		}
		super.doLayout();
	}

	/** Šířky řádku na jednotlivých stupních úspor, od plné podoby; podle nich se vybere první stupeň, který se vejde. */
	int[] sirkyStupnu() {
		final boolean videtVarovani = varovaniPoctuPrekrocenych.isVisible();
		final int textVarovani = sirkaTextuVarovani();
		int plna = layout.sirkaVJednomRadku(this) - sirkaVarovani(stupenUspor, videtVarovani, textVarovani);
		if (prepinaceZdroju.isUsporny()) {
			plna += prepinaceZdroju.sirkaNazvu();
		}
		if (stupenUspor >= 2) {
			plna += usporaKratkych() + usporaPopiskuOdPozice();
		}
		if (stupenUspor >= 3) {
			plna += 2 * (celkovePoctyVyrez.sirkaPole(true) + MEZERA_BUNEK);
		}
		final int[] w = new int[NEJVYSSI_STUPEN + 1];
		w[0] = plna + sirkaVarovani(0, videtVarovani, textVarovani);
		w[1] = w[0] - prepinaceZdroju.sirkaNazvu();
		w[2] = w[1] - usporaKratkych() - usporaPopiskuOdPozice();
		w[3] = w[2] - 2 * (celkovePoctyVyrez.sirkaPole(true) + MEZERA_BUNEK) - sirkaVarovani(2, videtVarovani, textVarovani) + sirkaVarovani(3, videtVarovani, textVarovani);
		return w;
	}

	/** Šířka textu varování bez rezervy. */
	private int sirkaTextuVarovani() {
		final Insets ins = varovaniPoctuPrekrocenych.getInsets();
		final String text = varovaniPoctuPrekrocenych.getText();
		return varovaniPoctuPrekrocenych.getFontMetrics(varovaniPoctuPrekrocenych.getFont()).stringWidth(text == null ? "" : text) + ins.left + ins.right;
	}

	private int sirkaVarovani(final int stupen, final boolean videt, final int sirkaTextu) {
		final Insets ins = varovaniPoctuPrekrocenych.getInsets();
		if (stupen >= NEJVYSSI_STUPEN) {
			return videt ? sirkaTextu : 0;
		}
		return Math.max(sirkaTextu, varovaniPoctuPrekrocenych.getFontMetrics(varovaniPoctuPrekrocenych.getFont()).stringWidth(textPrekroceni(false, LimityKresleni.MAX)) + ins.left + ins.right);
	}

	private int usporaKratkych() {
		int uspora = 0;
		for (final JValue v : kratkaPole()) {
			uspora += v.sirkaPole(false) - v.sirkaPole(true);
		}
		return uspora;
	}

	private int usporaPopiskuOdPozice() {
		final FontMetrics fm = popisekOdPozice.getFontMetrics(popisekOdPozice.getFont());
		return fm.stringWidth(OD_POZICE) - fm.stringWidth(OD_POZICE_KRATKE);
	}

	private JValue[] kratkaPole() {
		return new JValue[] { celkovePoctyVsude, filtrovanePocetyVsude, celkovePoctyVyrez, filtrovanePocetyVyrez, vyletAno, vyletNe };
	}

	private int vyberStupen(final int dostupna) {
		final int[] w = sirkyStupnu();
		for (int s = 0; s < NEJVYSSI_STUPEN; s++) {
			if (w[s] <= dostupna) {
				return s;
			}
		}
		return NEJVYSSI_STUPEN;
	}

	/** Použije stupeň úspor; vrací, jestli se něco změnilo. */
	private boolean nastavStupen(final int stupen) {
		if (stupen == stupenUspor) {
			return false;
		}
		stupenUspor = stupen;
		prepinaceZdroju.setUsporny(stupen >= 1);
		for (final JValue v : kratkaPole()) {
			v.setKratky(stupen >= 2);
		}
		popisekOdPozice.setText(stupen >= 2 ? OD_POZICE_KRATKE : OD_POZICE);
		celkovePoctyVyrez.setVisible(stupen < 3);
		varovaniPoctuPrekrocenych.invalidate();
		filtrovanePocetyVyrez.setVisible(stupen < 3);
		for (final JPanel panel : new JPanel[] { poctyKesiPanel, odPozice, vyletyPanel }) {
			panel.invalidate();
		}
		return true;
	}

	/** Stupeň úspor pro testy. */
	int getStupenUspor() {
		return stupenUspor;
	}

	public void inject(final KesoidModel kesoidModel) {
		prepinaceZdroju.setOvladani(kesoidModel);
		prepinaceZdroju.obnov(kesoidModel.getStavZdroju());
		prepinaceZdroju.setPovoleneTypy(JPrepinaceZdroju.povoleneTypy(kesoidModel.getUmisteniSouboruNeboZNastaveni()));
	}

	public void inject(final CestyModel cestyModel) {
		this.cestyModel = cestyModel;
	}

	public void inject(final Factory factory) {
		factory.init(popisekVyletu);
		factory.init(blokMap);
	}

	/** Výlet obnovený při startu se načte až po zobrazení okna; místo pro cesty je připravené předem, aby se lišta nepohnula. */
	@Override
	public void initAfterInject() {
		if (cestyModel != null && cestyModel.otevreVyletPriStartu()) {
			layout.rezervuj(cesty);
		}
	}

	public void onEvent(final KesoidUmisteniSouboruChangedEvent event) {
		prepinaceZdroju.setPovoleneTypy(JPrepinaceZdroju.povoleneTypy(event.getUmisteniSouboru()));
	}

	public void onEvent(final CestyChangedEvent aEvent) {
		final Doc doc = aEvent.getModel().getDoc();
		// vyletAno.setText(doc.getPocetWaypointu() + "");
		cesty.setVisible(!doc.isEmpty());
		if (!doc.isEmpty()) {
			// Po zavření výletu zůstane místo, aby lišta během relace neposkakovala.
			layout.rezervuj(cesty);
		}
		revalidate();
		if (doc.isEmpty()) {
			jSouborSVyletem.setText(".");
			jSouborSVyletem.setToolTipText("Výlet není vůbec definován.");
			jSouborSVyletemPotrebujeUlozit.setText("");
		} else {
			if (doc.getFile() != null) {
				jSouborSVyletem.setText(doc.getFile().getName());
				jSouborSVyletem.setToolTipText(FString.text(doc.getFile().toString()));
				jSouborSVyletemPotrebujeUlozit.setText(doc.isChanged() ? "*" : "");
			} else {
				jSouborSVyletem.setText("-");
				jSouborSVyletem.setToolTipText("S výletem není spojen žádný soubor.");
				jSouborSVyletemPotrebujeUlozit.setText("");
			}
		}
		jPocetKesiVCestach.setText(doc.getPocetWaypointu() + "/" + doc.getPocetCest());
		// vyletNe.setText(cestyModel.get(EVylet.NE).size()+"");
	}

	public void onEvent(final KeskyNactenyEvent aEvent) {
		vsechny = aEvent.getVsechny();
		celkovePoctyVsude.setText(celkove(vsechny));
		celkovePoctyVyrez.setText(veVyrezu(vsechny));

		prepinaceZdroju.setCasDat(casZdroju(aEvent.getVsechny().getInformaceOZdrojich().getYungest()));
		revalidate();
	}

	static final String BEZ_POZICE = "–";

	/** Bez načtených zdrojů se čas nezobrazuje. */
	static String casZdroju(final long nejmladsi) {
		return JTabulkaZdroju.casDat(nejmladsi);
	}

	public void onEvent(final KeskyVyfiltrovanyEvent aEvent) {
		filtrovane = aEvent.getFiltrovane();
		filtrovanePocetyVsude.setText(celkove(filtrovane));
		filtrovanePocetyVyrez.setText(veVyrezu(filtrovane));
		revalidate();
	}

	public void onEvent(final KesoidOnoffEvent event) {
		celkovePoctyVyrez.setSkrtnuto(!event.isOnoff());
		filtrovanePocetyVyrez.setSkrtnuto(!event.isOnoff());
	}

	public void onEvent(final PoziceChangedEvent event) {
		poziceq = event.poziceq;
		prepocitejVzdalenostAAzimut();

		if (poziceq.isNoPosition()) {
			// souradnicePozice.setVisible(false);
			souradnicePozice.setText(BEZ_POZICE);
		} else {
			souradnicePozice.setText(poziceq.getWgs().toString());
			// souradnicePozice.setVisible(true);
		}
	}

	public void onEvent(final StavZdrojuEvent event) {
		prepinaceZdroju.obnov(event.getStav());
	}

	public void onEvent(final StavVrstevEvent event) {
		final StavVrstev stav = event.getStav();
		stitekSkrytychKruhu.setText(stav.text());
		stitekSkrytychKruhu.setToolTipText(stav.jeKVideni() ? stav.tooltip() : null);
		stitekSkrytychKruhu.setVisible(stav.jeKVideni());
		revalidate();
	}

	public void onEvent(final PrekrocenLimitWaypointuVeVyrezuEvent event) {
		setVarujPrekroceni(event.isPrekrocen(), event.isTecky(), event.getLimit());
	}

	public void onEvent(final ProgressEvent event) {
		final Progressor progressor = event.getProgressor();
		// progressor = null;
		JProgressBar jProgressBar = jFilterProgressMap.get(progressor);
		// System.out.println(event);
		final boolean pridavat = event.isVisible();
		// pridavat = true;
		if (pridavat) {
			if (jProgressBar == null) {
				jProgressBar = new JProgressBar();
				jProgressBar.setStringPainted(true);
				jProgressBar.setVisible(true);
				jFilterProgressMap.put(progressor, jProgressBar);
				jFilterProgressPanel.add(jProgressBar);
				revalidate();
			}
			jProgressBar.setVisible(event.isVisible());
			jProgressBar.setValue(event.getProgress());
			jProgressBar.setMaximum(event.getMax());
			jProgressBar.setString(event.getText());
			jProgressBar.setToolTipText(event.getTooltip());
		} else {
			if (jProgressBar != null) {
				jFilterProgressMap.remove(progressor);
				jFilterProgressPanel.remove(jProgressBar);
				revalidate();
			}
		}
	}

	public void onEvent(final VyletChangeEvent aEvent) {
		final VyletModel vyletModel = aEvent.getVyletModel();
		vyletAno.setText(vyletModel.get(EVylet.ANO).size() + "");
		vyletNe.setText(vyletModel.get(EVylet.NE).size() + "");
	}

	public void onEvent(final VyrezChangedEvent event) {
		moord = event.getMoord();
		filtrovanePocetyVyrez.setText(veVyrezu(filtrovane));
		celkovePoctyVyrez.setText(veVyrezu(vsechny));
		meritkoMapy.setText(String.valueOf(moord.getMoumer()));

	}

	public void onEvent(final ZmenaSouradnicMysiEvent event) {
		if (cur != null && cur.equals(event.moucur)) {
			return;
		}
		cur = event.moucur;
		souradnice.setText(cur == null ? "?" : cur.toWgs().toString());
		prepocitejVzdalenostAAzimut();
	}

	JPanel createPanel() {
		final JPanel panel = new JPanel();
		panel.setLayout(new FlowLayout(FlowLayout.CENTER, 2, 0));
		panel.setBorder(BorderFactory.createEtchedBorder());
		return panel;
	}

	private double azimutPoziceAMysi() {
		return poziceq.getWgs().azimut(cur.toWgs());
	}

	private String celkove(final KesBag bag) {
		final String s = bag.getWpts().size() + "/" + bag.getKesoidy().size();
		return s;
	}

	private void initComponents() {
		setLayout(layout);

		final JPanel souradnicePanel = createPanel();
		// souradnicePanel.setBorder(BorderFactory.createEtchedBorder());

		// souradnicePozice.setEditable(false);

		souradnicePanel.add(new JLabel("Myš:"));
		souradnicePanel.add(souradnice);
		// souradnice.setColumns(13);
		souradnice.setToolTipText("Souřadnice kurzoru myši");
		souradnicePanel.add(new JLabel("Z="));
		souradnicePanel.add(meritkoMapy);
		add(souradnicePanel);

		final JPanel jPozicePanel = createPanel();

		souradnicePozice.setToolTipText("Souřadnice aktuálně vybrané pozice, možno vybrat a dát do clipboardu");
		jPozicePanel.add(new JLabel("Pozice:"));
		jPozicePanel.add(souradnicePozice);
		add(jPozicePanel);

		odPozice = createPanel();

		odPozice.add(popisekOdPozice);
		odPozice.add(vzdalenost);
		vzdalenost.setToolTipText("Vzdálenost mezi vyznačenou pozicí nebo waypointem a myší kurzoru.");

		odPozice.add(azimutCislo);
		odPozice.add(azimutSmer);
		azimutSmer.setToolTipText("Azimut od vyznačené pozice k myši kurzoru.");
		azimutCislo.setToolTipText("Azimut od vyznačené pozice k myši kurzoru.");

		meritkoMapy.setToolTipText("Měřítko mapy, čím větší číslo, tím větší přiblížení");
		// add(mapoveMeritko);

		add(odPozice);
		layout.rezervuj(odPozice);

		// add(Box.createHorizontalGlue());

		///////////////////////////////
		final JPanel poctyKesi = createPanel();
		poctyKesiPanel = poctyKesi;
		poctyKesi.add(new JLabel("Vše:"));
		poctyKesi.add(celkovePoctyVsude);
		celkovePoctyVsude.setToolTipText("Počet waypointů celkem / počet kešoidů celkem.");

		poctyKesi.add(new JLabel("Filtr:"));
		poctyKesi.add(filtrovanePocetyVsude);
		filtrovanePocetyVsude.setToolTipText("Počet waypointů po aplikaci filtru / počet kešoidů po aplikaci filtru.");

		poctyKesi.add(celkovePoctyVyrez);
		celkovePoctyVyrez.setToolTipText("Počet všech waypointů ve výřezu, které by byly zobrazeny, pokud by nebyl filtr.");

		poctyKesi.add(filtrovanePocetyVyrez);
		filtrovanePocetyVyrez.setToolTipText("Počet všech zobrazených waypointů ve výřezu.");
		add(poctyKesi);

		////////////////////////////

		final JPanel vylety = createPanel();
		vyletyPanel = vylety;
		// vylety.setBorder(BorderFactory.createEtchedBorder());
		vylety.add(popisekVyletu);
		vylety.add(vyletAno);
		vyletAno.setToolTipText("Počet keší, u kterých je vyznačeno, že je chci lovit.");
		vylety.add(new JLabel("/"));
		vylety.add(vyletNe);
		vyletNe.setToolTipText("Počet keší, u kterých je vyznačeno, že je budu ignorovat.");

		cesty.add(jSouborSVyletemPotrebujeUlozit);
		cesty.add(jSouborSVyletem);
		cesty.add(jPocetKesiVCestach);
		jPocetKesiVCestach.setToolTipText("Počet waypointů dohromady / počet cest.");
		cesty.setVisible(false);
		add(cesty);

		pravyBlok.add(vylety);
		pravyBlok.add(prepinaceZdroju);
		final JPanel mapy = createPanel();
		mapy.add(blokMap);
		pravyBlok.add(mapy);
		add(pravyBlok);
		layout.vpravo(pravyBlok);

		varovaniPoctuPrekrocenych.setText(textPrekroceni(false, LimityKresleni.VYCHOZI_IKON));
		varovaniPoctuPrekrocenych.setToolTipText("Přibližte mapu nebo vyfiltrujte zbytečné waypointy.");
		varovaniPoctuPrekrocenych.setForeground(Color.RED);
		varovaniPoctuPrekrocenych.setVisible(false);
		add(varovaniPoctuPrekrocenych);
		layout.rezervuj(varovaniPoctuPrekrocenych);
		stitekSkrytychKruhu.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
		stitekSkrytychKruhu.setVisible(false);
		add(stitekSkrytychKruhu);
		layout.plovouci(stitekSkrytychKruhu);
		jFilterProgressPanel = createPanel();
		// jFilterProgress.setVisible(false);
		// jFilterProgress.setStringPainted(true);
		add(jFilterProgressPanel);
		layout.plovouci(jFilterProgressPanel);
	}

	private void prepocitejVzdalenostAAzimut() {
		if (!poziceq.isNoPosition() && cur != null) {
			final double azimut = azimutPoziceAMysi();
			final long uhel = Math.round(azimut);
			vzdalenost.setText(vzdalenostPoziceAMysia());
			if (uhel != uhelSmeru) {
				uhelSmeru = uhel;
				azimutSmer.setIcon(Ikonizer.findSmerIcon(azimut));
			}
			azimutCislo.setText(uhel + "°");
			if (!odPozice.isVisible()) {
				odPozice.setVisible(true);
				revalidate();
			}
		} else {
			odPozice.setVisible(false);
			uhelSmeru = Long.MIN_VALUE;
		}
		// meritkoMapy.setText(coord.getMoumer() + "");
	}

	static String textPrekroceni(final boolean tecky, final int limit) {
		return String.format(new Locale("cs"), "Max. %,d %s", limit, tecky ? "teček" : "bodů");
	}

	static String tooltipPrekroceni(final boolean tecky, final int limit) {
		return String.format(new Locale("cs"), "Ve výřezu je víc než %,d %s, %s se nekreslí. Přibližte mapu nebo vyfiltrujte zbytečné waypointy.", limit, tecky ? "teček" : "waypointů",
				tecky ? "tečky" : "ikony");
	}

	private void setVarujPrekroceni(final boolean b, final boolean tecky, final int limit) {
		if (b) {
			varovaniPoctuPrekrocenych.setText(textPrekroceni(tecky, limit));
			varovaniPoctuPrekrocenych.setToolTipText(tooltipPrekroceni(tecky, limit));
		}
		varovaniPoctuPrekrocenych.setVisible(b);
		revalidate();
	}

	private String veVyrezu(final KesBag bag) {
		if (bag == null) {
			return null;
		}
		final int count = bag.getIndexator().count(moord.getBoundingRect());
		final String s = count + "";
		return s;
	}

	private String vzdalenostPoziceAMysia() {
		return Wgs.vzdalenostStr(cur.toWgs(), poziceq.getWgs());
	}
}
