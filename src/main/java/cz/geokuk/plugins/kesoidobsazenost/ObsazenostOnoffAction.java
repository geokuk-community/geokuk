package cz.geokuk.plugins.kesoidobsazenost;

import java.awt.event.KeyEvent;

import javax.swing.KeyStroke;

import cz.geokuk.framework.ToggleAction0;

public class ObsazenostOnoffAction extends ToggleAction0 {

	private static final long serialVersionUID = -7547868179813232769L;
	private ObsazenostModel popiskyModel;

	public ObsazenostOnoffAction() {
		super("Obsazenost 161 m");
		putValue(SHORT_DESCRIPTION, "Zobrazí na mapě oblasti, které jsou již obsazené kešemi a kde nemohou vzniknout další keše.");
		putValue(MNEMONIC_KEY, KeyEvent.VK_O);
		putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0));

	}

	public void inject(final ObsazenostModel popiskyModel) {
		this.popiskyModel = popiskyModel;
	}

	public void onEvent(final ObsazenostOnoffEvent event) {
		setSelected(event.isOnoff());
	}

	@Override
	protected void onSlectedChange(final boolean nastaveno) {
		popiskyModel.visible.setOnoff(nastaveno);
	}

}
