package cz.geokuk.util.exception;

import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.Test;

/** Došlou paměť pozná handler i zabalenou do výjimky z práce na pozadí. */
public class MyExceptionHandlerTest {

	@Test
	public void najdeOomZabalenouVPraciNaPozadi() {
		final OutOfMemoryError oome = new OutOfMemoryError("Java heap space");
		Assert.assertSame(oome, MyExceptionHandler.najdiOom(new RuntimeException("Chyba při práci na pozadí", new ExecutionException(oome))));
		Assert.assertSame(oome, MyExceptionHandler.najdiOom(oome));
	}

	@Test
	public void jinaChybaNeniOom() {
		Assert.assertNull(MyExceptionHandler.najdiOom(new RuntimeException(new IllegalStateException())));
		Assert.assertNull(MyExceptionHandler.najdiOom(null));
	}

	@Test(timeout = 5000)
	public void zacyklenePricinyNezamrznou() {
		final RuntimeException a = new RuntimeException("a");
		final RuntimeException b = new RuntimeException("b", a);
		a.initCause(b);
		Assert.assertNull(MyExceptionHandler.najdiOom(a));
	}
}
