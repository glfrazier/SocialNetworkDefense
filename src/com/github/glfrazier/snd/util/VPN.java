package com.github.glfrazier.snd.util;

import java.io.IOException;
import java.net.InetAddress;

import com.github.glfrazier.snd.protocol.message.Message;

public interface VPN {
	
	public void send(Message m) throws IOException;
	
	public InetAddress getRemote();
	
	public void close();

}
