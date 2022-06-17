package com.github.glfrazier.snd.util;

import com.github.glfrazier.snd.protocol.IntroductionRequest;

/**
 * A struct to hold a time value (long) with an IntroductionRequest.
 *
 */
public class TimeAndIntroductionRequest {
	public long time;
	public IntroductionRequest ir;

	public TimeAndIntroductionRequest(long t, IntroductionRequest i) {
		time = t;
		ir = i;
	}
}