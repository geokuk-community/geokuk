package cz.geokuk.plugins.geocoding;

import java.awt.event.ActionEvent;
import java.net.MalformedURLException;
import java.net.URL;

import cz.geokuk.framework.Action0;
import cz.geokuk.util.process.BrowserOpener;

/** Otevře místo v prohlížeči na webu s mapou. */
public class OtevritVMapeAction extends Action0 {

	private static final long serialVersionUID = 5841957130264417719L;
	private final String url;

	public OtevritVMapeAction(final OdkazyNaMapy.Odkaz odkaz) {
		super(odkaz.nazev);
		putValue(SHORT_DESCRIPTION, "Otevře místo v prohlížeči na webu " + odkaz.nazev + ".");
		this.url = odkaz.url;
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		try {
			BrowserOpener.displayURL(new URL(url));
		} catch (final MalformedURLException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
