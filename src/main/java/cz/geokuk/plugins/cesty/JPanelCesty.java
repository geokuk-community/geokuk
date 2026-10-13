package cz.geokuk.plugins.cesty;

import java.awt.*;
import java.util.*;

import javax.swing.*;
import javax.swing.border.*;

import cz.geokuk.plugins.cesty.data.*;
import cz.geokuk.plugins.cesty.routovani.Trasa;

/** Údaje o vybrané cestě vpravo nahoře v mapě. Je vidět, jen když je nějaká cesta vybraná a panel není vypnutý. */
public class JPanelCesty extends JPanel {

	private static final long serialVersionUID = 1L;

	private CestyModel cestyModel;
	private boolean zobrazit = true;

	final JLabel jNazev = new JLabel();
	final JLabel jDelka = new JLabel();
	final JLabel jBody = new JLabel();
	final JLabel jVzdusne = new JLabel();
	final JLabel jPoCestach = new JLabel();
	final JLabel jBezTrasy = new JLabel();
	final JButton jPrepocitat = new JButton("Přepočítat");
	final JRadioButton jNovePoCeste = new JRadioButton("po cestě");
	final JRadioButton jNoveRovne = new JRadioButton("rovně");

	public JPanelCesty() {
		super(new BorderLayout());
		setOpaque(false);
		// Odstup od okraje mapy a od posuvníků obsazenosti u pravého okraje.
		setBorder(new EmptyBorder(8, 0, 0, 30));
		final JPanel obsah = new JPanel(new GridBagLayout());
		obsah.setBackground(new Color(250, 250, 250));
		obsah.setBorder(new CompoundBorder(new LineBorder(Color.GRAY), new EmptyBorder(4, 8, 6, 4)));
		final GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.anchor = GridBagConstraints.WEST;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		jNazev.setFont(jNazev.getFont().deriveFont(Font.BOLD));
		c.gridy = 0;
		obsah.add(jNazev, c);
		final JButton zavrit = new JButton("×");
		zavrit.setToolTipText("Skrýt panel cesty (zpět v menu Cesty > Panel cesty)");
		zavrit.setMargin(new Insets(0, 4, 0, 4));
		zavrit.setBorderPainted(false);
		zavrit.setContentAreaFilled(false);
		zavrit.setFocusable(false);
		zavrit.addActionListener(e -> cestyModel.setPanelCesty(false));
		final GridBagConstraints cz = new GridBagConstraints();
		cz.gridx = 1;
		cz.gridy = 0;
		cz.anchor = GridBagConstraints.NORTHEAST;
		obsah.add(zavrit, cz);
		for (final JLabel l : new JLabel[] { jDelka, jPoCestach, jBody, jVzdusne }) {
			c.gridy++;
			c.gridwidth = 2;
			obsah.add(l, c);
		}
		final JPanel bezTrasy = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		bezTrasy.setOpaque(false);
		bezTrasy.add(jBezTrasy);
		bezTrasy.add(Box.createHorizontalStrut(6));
		jPrepocitat.setMargin(new Insets(0, 4, 0, 4));
		jPrepocitat.setFocusable(false);
		jPrepocitat.setToolTipText("Znovu hledat trasu po cestách, třeba po přidání offline mapy.");
		jPrepocitat.addActionListener(e -> cestyModel.prepocitejUsekyPoCeste());
		bezTrasy.add(jPrepocitat);
		c.gridy++;
		obsah.add(bezTrasy, c);
		final JPanel nove = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		nove.setOpaque(false);
		nove.add(new JLabel("Nové úseky: "));
		final ButtonGroup skupina = new ButtonGroup();
		for (final JRadioButton r : new JRadioButton[] { jNovePoCeste, jNoveRovne }) {
			r.setOpaque(false);
			r.setFocusable(false);
			skupina.add(r);
			nove.add(r);
		}
		jNovePoCeste.setToolTipText("Nové úseky povedou po cestách a pěšinách z offline mapy, kde mapa je.");
		jNoveRovne.setToolTipText("Nové úseky povedou rovnou čarou.");
		jNovePoCeste.addActionListener(e -> cestyModel.setTypNovychUseku(TypUseku.PO_CESTE));
		jNoveRovne.addActionListener(e -> cestyModel.setTypNovychUseku(TypUseku.ROVNE));
		c.gridy++;
		obsah.add(nove, c);
		add(obsah, BorderLayout.CENTER);
		setVisible(false);
	}

	public void inject(final CestyModel cestyModel) {
		this.cestyModel = cestyModel;
	}

	public void onEvent(final CestyChangedEvent event) {
		obnov();
	}

	public void onEvent(final PanelCestyEvent event) {
		zobrazit = event.isZobrazit();
		obnov();
	}

	void obnov() {
		final Cesta cesta = cestyModel == null ? null : cestyModel.getCurta();
		final boolean videt = zobrazit && cesta != null && !cesta.isEmpty();
		if (videt) {
			naplnZ(cesta);
		}
		if (videt != isVisible()) {
			setVisible(videt);
		}
		revalidate();
		repaint();
	}

	void naplnZ(final Cesta cesta) {
		jNazev.setText(cesta.getNazev() == null || cesta.getNazev().trim().isEmpty() ? "Cesta" : cesta.getNazev().trim());
		jDelka.setText("Délka " + km(cesta.dalka()));
		int bodu = 0;
		for (final Iterator<Bod> it = cesta.getBody().iterator(); it.hasNext(); it.next()) {
			bodu++;
		}
		final int wpt = cesta.getPocetWaypointu();
		jBody.setText(wpt == 0 ? "Body: " + bodu : "Body: " + bodu + ", z toho " + wpt + " " + kesi(wpt));
		final int vzdusnych = cesta.getPocetVzdusnychUseku();
		jVzdusne.setText("Vzdušné úseky: " + vzdusnych + " (do délky se nepočítají)");
		jVzdusne.setVisible(vzdusnych > 0);
		final double poCestach = cesta.dalkaPoCestach();
		jPoCestach.setText("z toho po cestách " + km(poCestach));
		jPoCestach.setVisible(poCestach > 0);
		final int bezTrasy = cesta.getPocetUsekuPoCesteBezTrasy();
		jBezTrasy.setText("Úseky bez trasy po cestách: " + bezTrasy);
		jBezTrasy.setToolTipText(bezTrasy == 0 ? null : "<html>" + popisyBezTrasy(cesta));
		jBezTrasy.getParent().setVisible(bezTrasy > 0);
		final boolean poCeste = cestyModel.getTypNovychUseku() == TypUseku.PO_CESTE;
		jNovePoCeste.setSelected(poCeste);
		jNoveRovne.setSelected(!poCeste);
	}

	private static String popisyBezTrasy(final Cesta cesta) {
		final Set<String> popisy = new LinkedHashSet<>();
		for (final Usek usek : cesta.getUseky()) {
			if (usek.getStavTrasy() != null && usek.getTrasa() == null) {
				popisy.add(popisStavu(usek.getStavTrasy()));
			}
		}
		return String.join("<br>", popisy);
	}

	/** Proč úsek po cestě nevede po cestách. */
	static String popisStavu(final Trasa.Stav stav) {
		if (stav == null) {
			return "trasa se ještě nehledala";
		}
		switch (stav) {
		case BEZ_MAPY:
			return "místo není na offline mapě";
		case NENALEZENO:
			return "na mapě tudy nevede cesta";
		case PRILIS_DALEKO:
			return "úsek je delší než 30 km";
		default:
			return "trasa po cestách";
		}
	}

	static String km(final double metru) {
		return String.format(new Locale("cs"), "%.1f km", metru / 1000);
	}

	/** Keše a jejich waypointy ve správném tvaru. */
	static String kesi(final int pocet) {
		if (pocet == 1) {
			return "keš nebo waypoint";
		}
		return pocet >= 2 && pocet <= 4 ? "keše a waypointy" : "keší a waypointů";
	}

	/** Šířka pevná podle obsahu, aby panel v rohu mapy nikdo nezúžil. */
	@Override
	public Dimension getMinimumSize() {
		return getPreferredSize();
	}
}
