package com.github.glfrazier.snd.protocol.message;

import java.io.Serializable;
import java.net.InetAddress;

import com.github.glfrazier.event.Event;

public class ServerAppMessage extends Message implements Serializable, Event {

	public static final ServerAppMessage ACKNACK = new ServerAppMessage(null, null, null);

	private static final long serialVersionUID = 1L;
	private Serializable content;

	public ServerAppMessage(InetAddress client, InetAddress server, Serializable content) {
		super(client, server, MessageType.SERVER_TO_CLIENT);
		this.content = content;
	}

	public Serializable getContent() {
		return content;
	}

	@Override
	public String toString() {
		return super.toString() + " \"" + content.toString() + "\"";
	}

}
