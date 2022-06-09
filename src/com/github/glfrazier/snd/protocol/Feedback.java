package com.github.glfrazier.snd.protocol;

public enum Feedback {
	/** The server decided that the transaction was inappropriate. */
	BAD,
	/** The server decided that the transaction was NOT inappropriate. */
	NOT_BAD,
	/** The server decided that the transaction was actively good. */
	GOOD,
	/**
	 * The transaction never transpired, or for some other reason was never
	 * adjudged.
	 */
	NOOP
}
