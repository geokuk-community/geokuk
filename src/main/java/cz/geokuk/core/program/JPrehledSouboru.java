package cz.geokuk.core.program;

import java.awt.Dimension;
import java.util.*;
import java.util.stream.Collectors;

import javax.swing.*;

import cz.geokuk.core.render.*;
import cz.geokuk.framework.Dlg;
import cz.geokuk.plugins.kesoid.mvc.*;
import cz.geokuk.plugins.mapy.KachleUmisteniSouboru;
import cz.geokuk.util.file.Filex;
import cz.geokuk.util.lang.StringUtils;

public class JPrehledSouboru extends JPanel {

	public static class YNejdeTo extends Exception {

		private static final long serialVersionUID = -4020543178329529443L;

		/**
		 * @param aMessage
		 */
		public YNejdeTo(final String aMessage) {
			super(aMessage);
		}
	}

	private static final long serialVersionUID = -2491414463002815835L;
	private JJedenSouborPanel jKesDir;
	private JJedenSouborPanel jGeogetDataDir;

	private JJedenSouborPanel jGsakDataDir;
	private JCheckBox jGsakNacitatAzPoVybrani;
	private JTextField jGsakCasNalezu;
	private JTextField jGsakCasNenalezu;

	private JJedenSouborPanel jOziDir;
	private JJedenSouborPanel jKmzDir;

	private JJedenSouborPanel jPictureDir;

	private KesoidModel kesoidModel;

	private RenderModel renderModel;

	private JTabbedPane jTabbedPane;

	private final EnumMap<ESouborPanelName, JJedenSouborPanel> mapaProFokusovani = new EnumMap<>(ESouborPanelName.class);

	public JPrehledSouboru(final Void v) {
		initComponents();
	}

	public void fokusni(final ESouborPanelName panelName) {
		final JJedenSouborPanel panel = mapaProFokusovani.get(panelName);
		if (panel == null) {
			return;
		}
		panel.fokusniSe();
	}

	public void inject(final KesoidModel kesoidModel) {
		this.kesoidModel = kesoidModel;
	}

	public void inject(final RenderModel renderModel) {
		this.renderModel = renderModel;
	}

	public void onEvent(final KesoidUmisteniSouboruChangedEvent event) {
		final KesoidUmisteniSouboru u = event.getUmisteniSouboru();
		jKesDir.setFilex(u.getKesDir());
		jGeogetDataDir.setFilex(u.getGeogetDataDir());
		jGsakDataDir.setFilex(u.getGsakDataDir());
	}

	public void onEvent(final GsakParametryNacitaniChangedEvent event) {
		final GsakParametryNacitani g = event.getGsakParametryNacitani();
		jGsakCasNalezu.setText(_join(g.getCasNalezu()));
		jGsakCasNenalezu.setText(_join(g.getCasNenalezu()));
		jGsakNacitatAzPoVybrani.setSelected(!g.isNacistVsechnyDatabaze());
	}

	public void onEvent(final RenderUmisteniSouboruChangedEvent event) {
		final RenderUmisteniSouboru u = event.getUmisteniSouboru();
		jPictureDir.setFilex(u.getPictureDir());
		jKmzDir.setFilex(u.getKmzDir());
		jOziDir.setFilex(u.getOziDir());
	}

	private JComponent createTab() {
		final JComponent tab = new JPanel();
		tab.setLayout(new BoxLayout(tab, BoxLayout.PAGE_AXIS));
		return tab;
	}

	private void initComponents() {
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

		final int ho = 8;
		setBorder(BorderFactory.createEmptyBorder(ho, ho, ho, ho));

		jTabbedPane = new JTabbedPane();

		final JComponent tab1 = createTab();
		jTabbedPane.addTab("Keše", null, tab1, "Odkud program načítá keše.");
		final JComponent tab1a = createTab();
		jTabbedPane.addTab("GSAK", null, tab1a, "Načítání keší z GSAK.");
		final JComponent tab3 = createTab();
		jTabbedPane.addTab("Rendr", null, tab3, "Výstupní složky pro rendrování.");
		final JComponent tab4 = createTab();
		jTabbedPane.addTab("Program", null, tab4, "Složky a soubory programu, všechny jsou ve složce s programem.");
		// tabbedPane.setMnemonicAt(0, KeyEvent.VK_1);

		jKesDir = pridejJednuPolozkuproEdit(null, tab1, "Složka s keškami (GPX) získanými z Geogetu nebo jiného programu.", true, false);
		jGeogetDataDir = pridejJednuPolozkuproEdit(null, tab1, "Datová složka geogetu.", true, true);

		jGsakDataDir = pridejJednuPolozkuproEdit(null, tab1a, "Datová složka GSAK.", true, true);
		jGsakNacitatAzPoVybrani = pridejLogickePole(jGsakDataDir, "Načítat až po vybrání",
		        "<html>" //
		                + "Po změně datové složky GSAK budou všechny databáze označeny jako blokované." //
		                + "<br/>Mohou být dost velké a stejně asi budete chtít zobrazit jen několik málo z nich" //
		                + "<br/>vyberte si je vpravo dole, kliknutím na \"zdroje\"." //
		                + "</html>");
		jGsakCasNalezu = pridejTextovePole(jGsakDataDir, "Čas nálezu",
		        "<html>" //
		                + "Vestavěné nebo vlastní políčko v GSAK, které obsahuje čas nálezu keše ve tvaru <tt>HH:MM</tt>." //
		                + "<br/>Pokud obsahuje kromě časového údaje i další text, bude použit jen časový údaj." //
		                + "<br/>Můžete uvést více políček, použije se první časový údaj, který bude nalezen." //
		                + "</html>");
		jGsakCasNenalezu = pridejTextovePole(jGsakDataDir, "Čas nenálezu",
		        "<html>" //
		                + "Vestavěné nebo vlastní políčko v GSAK, které obsahuje čas nenalezení (DNF) keše ve tvaru <tt>HH:MM</tt>." //
		                + "<br/>Pokud obsahuje kromě časového údaje i další text, bude použit jen časový údaj." //
		                + "<br/>Můžete uvést více políček, použije se první časový údaj, který bude nalezen." //
		                + "</html>");

		jOziDir = pridejJednuPolozkuproEdit(ESouborPanelName.OZI, tab3, "Složka pro rendrování kalibrovaných mapy pro OziExplorer", true, false);
		jKmzDir = pridejJednuPolozkuproEdit(ESouborPanelName.KMZ, tab3, "Složka pro rendrování KMZ souborů (GoogleEarthj)", true, false);
		jPictureDir = pridejJednuPolozkuproEdit(ESouborPanelName.PICTURE, tab3, "Složka pro rendrování obrázků map", true, false);

		pridejJednuPolozkuProCteni(null, tab4, "Složka s programem (zde je geokuk.jar)", new Filex(FConst.JAR_DIR, false, true), true);
		pridejJednuPolozkuProCteni(null, tab4, "Data programu: nastavení, cache, cesty, ikony, výlety a logy", new Filex(FConst.DATA_DIR, false, true), true);
		pridejJednuPolozkuProCteni(null, tab4, "Cesty: výchozí složka pro ukládání cest", KesoidUmisteniSouboru.CESTY_DIR, true);
		pridejJednuPolozkuProCteni(null, tab4, "Ikony: vlastní ikony (moje) a ikony od jiných geokolegů (ostatni)", new Filex(KesoidUmisteniSouboru.IKONY_DIR, false, true), true);
		pridejJednuPolozkuProCteni(null, tab4, "Výlety: keše, které jdeme lovit (lovim.ggt) a na které teď nepůjdeme (tedne.ggt)", new Filex(KesoidUmisteniSouboru.VYLETY_DIR, false, true), true);
		pridejJednuPolozkuProCteni(null, tab4, "Cache: dlaždice map uložené na disk (lze smazat)", KachleUmisteniSouboru.KACHLE_CACHE_DIR, true);
		pridejJednuPolozkuProCteni(null, tab4, "Log a chybová hlášení", new Filex(UmisteniProgramu.log(), false, true), true);
		ukonciPanel(tab1);
		ukonciPanel(tab1a);
		ukonciPanel(tab3);
		ukonciPanel(tab4);
		add(jTabbedPane);
		add(Box.createVerticalGlue());

		final Box ulobox = Box.createHorizontalBox();
		final JButton ulozit = new JButton("Uložit");

		// ulobox.add(ulozlabel);
		ulobox.add(Box.createHorizontalGlue());
		ulobox.add(ulozit);
		ulobox.setAlignmentX(LEFT_ALIGNMENT);
		add(ulobox);

		// jTabbedPane.setSelectedIndex(2);
		// jKmzDir.requestFocus();
		registerEvents(ulozit);
	}

	private JJedenSouborPanel pridejJednuPolozkuProCteni(final ESouborPanelName souborPanelName, final JComponent tab, final String label, final Filex hodnota, final boolean jenAdresare) {
		final JJedenSouborPanel panel = new JJedenSouborPanel(souborPanelName, label, jenAdresare, false, false);
		panel.setFilex(hodnota);
		tab.add(panel);
		tab.add(Box.createRigidArea(new Dimension(0, 20)));
		panel.setMaximumSize(new Dimension(1000, 40));
		if (souborPanelName != null) {
			mapaProFokusovani.put(souborPanelName, panel);
		}
		return panel;
	}

	private JJedenSouborPanel pridejJednuPolozkuproEdit(final ESouborPanelName souborPanelName, final JComponent tab, final String label, final boolean jenAdresare, final boolean lzeDeaktivovat) {
		final JJedenSouborPanel panel = new JJedenSouborPanel(souborPanelName, label, jenAdresare, true, lzeDeaktivovat);
		tab.add(panel);
		tab.add(Box.createRigidArea(new Dimension(0, 20)));
		// Zjištěno, že to funguje, pokud je tam i lepidlo
		// panel.setMaximumSize(new Dimension(1000, 40));
		panel.setMaximumSize(new Dimension(panel.getMaximumSize().width, panel.getPreferredSize().height));
		if (souborPanelName != null) {
			mapaProFokusovani.put(souborPanelName, panel);
		}
		return panel;
	}

	private JTextField pridejTextovePole(final JComponent aParent, final String aLabel, final String aHint) {
		final JTextField input = new JTextField();
		input.setToolTipText(aHint);

		final JLabel label = new JLabel(aLabel);
		label.setToolTipText(aHint);
		label.setLabelFor(input);

		aParent.add(label);
		aParent.add(input);

		return input;
	}

	private JCheckBox pridejLogickePole(final JComponent aParent, final String aLabel, final String aHint) {
		final JCheckBox input = new JCheckBox(aLabel);
		input.setToolTipText(aHint);

		aParent.add(input);

		return input;
	}

	private void registerEvents(final JButton ulozit) {
		ulozit.addActionListener(aE -> {
			try {
				{
					final KesoidUmisteniSouboru u1 = new KesoidUmisteniSouboru();
					u1.setKesDir(jKesDir.vezmiSouborAProver());
					u1.setCestyDir(KesoidUmisteniSouboru.CESTY_DIR);
					u1.setGeogetDataDir(jGeogetDataDir.vezmiSouborAProver());
					u1.setGsakDataDir(jGsakDataDir.vezmiSouborAProver());
					u1.setImage3rdPartyDir(KesoidUmisteniSouboru.IMAGE_3RDPARTY_DIR);
					u1.setImageMyDir(KesoidUmisteniSouboru.IMAGE_MY_DIR);
					u1.setNeGgtFile(KesoidUmisteniSouboru.NE_GGT);
					u1.setAnoGgtFile(KesoidUmisteniSouboru.ANO_GGT);
					kesoidModel.setUmisteniSouboru(u1);
				}
				{
					final GsakParametryNacitani g = new GsakParametryNacitani();
					g.setCasNalezu(_split(jGsakCasNalezu.getText()));
					g.setCasNenalezu(_split(jGsakCasNenalezu.getText()));
					g.setNacistVsechnyDatabaze(!jGsakNacitatAzPoVybrani.isSelected());
					kesoidModel.setGsakParametryNacitani(g);
				}
				{
					final RenderUmisteniSouboru u3 = new RenderUmisteniSouboru();
					u3.setOziDir(jOziDir.vezmiSouborAProver());
					u3.setKmzDir(jKmzDir.vezmiSouborAProver());
					u3.setPictureDir(jPictureDir.vezmiSouborAProver());
					renderModel.setUmisteniSouboru(u3);
				}

				// Board.multiNacitacLoaderManager.startLoad(true);

				((JUmisteniSouboruDialog) SwingUtilities.getRoot(JPrehledSouboru.this)).dispose();
			} catch (final YNejdeTo e) {
				Dlg.error(e.getMessage());
			}
		});
	}

	private String _join(final Collection<String> aValues) {
		return aValues.stream().collect(Collectors.joining(";"));
	}

	private Collection<String> _split(final String text) {
		return Arrays.stream((text == null ? "" : text).split("[;:,]"))//
		        .filter(s -> !StringUtils.isBlank(s))//
		        .map(s -> s.trim())//
		        .collect(Collectors.toList())//
		;
	}

	private void ukonciPanel(final JComponent tab) {
		tab.remove(tab.getComponentCount() - 1); // odstranit mezeru za poslední podtovoru
		tab.add(Box.createVerticalGlue());
	}
}
