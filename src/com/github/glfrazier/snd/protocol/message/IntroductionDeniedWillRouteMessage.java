package com.github.glfrazier.snd.protocol.message;

import static com.github.glfrazier.snd.protocol.IntroductionRequest.SAMPLE_INTRODUCTION_REQUEST;
import java.io.Serializable;
import java.net.InetAddress;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.protocol.IntroductionRequest;

/**
 * The entity from whom an introduction was requested will not make the
 * introduction, but is a route to the destination. Thus, by virtue of having a
 * VPN to the entity, the client can begin to transmit application messages to
 * the destination, using the denying entity as a route to the destination.
 * 
 * @author Greg Frazier
 *
 */
public class IntroductionDeniedWillRouteMessage extends SNDMessage implements Serializable, Event {

	/**
	 * 
	 */
	public static final IntroductionDeniedWillRouteMessage SAMPLE_INTRODUCTION_DENIED_WILL_ROUTE = new IntroductionDeniedWillRouteMessage(
			SAMPLE_INTRODUCTION_REQUEST);

	private static final long serialVersionUID = 1L;

	public IntroductionDeniedWillRouteMessage(IntroductionRequest req) {
		// refused is sent from target to introducer
		super(req.requester, // dst
				req.introducer, // src
				req, MessageType.INTRODUCTION_DENIED_WILL_ROUTE);
	}

}
