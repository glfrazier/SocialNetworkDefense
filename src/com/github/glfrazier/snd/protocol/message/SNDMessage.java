package com.github.glfrazier.snd.protocol.message;

import java.net.InetAddress;

import com.github.glfrazier.snd.protocol.IntroductionRequest;

/**
 * The messages that comprise the Social Network Dynamics Protocol (SNDP).
 *
 */
public class SNDMessage extends Message {

	private static final long serialVersionUID = 1L;

	private final IntroductionRequest req;

	public SNDMessage(InetAddress dst, InetAddress src, IntroductionRequest req, MessageType type) {
		super(dst, src, type);
		this.req = req;
	}

	public IntroductionRequest getIntroductionRequest() {
		return req;
	}

	@Override
	public String toString() {
		return super.toString() + "(" + req + ")";
	}

//	public static class IntroSequenceID implements Serializable {
//
//		private static final long serialVersionUID = 1L;
//		private final InetAddress dst;
//		private final InetAddress src;
//		private final long l;
//
//		public IntroSequenceID(InetAddress dst, InetAddress src) {
//			l = INDEX.getAndIncrement();
//			this.dst = dst;
//			this.src = src;
//		}
//
//		@Override
//		public int hashCode() {
//			return Long.hashCode(l) + dst.hashCode() + src.hashCode();
//		}
//
//		@Override
//		public boolean equals(Object o) {
//			if (!(o instanceof IntroSequenceID)) {
//				return false;
//			}
//			IntroSequenceID uid = (IntroSequenceID)o;
//			return l == uid.l && dst.equals(uid.dst) && src.equals(uid.src);
//		}
//
//		@Override
//		public String toString() {
//			return "UUID:" + dst + "<==" + src + "(" + l + ")";
//		}
//	}

}
