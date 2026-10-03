/**
 *
 */
package cz.geokuk.core.napoveda;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

import cz.geokuk.core.program.FConst;
import cz.geokuk.framework.Action0;
import cz.geokuk.util.process.BrowserOpener;

/**
 * @author Martin Veverka
 *
 */
public class ZadatProblemAction extends Action0 {

	private static final long serialVersionUID = -2882817111560336824L;
	/** Delší odkaz prohlížeče nebo GitHub odmítnou. */
	private static final int MAX_DELKA_URL = 6000;
	private final transient Runnable dialog;

	/**
	 * @param aBoard
	 */
	public ZadatProblemAction() {
		this(null);
	}

	ZadatProblemAction(final Runnable dialog) {
		super("Zadat problém ...");
		this.dialog = dialog != null ? dialog : () -> DiagnostikaAction.ukaz(getMainFrame());
		putValue(SHORT_DESCRIPTION, "Ukáže informace o programu a pak otevře stránku na GitHubu, kde jde zadat chybu nebo požadavek na novou funkci. Informace o programu budou předvyplněné.");
		putValue(MNEMONIC_KEY, KeyEvent.VK_P);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	@Override
	public void actionPerformed(final ActionEvent aE) {
		dialog.run();
	}

	static void otevri() {
		try {
			BrowserOpener.displayURL(new URL(odkaz()));
		} catch (final MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	/** Nejdřív ubírá nejstarší události a řádky logu, chyby a servisní hodnoty jsou cennější. */
	static String odkaz() {
		final int[][] limity = { { 100, 50 }, { 50, 25 }, { 25, 12 }, { 12, 6 }, { 6, 0 } };
		for (final int[] limit : limity) {
			final String url = odkazS(Diagnostika.text(limit[0], limit[1]));
			if (url.length() <= MAX_DELKA_URL) {
				return url;
			}
		}
		return odkaz(Diagnostika.text(3, 0));
	}

	static String odkaz(final String diagnostika) {
		String text = diagnostika;
		String url = odkazS(text);
		while (url.length() > MAX_DELKA_URL) {
			text = text.substring(0, text.length() * 9 / 10);
			url = odkazS(text + "\n… zkráceno");
		}
		return url;
	}

	private static String odkazS(final String diagnostika) {
		final String telo = "Co se stalo a jak to zopakovat:\n\n\n\nInformace o programu:\n```\n" + diagnostika + "\n```\n";
		try {
			return FConst.POST_PROBLEM_URL + "?body=" + URLEncoder.encode(telo, "UTF-8").replace("+", "%20");
		} catch (final UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

}
