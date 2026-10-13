/*
 * Created on 29.3.2005
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package cz.geokuk.util.file;

import java.io.*;

/**
 *
 * Obalí řádek zadaným prefixem či suffixem.
 */
public class LineWrappingDecorationWriter extends LineDecorationWriter {

	private String iPrefix;
	private String iSuffix;

	/**
	 * @param out
	 */
	public LineWrappingDecorationWriter(final Writer out) {
		super(out);
	}

	/**
	 * @return
	 */
	public String getPrefix() {
		return iPrefix;
	}

	/**
	 * @return
	 */
	public String getSuffix() {
		return iSuffix;
	}

	/**
	 * @param aString
	 */
	public void setPrefix(final String aString) {
		iPrefix = aString;
	}

	/**
	 * @param aString
	 */
	public void setSuffix(final String aString) {
		iSuffix = aString;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see cz.tconsult.tw.util.LineDecorationWriter#onLineBeg()
	 */
	@Override
	protected void onLineBeg() throws IOException {
		if (iPrefix != null) {
			super.out.write(iPrefix);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see cz.tconsult.tw.util.LineDecorationWriter#onLineEnd()
	 */
	@Override
	protected void onLineEnd() throws IOException {
		if (iSuffix != null) {
			super.out.write(iSuffix);
		}
	}

}
