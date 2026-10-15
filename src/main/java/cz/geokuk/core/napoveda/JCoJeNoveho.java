package cz.geokuk.core.napoveda;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

import cz.geokuk.core.program.FConst;
import cz.geokuk.framework.Dlg;
import cz.geokuk.util.process.BrowserOpener;
import lombok.extern.slf4j.Slf4j;

/** Co je nového: posuvný text s popisy verzí a klikacími odkazy. */
@Slf4j
public class JCoJeNoveho extends JPanel {

	private static final long serialVersionUID = 1L;

	static final String NACITANI = "<html><body><p>Načítám popis nové verze…</p></body></html>";

	private final JEditorPane text = new JEditorPane();
	private String html = "";

	public JCoJeNoveho() {
		super(new BorderLayout());
		final HTMLEditorKit kit = new HTMLEditorKit();
		final StyleSheet styly = new StyleSheet();
		styly.addStyleSheet(kit.getStyleSheet());
		final Font pismo = UIManager.getFont("Label.font");
		styly.addRule("body { font-family: '" + (pismo == null ? Font.SANS_SERIF : pismo.getFamily()) + "'; font-size: " + (pismo == null ? 12 : pismo.getSize())
				+ "pt; margin: 8px 12px; }");
		styly.addRule("h2 { font-size: 150%; color: #1f5f8b; margin-top: 14px; margin-bottom: 2px; }");
		styly.addRule("h3 { font-size: 110%; margin-top: 8px; margin-bottom: 2px; }");
		styly.addRule("ul { margin-left: 18px; margin-top: 2px; }");
		styly.addRule("li { margin-bottom: 3px; }");
		styly.addRule("code { font-size: 95%; }");
		kit.setStyleSheet(styly);
		text.setEditorKit(kit);
		text.setEditable(false);
		text.addHyperlinkListener(e -> {
			if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null) {
				BrowserOpener.displayURL(e.getURL());
			}
		});
		final JScrollPane posuv = new JScrollPane(text);
		posuv.getVerticalScrollBar().setUnitIncrement(16);
		add(posuv, BorderLayout.CENTER);
		setPreferredSize(new Dimension(680, 560));
	}

	public void setHtml(final String html) {
		this.html = html;
		text.setText(html);
		text.setCaretPosition(0);
	}

	String getHtml() {
		return html;
	}

	/** Popisy verzí přibalené v programu (Nápověda > Co je nového). */
	void zProgramu() {
		final List<CoJeNoveho.Vydani> vydani = CoJeNoveho.zProgramu();
		setHtml(vydani.isEmpty() ? "<html><body><p>Popis verzí v programu chybí.</p></body></html>" : CoJeNoveho.html(vydani));
	}

	/** Popisy verzí od používané po novou z vydání na GitHubu; stahuje mimo EDT. */
	void zGitHubu(final Callable<String> stahni, final String pouzivana, final String nova, final boolean betaKanal) {
		setHtml(NACITANI);
		new SwingWorker<List<CoJeNoveho.Vydani>, Void>() {
			@Override
			protected List<CoJeNoveho.Vydani> doInBackground() throws Exception {
				return CoJeNoveho.mezi(CoJeNoveho.zGitHubu(stahni.call()), pouzivana, nova, betaKanal);
			}

			@Override
			protected void done() {
				try {
					final List<CoJeNoveho.Vydani> vydani = get();
					setHtml(vydani.isEmpty() ? chyba(nova) : CoJeNoveho.html(vydani));
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (final ExecutionException e) {
					log.warn("Popis vydání nejde načíst: {}", e.getCause().toString());
					setHtml(chyba(nova));
				}
			}
		}.execute();
	}

	static String chyba(final String nova) {
		return "<html><body><p>Popis nové verze se nepodařilo načíst.</p><p><a href=\"" + FConst.RELEASE_TAG_URL + CoJeNoveho.escape(nova)
				+ "\">Zobrazit popis verze " + CoJeNoveho.escape(nova) + " na webu</a></p></body></html>";
	}

	static JDialog dialog(final Window vlastnik, final JCoJeNoveho obsah) {
		final JDialog d = new JDialog(vlastnik, "Co je nového", Dialog.ModalityType.APPLICATION_MODAL);
		final JButton zavrit = new JButton("Zavřít");
		zavrit.addActionListener(e -> d.dispose());
		final JPanel tlacitka = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		tlacitka.add(zavrit);
		d.getContentPane().add(obsah, BorderLayout.CENTER);
		d.getContentPane().add(tlacitka, BorderLayout.SOUTH);
		d.getRootPane().setDefaultButton(zavrit);
		d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
		d.pack();
		d.setLocationRelativeTo(vlastnik);
		return d;
	}

	/** Nápověda > Co je nového. */
	public static void ukazZProgramu() {
		final JCoJeNoveho obsah = new JCoJeNoveho();
		obsah.zProgramu();
		dialog(Dlg.parentFrame(), obsah).setVisible(true);
	}

	/** Z okna nové verze: co přinesou verze od používané po nabízenou. */
	public static void ukazZGitHubu(final Window vlastnik, final String nova, final boolean betaKanal) {
		final JCoJeNoveho obsah = new JCoJeNoveho();
		obsah.zGitHubu(() -> stahni(FConst.RELEASES_API_URL + "?per_page=100"), FConst.VERSION, nova, betaKanal);
		dialog(vlastnik, obsah).setVisible(true);
	}

	private static String stahni(final String url) throws IOException {
		final java.net.URLConnection c = new java.net.URL(url).openConnection();
		c.setRequestProperty("User-Agent", "Geokuk/" + FConst.VERSION + " (" + FConst.WEB_PAGE_URL + ")");
		c.setRequestProperty("Accept", "application/vnd.github+json");
		c.setConnectTimeout(30000);
		c.setReadTimeout(30000);
		try (java.util.Scanner sc = new java.util.Scanner(c.getInputStream(), "UTF-8").useDelimiter("\\A")) {
			return sc.hasNext() ? sc.next() : "";
		}
	}
}
