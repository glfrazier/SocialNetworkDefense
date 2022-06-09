package com.github.glfrazier.snd.protocol.message;

import java.io.Serializable;
import java.net.InetAddress;

import com.github.glfrazier.event.Event;

public class ClientAppMessage extends Message implements Serializable, Event {

	//public static final ClientAppMessage CLIENT_APP_MESSAGE = new ClientAppMessage(null, null, null);

	private static final long serialVersionUID = 1L;
	private Serializable content;

	public ClientAppMessage(InetAddress server, InetAddress client, Serializable body) {
		super(server, client, MessageType.CLIENT_TO_SERVER);
		this.content = body;
	}

	public Serializable getContent() {
		return content;
	}

	public String toString() {
		return super.toString() + " \"" + content.toString() + "\"";
	}

}
