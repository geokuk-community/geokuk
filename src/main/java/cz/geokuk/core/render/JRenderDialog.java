package cz.geokuk.core.render;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.*;

import javax.swing.*;
import javax.swing.border.*;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.core.hledani.RefreshorVysledkuHledani;
import cz.geokuk.core.hledani.VysledekHledani;
import cz.geokuk.core.program.ESouborPanelName;
import cz.geokuk.core.program.UmisteniSouboruAction;
import cz.geokuk.framework.*;
import cz.geokuk.plugins.geocoding.GeocodingModel;
import cz.geokuk.plugins.geocoding.Nalezenec;
import cz.geokuk.plugins.mapy.kachle.gui.JKachlovnik;
import cz.geokuk.plugins.refbody.ReferencniBodSeZmenilEvent;
import cz.geokuk.util.gui.*;
import cz.geokuk.util.lang.FUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JRenderDialog extends JMyDialog0 implements AfterInjectInit, AfterEventReceiverRegistrationInit {

	private static final long serialVersionUID = 7180968190465321695L;

	static int citacUlozeni;

	private RenderModel renderModel;
	private JButton jSpustitButton;

	private JButton jPrerusitButton;

	private JProgressBar jProgressBar;
	private JMvRadioPanel<EWhatRender> jWhatRenderRadioPanel;

	// private JTextField jRendrovaneMeritko;

	private JMvRadioPanel<EImageType> jImgTypeRadioPanel;

	private JButton jNastaveniAktualnihoMeritkaButton;

	private JNastavovecMeritka jNastavovecMeritka;
	private JGeocodingComboBox jPureJmenoSouboruCombo;
	private JGeocodingComboBox jKmzFolderNazevCombo;

	private JNastavovacVelikostiDlazdic jNastavovacVelikostiDlazdicX;

	private JNastavovacVelikostiDlazdic jNastavovacVelikostiDlazdicY;

	private JLabel jJakouHustotuLabel;
	private JSpinner jKmzDrawOrder;
	private JCheckBox jSrovnatDoSeveru;

	static final int MAX_ZNAKU_CESTY = 45;
	private static final int SIRKA_JMENA_SOUBORU = 300;

	private JTextField jKmzFolderDescription;
	private JPapirMeritkoComboBox jPapirMeritkoComboBox;

	private JKalibrBoduSpinner jKalibrBodu;

	private JLabel jPriponaSouboruLabel;

	private GeocodingModel geocodingModel;
	private final javax.swing.Timer zpozdeniGeocodingu = jednorazovyCasovac(1500, this::hledejAdresu);

	protected SortedMap<String, String> geotagingPatterns;

	private Wgs referecniBod;

	private JTwoColumnsPanel jOziPanel;

	private JTwoColumnsPanel jKmzPanel;

	private JLabel jOutputFolderLabel;
	private JButton jChangeOutputFolderButton;

	private JIkonkaPapiru jIkonkaPapiru;

	private JTextField jTerenniRozmerField;

	private JLabel jVystupniSouborLabel;

	private JLabel jVystupniSlozkaLabel;

	static Border createBorder(final String titleText) {
		final TitledBorder border = BorderFactory.createTitledBorder(titleText);
		final Font titleFont = border.getTitleFont();
		if (titleFont != null) {
			border.setTitleFont(titleFont.deriveFont(Font.BOLD | Font.ITALIC));
		}
		return border;

	}

	public JRenderDialog() {
		setTitle("Tisknout/Rendrovat");
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see cz.geokuk.framework.AfterEventReceiverRegistrationInit#initAfterEventReceiverRegistration()
	 */
	@Override
	public void initAfterEventReceiverRegistration() {
		renderModel.startRenderingProcess();
		factory.init(jNastavovecMeritka);
		registerEvents();
		// prepocitat();
	}

	@Override
	public void initAfterInject() {
		init();
	}

	public void inject(final GeocodingModel geocodingModel) {
		this.geocodingModel = geocodingModel;
	}

	public void inject(final RenderModel renderModel) {
		this.renderModel = renderModel;
	}

	public void onEvent(final PripravaRendrovaniEvent event) {
		final boolean maBytEnablovano = event.getStavRendrovani() == EStavRendrovani.PRIPRAVA;

		FComponent.setEnabledChildren(this, maBytEnablovano);
		final RenderSettings renderSettings = event.getRenderSettings();

		jWhatRenderRadioPanel.getSelectionModel().setSelected(renderSettings.getWhatRender());
		jImgTypeRadioPanel.getSelectionModel().setSelected(renderSettings.getImageType());
		final EStavRendrovani stavRendrovani = event.getStavRendrovani();
		jPrerusitButton.setVisible(stavRendrovani == EStavRendrovani.BEH);
		jPrerusitButton.setEnabled(stavRendrovani == EStavRendrovani.BEH);
		jProgressBar.setVisible(stavRendrovani == EStavRendrovani.BEH || stavRendrovani == EStavRendrovani.PRERUSOVANO);
		jSpustitButton.setVisible(stavRendrovani == EStavRendrovani.PRIPRAVA);

		final long pametMiB = renderModel.odhadniMnozstviZabranePameti() / 1024 / 1024 + 1;
		// jSpustit.setEnabled(true);
		jSpustitButton.setText(String.format("<html>%s <b>%d * %d px</b> - (%d MiB)", renderSettings.getWhatRender() != EWhatRender.TISK ? "Rendrovat " : "Tisknout", renderModel.getDim().width,
		        renderModel.getDim().height, pametMiB));
		jPrerusitButton.setText(renderSettings.getWhatRender() != EWhatRender.TISK ? "Přerušit rendrování" : "Přerušit tisk");
		// jRendrovaneMeritko.setText(renderModel.getRenderedMoumer() + "");
		jNastaveniAktualnihoMeritkaButton.setText("Nastavit na měřítko: " + renderModel.getCurrentMoumer());
		jNastaveniAktualnihoMeritkaButton.setEnabled(maBytEnablovano && renderModel.getCurrentMoumer() != renderModel.getRenderedMoumer());

		jPureJmenoSouboruCombo.setPatterned(renderSettings.getPureFileName());
		jKmzFolderNazevCombo.setPatterned(renderSettings.getKmzFolder());

		jNastavovacVelikostiDlazdicX.setMaximalniVelikost(renderSettings.getKmzMaxDlazdiceX());
		jNastavovacVelikostiDlazdicY.setMaximalniVelikost(renderSettings.getKmzMaxDlazdiceY());
		final DlazdicovaMetrikaXY dlazdicovaMetrika = renderModel.spoctiDlazdicovouMetriku();
		jNastavovacVelikostiDlazdicX.setMetrika(dlazdicovaMetrika.xx);
		jNastavovacVelikostiDlazdicY.setMetrika(dlazdicovaMetrika.yy);

		final Coord roord = event.getModel().getRoord();
		final double pixluNaMetr = roord.getPixluNaMetr();
		final double pixluNaMilimetrMapy = pixluNaMetr / 1000 * renderSettings.getPapiroveMeritko();
		final double dpi = pixluNaMilimetrMapy * 25.4;
		final double vzdalenostBodu = 1000 / pixluNaMilimetrMapy;
		final PapirovaMetrika papirovaMetrika = renderModel.getPapirovaMetrika();
		jJakouHustotuLabel.setText(
		        String.format("%.0f * %.0f mm - %.0f DPI = %.2f px/mm = %.1f \u03BCm/px", papirovaMetrika.xsize * 1000, papirovaMetrika.ysize * 1000, dpi, pixluNaMilimetrMapy, vzdalenostBodu));

		jTerenniRozmerField.setText(String.format("%.1f * %.1f km", roord.getWidthMetru() / 1000, roord.getHeightMetru() / 1000));

		jIkonkaPapiru.setMetrikia(papirovaMetrika);

		jKmzDrawOrder.setValue(renderSettings.getKmzDrawOrder());
		jSrovnatDoSeveru.setSelected(renderSettings.isSrovnatDoSeveru());
		jPapirMeritkoComboBox.setMeritko(renderSettings.getPapiroveMeritko());
		jKalibrBodu.setValue(renderSettings.getKalibrBodu());

		jPriponaSouboruLabel.setText("." + urciPriponuSouboru(renderSettings));
		nastavZakladyDoComboboxu(false);
		nastavViditelnost(renderSettings.getWhatRender());

		final File outputFolder = renderModel.getOutputFolder();
		final String cesta = outputFolder == null ? "" : outputFolder.toString();
		jOutputFolderLabel.setText(zkratCestu(cesta, MAX_ZNAKU_CESTY));
		jOutputFolderLabel.setToolTipText(cesta.isEmpty() ? null : cesta);
		jChangeOutputFolderButton.setAction(factory.init(new UmisteniSouboruAction(urciFokusovanouSlozku(renderSettings))));
		jChangeOutputFolderButton.setText("Změň...");
	}

	public void onEvent(final ProgressEvent event) {
		// TODO vyřešit problém, kdy se třeba během rendrování načtou kešule
		jProgressBar.setIndeterminate(!event.isVisible());
		jProgressBar.setValue(event.getProgress());
		jProgressBar.setMaximum(event.getMax());
		jProgressBar.setStringPainted(true);
		jProgressBar.setString(event.getText());
		jProgressBar.setToolTipText(event.getTooltip());

	}

	public void onEvent(final ReferencniBodSeZmenilEvent event) {
		referecniBod = event.wgs;
		nastavZakladyDoComboboxu(true);
		// Adresa se hledá až po zastavení mapy, ne při každém posunu.
		zpozdeniGeocodingu.restart();
	}

	private void hledejAdresu() {
		if (referecniBod == null) {
			return;
		}
		geocodingModel.spustHledani(referecniBod, new RefreshorVysledkuHledani<Nalezenec>() {

			private SortedMap<String, String> patsPureFileName;
			private SortedMap<String, String> patsFolderName;

			@Override
			public void refreshVysledekHledani(final VysledekHledani<Nalezenec> vysledekHledani) {
				patsPureFileName = new TreeMap<>();
				patsFolderName = new TreeMap<>();
				if (vysledekHledani.nalezenci != null) {
					int poradi = 0;
					for (final Nalezenec nalezenec : vysledekHledani.nalezenci) {
						polozkuDoObou(poradi, "A20-geocoding", spoj(nalezenec.administrativeArea, nalezenec.subAdministrativeArea, nalezenec.locality, nalezenec.thoroughfare));
						polozkuDoObou(poradi, "A22-geocoding", spoj(nalezenec.administrativeArea, nalezenec.subAdministrativeArea, nalezenec.locality));
						polozkuDoObou(poradi, "A26-geocoding", spoj(nalezenec.administrativeArea, nalezenec.subAdministrativeArea));
						polozkuDoObou(poradi, "A28-geocoding", spoj(nalezenec.administrativeArea));
						polozkuDoObou(poradi, "A30-geocoding", spoj(nalezenec.subAdministrativeArea, nalezenec.locality, nalezenec.thoroughfare));
						polozkuDoObou(poradi, "A32-geocoding", spoj(nalezenec.subAdministrativeArea, nalezenec.locality));
						polozkuDoObou(poradi, "A36-geocoding", spoj(nalezenec.subAdministrativeArea));
						polozkuDoObou(poradi, "A88-geocoding", nalezenec.adresa);
						poradi++;
					}
					jKmzFolderNazevCombo.addPatterns(null, patsFolderName);
					jPureJmenoSouboruCombo.addPatterns(null, patsPureFileName);
				}
			}

			private void polozkuDoObou(final int poradi, final String klicek, final String textik) {
				patsPureFileName.put(klicek + poradi, FUtil.vycistiJmenoSouboru(textik));
				patsFolderName.put(klicek + poradi, textik);
			}

			private String spoj(final String... jmena) {
				final StringBuilder sb = new StringBuilder();
				boolean prvni = true;
				for (final String jmeno : jmena) {
					if (jmeno == null || jmeno.isEmpty()) {
						continue;
					}
					if (!prvni) {
						sb.append(", ");
					}
					sb.append(jmeno);
					prvni = false;
				}
				return sb.toString();
			}
		});
	}

	@Override
	protected String getTemaNapovedyDialogu() {
		return "Render";
	}

	@Override
	protected void initComponents() {
		// Napřed registrovat, aby při inicializaci už byl výsledek tady
		getRootPane().setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		final Box box = Box.createVerticalBox();
		add(box);

		jSrovnatDoSeveru = new JCheckBox();
		jSrovnatDoSeveru.setText("Srovnat do severu");
		jNastaveniAktualnihoMeritkaButton = new JButton("Nastavit na měřítko");
		jNastavovecMeritka = new JNastavovecMeritka();
		jTerenniRozmerField = new JTextField();
		jTerenniRozmerField.setEditable(false);

		final JPanel jPanMeritko = new JPanel(new BorderLayout());
		jPanMeritko.setBorder(createBorder("Měřítko"));

		jPanMeritko.setName("Měřítko");
		jPanMeritko.add(createMoumerNahled());

		final JPanel meritkoveNastavovace = new JPanel();
		meritkoveNastavovace.add(jSrovnatDoSeveru);
		meritkoveNastavovace.add(new JLabel("Měřítko:"));
		meritkoveNastavovace.add(jNastavovecMeritka);
		meritkoveNastavovace.add(jNastaveniAktualnihoMeritkaButton);
		meritkoveNastavovace.add(jTerenniRozmerField);

		jPanMeritko.add(meritkoveNastavovace, BorderLayout.SOUTH);

		box.add(jPanMeritko);

		final Box jNastaTypu = Box.createHorizontalBox();

		// box.add(Box.createVerticalStrut(10));
		createrWhatRender();
		jNastaTypu.add(jWhatRenderRadioPanel);
		// box.add(Box.createVerticalStrut(10));
		createImgType();
		jNastaTypu.add(jImgTypeRadioPanel);
		// box.add(Box.createVerticalStrut(5));
		box.add(jNastaTypu);

		box.add(Box.createVerticalStrut(5));

		initOziComponents();
		box.add(jOziPanel);

		// KMZ speciality
		intKmzComponents();
		box.add(jKmzPanel);

		box.add(Box.createVerticalGlue());

		final Box jVystup = Box.createVerticalBox();
		jVystup.setBorder(createBorder("Výstup"));

		jVystup.add(initVystupComponents());

		// spouštěcí a zastavovací tlačítka
		jSpustitButton = new JButton("Vyberte nějaký výřez");
		jSpustitButton.setEnabled(false);
		jSpustitButton.setAlignmentX(CENTER_ALIGNMENT);
		jPrerusitButton = new JButton("Přerušit rendrování");
		jPrerusitButton.setEnabled(false);
		jPrerusitButton.setVisible(false);
		jPrerusitButton.setAlignmentX(CENTER_ALIGNMENT);
		jProgressBar = new JProgressBar();
		jVystup.add(jSpustitButton);
		jVystup.add(jProgressBar);
		jVystup.add(jPrerusitButton);
		box.add(jVystup);

		add(box);

	}

	private void createImgType() {
		final SelectionModel<EImageType> whrm = new SelectionModel<>();
		whrm.add(EImageType.bmp, "<html><i>BMP</i> - nekomprimovaný obrázek (pro volný OziExplorer).");
		whrm.add(EImageType.jpg, "<html><i>JPG</i> - ztrátová komprimace, vhodné pro fotky, nutné pro Garmin.");
		whrm.add(EImageType.png, "<html><i>PNG</i> - bezeztrátová komprimace, umožňuje průhlednost.");
		jImgTypeRadioPanel = new JMvRadioPanel<>("Typ obrázku");
		jImgTypeRadioPanel.setBorder(createBorder("Typ obrázku"));
		jImgTypeRadioPanel.setSelectionModel(whrm);
		jImgTypeRadioPanel.setAlignmentX(0.5f);
	}

	private JComponent createMoumerNahled() {
		JComponent nahled;
		// JComponent sv = new JPrekryvnik();
		nahled = factory.init(new JRenderNahledPrekryvnik());
		// detailRoh.setBackground(new Color(0,255,120));
		nahled.setMinimumSize(new Dimension(100, 100));
		nahled.setPreferredSize(new Dimension(200, 200));
		nahled.setMaximumSize(new Dimension(300, 300));

		final JKachlovnik nahledKachlovnik = new JRenderNahledKachlovnik();
		nahled.add(nahledKachlovnik);
		factory.init(nahledKachlovnik);
		nahled.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.RAISED));
		nahled.setAlignmentX(0.5f);
		return nahled;
	}

	private void createrWhatRender() {
		final SelectionModel<EWhatRender> whrm = new SelectionModel<>();
		whrm.add(EWhatRender.TISK, "<html><i>Tisk</i> - přímý tisk na tiskárnu (beta)");
		whrm.add(EWhatRender.JEN_OBRAZEK, "<html><i>Obrázek mapy</i> - pro tisk nebo prohlížení");
		whrm.add(EWhatRender.OZI_EXPLORER, "<html><i>OziExplorer</i> - obrázek a kalibrační map soubor.");
		whrm.add(EWhatRender.GOOGLE_EARTH, "<html><i>KMZ</i> soubor pro Google Earth nebo Garmin Oregon.");
		jWhatRenderRadioPanel = new JMvRadioPanel<>("Co rendrovat");
		jWhatRenderRadioPanel.setBorder(createBorder("Co rendrovat"));
		jWhatRenderRadioPanel.setSelectionModel(whrm);
		jWhatRenderRadioPanel.setAlignmentX(0.5f);
	}

	/**
	 * Dlouhá cesta by roztáhla oddíl Výstup a ostatní popisky by se nevešly. Zkrátí ji uprostřed, kořen a poslední složky zůstanou.
	 */
	static String zkratCestu(final String cesta, final int maxZnaku) {
		if (cesta.length() <= maxZnaku) {
			return cesta;
		}
		final char oddelovac = cesta.indexOf('\\') >= 0 ? '\\' : '/';
		final int zacatek = cesta.indexOf(oddelovac, cesta.startsWith("\\\\") ? 2 : 0) + 1;
		final String koren = cesta.substring(0, zacatek);
		String konec = "";
		for (int i = cesta.lastIndexOf(oddelovac); i >= zacatek; i = cesta.lastIndexOf(oddelovac, i - 1)) {
			final String kandidat = cesta.substring(i);
			if (koren.length() + 1 + kandidat.length() > maxZnaku) {
				break;
			}
			konec = kandidat;
		}
		if (konec.isEmpty()) {
			return "…" + cesta.substring(cesta.length() - (maxZnaku - 1));
		}
		return koren + "…" + konec;
	}

	private void initOziComponents() {
		jOziPanel = new JTwoColumnsPanel("OZI Explorer");
		jKalibrBodu = new JKalibrBoduSpinner();
		jOziPanel.addx("Počet kalibračních bodů", jKalibrBodu);
	}

	private JPanel initVystupComponents() {
		final JPanel pan = new JPanel(new GridBagLayout());
		jOutputFolderLabel = new JLabel();
		// jOutputFolder.setEditable(false);
		final Dimension dm1 = getMinimumSize();
		dm1.width = 300;
		// jOutputFolder.setColumns(50);
		// jOutputFolder.setMinimumSize(dm1);
		jChangeOutputFolderButton = new JButton("Změň");
		jPureJmenoSouboruCombo = new JGeocodingComboBox();
		// Šířka podle nejdelšího názvu by oddíl roztáhla a ostatní popisky by se nevešly.
		final Dimension sirkaJmena = jPureJmenoSouboruCombo.getPreferredSize();
		sirkaJmena.width = SIRKA_JMENA_SOUBORU;
		jPureJmenoSouboruCombo.setPreferredSize(sirkaJmena);
		jPriponaSouboruLabel = new JLabel();
		jIkonkaPapiru = new JIkonkaPapiru();
		jPapirMeritkoComboBox = new JPapirMeritkoComboBox();
		jJakouHustotuLabel = new JLabel("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
		// jJakouHustotuNaPadesatku.setEditable(false);
		// jJakouHustotuNaPadesatku.setAlignmentX(CENTER_ALIGNMENT);
		final Box box1 = Box.createHorizontalBox();
		box1.add(jPapirMeritkoComboBox);
		box1.add(Box.createHorizontalStrut(5));
		box1.add(jJakouHustotuLabel);

		final Box box2 = Box.createHorizontalBox();
		box2.add(jOutputFolderLabel);
		box2.add(Box.createRigidArea(new Dimension(5, 0)));
		box2.add(jChangeOutputFolderButton);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridy = -1;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(2, 3, 2, 3);
		gbc.weightx = 0;

		gbc.gridy++;
		gbc.gridx = 0;
		pan.add(new JLabel("Papírové měřítko:"), gbc);
		gbc.gridx = 1;
		gbc.weightx = 0;
		gbc.gridwidth = 2;
		pan.add(box1, gbc);
		gbc.weightx = 0;
		gbc.gridwidth = 1;

		gbc.gridy++;
		gbc.gridx = 0;
		jVystupniSlozkaLabel = new JLabel("Výstupní složka:");
		pan.add(jVystupniSlozkaLabel, gbc);
		gbc.gridx = 1;
		gbc.weightx = 0;
		pan.add(box2, gbc);
		gbc.weightx = 0;
		gbc.gridx = 2;
		// pan.add(jChangeOutputFolder, gbc);

		gbc.gridy++;
		gbc.gridx = 0;
		jVystupniSouborLabel = new JLabel("Výstupní soubor: ");
		pan.add(jVystupniSouborLabel, gbc);
		gbc.gridx = 1;
		gbc.weightx = 0;
		pan.add(jPureJmenoSouboruCombo, gbc);
		gbc.weightx = 0;
		gbc.gridx = 2;
		pan.add(jPriponaSouboruLabel, gbc);

		gbc.gridx = 3;
		gbc.gridy = 0;
		gbc.gridheight = 3;
		pan.add(jIkonkaPapiru, gbc);

		gbc = new GridBagConstraints();
		gbc.gridx = 4;
		gbc.gridy = 0;
		gbc.gridheight = 3;
		gbc.weightx = 1.5;
		pan.add(new JLabel(), gbc);
		return pan;

	}

	private void intKmzComponents() {
		jKmzPanel = new JTwoColumnsPanel("KMZ (Google Earth či Oregon)");
		jKmzPanel.setFont(getFont().deriveFont(Font.BOLD));
		jKmzFolderNazevCombo = new JGeocodingComboBox();
		jKmzFolderDescription = new JTextField();
		jNastavovacVelikostiDlazdicX = new JNastavovacVelikostiDlazdic("vodorovném");
		jNastavovacVelikostiDlazdicY = new JNastavovacVelikostiDlazdic("svislém");
		jKmzDrawOrder = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));

		// jPanKmz = new JPanel();
		// jPanKmz.setBorder(BorderFactory.createTitledBorder("KMZ (GoogleEarth či Oregon)"));

		// jPanKmz.gbc.insets = new Insets(0, 0, 0, 0);
		jKmzPanel.addx("Název:", jKmzFolderNazevCombo);
		jKmzPanel.gbc.fill = GridBagConstraints.HORIZONTAL;
		jKmzPanel.addx("Popis:", jKmzFolderDescription);
		jKmzPanel.addx("Pořadí vykreslení:", jKmzDrawOrder);
		jKmzPanel.addx("Dlaždice X:", jNastavovacVelikostiDlazdicX);
		// jPanKmz.gbc.insets = new Insets(0, 0, 0, 0);
		jKmzPanel.addx("Dlaždice Y:", jNastavovacVelikostiDlazdicY);
	}

	private void nastavViditelnost(final EWhatRender whatRender) {
		final boolean jenOzi = whatRender == EWhatRender.OZI_EXPLORER;
		final boolean jenKmz = whatRender == EWhatRender.GOOGLE_EARTH;
		final boolean netiskneSe = whatRender != EWhatRender.TISK;
		jKmzFolderNazevCombo.setVisible(jenKmz);
		jNastavovacVelikostiDlazdicX.setVisible(jenKmz);
		jNastavovacVelikostiDlazdicY.setVisible(jenKmz);
		jKmzDrawOrder.setVisible(jenKmz);
		jKmzFolderDescription.setVisible(jenKmz);

		jKalibrBodu.setVisible(jenOzi);
		jKmzPanel.setVisible(jenKmz);
		jOziPanel.setVisible(jenOzi);

		jPureJmenoSouboruCombo.setVisible(netiskneSe);
		jOutputFolderLabel.setVisible(netiskneSe);
		jVystupniSlozkaLabel.setVisible(netiskneSe);
		jPriponaSouboruLabel.setVisible(netiskneSe);
		jChangeOutputFolderButton.setVisible(netiskneSe);
		jVystupniSouborLabel.setVisible(netiskneSe);
		jImgTypeRadioPanel.setVisible(netiskneSe);
	}

	/**
	 * @param event
	 * @return
	 */
	private void nastavZakladyDoComboboxu(final boolean smazatGeocodingPatterns) {
		if (referecniBod == null) {
			return;
		}
		final Wgs wgs = referecniBod;
		final int moumer = renderModel.getRenderedMoumer();
		{
			final SortedMap<String, String> pats = new TreeMap<>();
			pats.put("C1-wgs", wgs + " z" + moumer);
			pats.put("C2-utm", wgs.toUtm().toString() + " z" + moumer);
			pats.put("C3-vter", (wgs.lat >= 0 ? "N" : "S") + Wgs.toDdMmSsFormat(Math.abs(wgs.lat)) + " " + (wgs.lon >= 0 ? "E" : "W") + Wgs.toDdMmSsFormat(Math.abs(wgs.lon)) + " z" + moumer);
			jKmzFolderNazevCombo.addPatterns(pats, smazatGeocodingPatterns ? JGeocodingComboBox.PRAZDNE_GEOTAGGINGG_PATTERNS : null);
		}
		{
			final SortedMap<String, String> pats = new TreeMap<>();
			pats.put("C0-compact", String.format(Locale.ENGLISH, "%c%7f%c%7fz%d",
					(wgs.lat >= 0 ? 'n' : 's'), Math.abs(wgs.lat), 
					(wgs.lon >= 0 ? 'e' : 'w'), Math.abs(wgs.lon), moumer).replace(".", ""));
			pats.put("C1-wgs", wgs + " z" + moumer);
			pats.put("C2-utm", wgs.toUtm().toString());
			pats.put("C3-vter", FUtil.vycistiJmenoSouboru(
			        (wgs.lat >= 0 ? "N" : "S") + Wgs.toDdMmSsFormat(Math.abs(wgs.lat)) + " " + 
			        (wgs.lon >= 0 ? "E" : "W") + Wgs.toDdMmSsFormat(Math.abs(wgs.lon)) + " z" + moumer));
			jPureJmenoSouboruCombo.addPatterns(pats, smazatGeocodingPatterns ? JGeocodingComboBox.PRAZDNE_GEOTAGGINGG_PATTERNS : null);
		}
	}

	private void registerEvents() {

		jSpustitButton.addActionListener(e -> {
			final RenderSettings ss = renderModel.getRenderSettings().copy();
			ss.setKmzFolderDescription(jKmzFolderDescription.getText());
			renderModel.setRenderSettings(ss);
			renderModel.spustRendrovani();
		});

		jPrerusitButton.addActionListener(e -> renderModel.prerusRendrovani());

		addWindowListener(new WindowAdapter() {

			@Override
			public void windowClosed(final WindowEvent e) {
				renderModel.finishRenderingProcess();
			}

			@Override
			public void windowClosing(final WindowEvent e) {
				renderModel.finishRenderingProcess();
			}
		});

		jNastaveniAktualnihoMeritkaButton.addActionListener(e -> renderModel.uschovejAktualniMeritko());

		jWhatRenderRadioPanel.getSelectionModel().addListener(event -> {
			log.debug("Vybráno: {}", event.getSelected());
			final RenderSettings ss = renderModel.getRenderSettings().copy();
			ss.setWhatRender(event.getSelected());
			renderModel.setRenderSettings(ss);
		});

		jImgTypeRadioPanel.getSelectionModel().addListener(event -> {
			log.debug("Vybráno: {}", event.getSelected());
			final RenderSettings ss = renderModel.getRenderSettings().copy();
			ss.setImageType(event.getSelected());
			renderModel.setRenderSettings(ss);
		});

		jKmzFolderNazevCombo.addListener(patterned -> {
			final RenderSettings rs = renderModel.getRenderSettings();
			log.debug("pattern 1: {}", rs.getKmzFolder());
			log.debug("pattern 2: {}", patterned);
			rs.setKmzFolder(patterned);
			renderModel.setRenderSettings(rs);
		});

		jPureJmenoSouboruCombo.addListener(patterned -> {
			final RenderSettings rs = renderModel.getRenderSettings();
			final String vycisteneJmeno = FUtil.vycistiJmenoSouboru(patterned.getText());
			if (!patterned.getText().equals(vycisteneJmeno)) {
				patterned.setText(vycisteneJmeno);
				jPureJmenoSouboruCombo.setSelectedItem(vycisteneJmeno);
			}
			rs.setPureFileName(patterned);
			renderModel.setRenderSettings(rs);
		});

		jNastavovacVelikostiDlazdicX.addChangeListener(e -> {
			final RenderSettings settings = renderModel.getRenderSettings();
			settings.setKmzMaxDlazdiceX(jNastavovacVelikostiDlazdicX.getMaximalniVelikost());
			renderModel.setRenderSettings(settings);
		});

		jNastavovacVelikostiDlazdicY.addChangeListener(e -> {
			final RenderSettings settings = renderModel.getRenderSettings();
			settings.setKmzMaxDlazdiceY(jNastavovacVelikostiDlazdicY.getMaximalniVelikost());
			renderModel.setRenderSettings(settings);
		});

		jSrovnatDoSeveru.addChangeListener(e -> {
			final RenderSettings settings = renderModel.getRenderSettings();
			settings.setSrovnatDoSeveru(jSrovnatDoSeveru.isSelected());
			renderModel.setRenderSettings(settings);
		});

		jKmzDrawOrder.addChangeListener(e -> {
			final RenderSettings settings = renderModel.getRenderSettings();
			settings.setKmzDrawOrder((Integer) jKmzDrawOrder.getValue());
			renderModel.setRenderSettings(settings);
		});

		jPapirMeritkoComboBox.addItemListener(e -> {
			final RenderSettings settings = renderModel.getRenderSettings();
			settings.setPapiroveMeritko(jPapirMeritkoComboBox.getMeritko());
			renderModel.setRenderSettings(settings);
		});

		jKalibrBodu.addChangeListener(e -> {
			final RenderSettings settings = renderModel.getRenderSettings();
			settings.setKalibrBodu((Integer) jKalibrBodu.getValue());
			renderModel.setRenderSettings(settings);
		});

	}

	private ESouborPanelName urciFokusovanouSlozku(final RenderSettings renderSettings) {
		switch (renderSettings.getWhatRender()) {
		case GOOGLE_EARTH:
			return ESouborPanelName.KMZ;
		case OZI_EXPLORER:
			return ESouborPanelName.OZI;
		case JEN_OBRAZEK:
			return ESouborPanelName.PICTURE;
		default:
			return null;
		}
	}

	private String urciPriponuSouboru(final RenderSettings renderSettings) {
		switch (renderSettings.getWhatRender()) {
		case GOOGLE_EARTH:
			return "kmz";
		case OZI_EXPLORER:
			return "map";
		case JEN_OBRAZEK:
			return renderSettings.getImageType().getType();
		default:
			return "???";
		}
	}

	private static javax.swing.Timer jednorazovyCasovac(final int ms, final Runnable akce) {
		final javax.swing.Timer casovac = new javax.swing.Timer(ms, e -> akce.run());
		casovac.setRepeats(false);
		return casovac;
	}
}
