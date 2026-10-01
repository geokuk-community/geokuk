package cz.geokuk.core.program;

import ch.qos.logback.core.PropertyDefinerBase;

/** Složka logu pro {@code logback.xml}. */
public class SlozkaLogu extends PropertyDefinerBase {

	@Override
	public String getPropertyValue() {
		return UmisteniProgramu.log().getPath();
	}
}
