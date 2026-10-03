package cz.geokuk.core.lookandfeel;

import static org.junit.Assert.assertEquals;

import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;

import org.junit.Test;

public class CeskeTextyTest {

	@Test
	public void textyPlatiIPoZmeneVzhledu() throws Exception {
		final String puvodni = UIManager.getLookAndFeel().getClass().getName();
		try {
			UIManager.setLookAndFeel(new MetalLookAndFeel());
			CeskeTexty.nastav();
			UIManager.setLookAndFeel(new NimbusLookAndFeel());
			assertEquals("Zrušit", UIManager.getString("OptionPane.cancelButtonText"));
			assertEquals("Ano", UIManager.getString("OptionPane.yesButtonText"));
			assertEquals("Uložit", UIManager.getString("FileChooser.saveButtonText"));
			assertEquals("Hledat v:", UIManager.getString("FileChooser.lookInLabelText"));
		} finally {
			UIManager.setLookAndFeel(puvodni);
		}
	}
}
