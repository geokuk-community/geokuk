package cz.geokuk.util.exception;

import org.junit.Assert;
import org.junit.Test;

public class JErrorDialogTest {

	@Test
	public void bezUlozenehoVypisuNeniCoZobrazit() {
		Assert.assertNull(JErrorDialog.vypis(null));
		Assert.assertNull(JErrorDialog.vypis(AExcId.from("exc9z9")));
	}
}
