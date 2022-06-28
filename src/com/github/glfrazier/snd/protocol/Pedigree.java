package com.github.glfrazier.snd.protocol;

import static com.github.glfrazier.snd.util.AddressUtils.addrToString;
import java.io.Serializable;
import java.net.InetAddress;

public class Pedigree implements Serializable {

	private static final long serialVersionUID = 1L;

	public final InetAddress entity;

	private IntroductionRequest[] requests;

	private transient String stringValue;

	public Pedigree(InetAddress entity) {
		this.entity = entity;
		this.requests = new IntroductionRequest[0];
	}

	public Pedigree getNext(IntroductionRequest request) {
		Pedigree p = new Pedigree(entity);
		p.requests = new IntroductionRequest[this.requests.length + 1];
		for (int i = 0; i < this.requests.length; i++) {
			p.requests[i] = this.requests[i];
		}
		p.requests[p.requests.length - 1] = request;
		return p;
	}

	/**
	 * Get the sequence of requests by which the requester reached this node.
	 * 
	 */
	public IntroductionRequest[] getRequestSequence() {
		return requests;
	}

	public InetAddress getSubject() {
		return entity;
	}

	public String toString() {
		if (stringValue == null) {
			StringBuffer b = new StringBuffer(addrToString(entity));
			b.append('{');
			for (int i = 0; i < requests.length; i++) {
				if (i > 0) {
					b.append(',');
				}
				b.append(requests[i]);
			}
			stringValue = b.append('}').toString();
		}
		return stringValue;
	}

}
