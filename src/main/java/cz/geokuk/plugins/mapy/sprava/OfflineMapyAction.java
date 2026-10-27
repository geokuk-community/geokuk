package cz.geokuk.plugins.mapy.sprava;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import cz.geokuk.framework.Action0;

/** Mapy > Správce offline map…: otevře panel stažených map a map ke stažení, klávesnicí i myší. */
public class OfflineMapyAction extends Action0 {

	private static final long serialVersionUID = 1L;

	static final String NAZEV = "Správce offline map…";

	private SpravaMapModel spravaMap;

	public OfflineMapyAction() {
		super(NAZEV);
		putValue(SHORT_DESCRIPTION, "Stažené mapy, mapy ke stažení a jejich stahování.");
		putValue(MNEMONIC_KEY, KeyEvent.VK_S);
	}

	public void inject(final SpravaMapModel spravaMap) {
		this.spravaMap = spravaMap;
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		spravaMap.otevriPanel();
	}
}
