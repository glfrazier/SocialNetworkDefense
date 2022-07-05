package com.github.glfrazier.snd.util;

import com.github.glfrazier.snd.protocol.message.AcknowledgeMessage;
import com.github.glfrazier.snd.protocol.message.SNDMessage;

/**
 * A struct to hold a time value (long) with an SNDMessage.
 * It implements Comparable<> so that one can have a Set that is ordered by time.
 *
 */
public class TimeAndAcknowledgeMessage implements Comparable<TimeAndAcknowledgeMessage> {
	public long time;
	public AcknowledgeMessage message;

	public TimeAndAcknowledgeMessage(long t, AcknowledgeMessage msg) {
		time = t;
		message = msg;
	}

	@Override
	public int compareTo(TimeAndAcknowledgeMessage o) {
		return Long.compare(time, o.time);
	}
}