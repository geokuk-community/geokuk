package cz.geokuk.core.lookandfeel;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.UIManager;

/** Texty standardních dialogů Swingu, které Java česky nemá. */
public final class CeskeTexty {

	static final Map<String, Object> TEXTY = new LinkedHashMap<>();

	static {
		TEXTY.put("OptionPane.yesButtonText", "Ano");
		TEXTY.put("OptionPane.noButtonText", "Ne");
		TEXTY.put("OptionPane.okButtonText", "OK");
		TEXTY.put("OptionPane.cancelButtonText", "Zrušit");
		TEXTY.put("OptionPane.yesButtonMnemonic", "65");
		TEXTY.put("OptionPane.noButtonMnemonic", "78");
		TEXTY.put("OptionPane.okButtonMnemonic", "0");
		TEXTY.put("OptionPane.cancelButtonMnemonic", "90");
		TEXTY.put("OptionPane.titleText", "Vyberte možnost");
		TEXTY.put("OptionPane.inputDialogTitle", "Zadání");
		TEXTY.put("OptionPane.messageDialogTitle", "Zpráva");

		TEXTY.put("FileChooser.openButtonText", "Otevřít");
		TEXTY.put("FileChooser.saveButtonText", "Uložit");
		TEXTY.put("FileChooser.cancelButtonText", "Zrušit");
		TEXTY.put("FileChooser.updateButtonText", "Obnovit");
		TEXTY.put("FileChooser.helpButtonText", "Nápověda");
		TEXTY.put("FileChooser.directoryOpenButtonText", "Otevřít");
		TEXTY.put("FileChooser.openButtonMnemonic", "0");
		TEXTY.put("FileChooser.saveButtonMnemonic", "0");
		TEXTY.put("FileChooser.cancelButtonMnemonic", "0");
		TEXTY.put("FileChooser.updateButtonMnemonic", "0");
		TEXTY.put("FileChooser.helpButtonMnemonic", "0");
		TEXTY.put("FileChooser.directoryOpenButtonMnemonic", "0");
		TEXTY.put("FileChooser.openButtonToolTipText", "Otevřít vybraný soubor");
		TEXTY.put("FileChooser.saveButtonToolTipText", "Uložit vybraný soubor");
		TEXTY.put("FileChooser.cancelButtonToolTipText", "Zavřít dialog bez výběru");
		TEXTY.put("FileChooser.updateButtonToolTipText", "Obnovit seznam souborů");
		TEXTY.put("FileChooser.helpButtonToolTipText", "Nápověda k výběru souboru");
		TEXTY.put("FileChooser.directoryOpenButtonToolTipText", "Otevřít vybranou složku");
		TEXTY.put("FileChooser.openDialogTitleText", "Otevřít");
		TEXTY.put("FileChooser.saveDialogTitleText", "Uložit");
		TEXTY.put("FileChooser.acceptAllFileFilterText", "Všechny soubory");
		TEXTY.put("FileChooser.lookInLabelText", "Hledat v:");
		TEXTY.put("FileChooser.saveInLabelText", "Uložit do:");
		TEXTY.put("FileChooser.fileNameLabelText", "Název souboru:");
		TEXTY.put("FileChooser.folderNameLabelText", "Název složky:");
		TEXTY.put("FileChooser.filesOfTypeLabelText", "Typ souborů:");
		TEXTY.put("FileChooser.upFolderToolTipText", "O úroveň výš");
		TEXTY.put("FileChooser.upFolderAccessibleName", "Nahoru");
		TEXTY.put("FileChooser.homeFolderToolTipText", "Domovská složka");
		TEXTY.put("FileChooser.homeFolderAccessibleName", "Domů");
		TEXTY.put("FileChooser.newFolderToolTipText", "Vytvořit novou složku");
		TEXTY.put("FileChooser.newFolderAccessibleName", "Nová složka");
		TEXTY.put("FileChooser.newFolderErrorText", "Složku nelze vytvořit");
		TEXTY.put("FileChooser.listViewButtonToolTipText", "Seznam");
		TEXTY.put("FileChooser.listViewButtonAccessibleName", "Seznam");
		TEXTY.put("FileChooser.detailsViewButtonToolTipText", "Podrobnosti");
		TEXTY.put("FileChooser.detailsViewButtonAccessibleName", "Podrobnosti");
		TEXTY.put("FileChooser.viewMenuButtonToolTipText", "Zobrazení");
		TEXTY.put("FileChooser.viewMenuButtonAccessibleName", "Zobrazení");
		TEXTY.put("FileChooser.viewMenuLabelText", "Zobrazení");
		TEXTY.put("FileChooser.refreshActionLabelText", "Obnovit");
		TEXTY.put("FileChooser.newFolderActionLabelText", "Nová složka");
		TEXTY.put("FileChooser.listViewActionLabelText", "Seznam");
		TEXTY.put("FileChooser.detailsViewActionLabelText", "Podrobnosti");
		TEXTY.put("FileChooser.fileNameHeaderText", "Název");
		TEXTY.put("FileChooser.fileSizeHeaderText", "Velikost");
		TEXTY.put("FileChooser.fileTypeHeaderText", "Typ");
		TEXTY.put("FileChooser.fileDateHeaderText", "Změněno");
		TEXTY.put("FileChooser.fileAttrHeaderText", "Atributy");

		TEXTY.put("ColorChooser.okText", "OK");
		TEXTY.put("ColorChooser.cancelText", "Zrušit");
		TEXTY.put("ColorChooser.resetText", "Obnovit");
		TEXTY.put("ColorChooser.previewText", "Náhled");
		TEXTY.put("ColorChooser.sampleText", "Ukázkový text");
		TEXTY.put("ColorChooser.swatchesNameText", "Vzorník");
		TEXTY.put("ColorChooser.swatchesRecentText", "Naposledy:");

		TEXTY.put("ProgressMonitor.progressText", "Průběh...");
	}

	public static void nastav() {
		TEXTY.forEach(UIManager::put);
	}

	private CeskeTexty() {}
}
