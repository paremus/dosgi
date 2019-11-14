/*-
 * #%L
 * com.paremus.dosgi.discovery.cluster
 * %%
 * Copyright (C) 2016 - 2019 Paremus Ltd
 * %%
 * Licensed under the Fair Source License, Version 0.9 (the "License");
 * 
 * See the NOTICE.txt file distributed with this work for additional 
 * information regarding copyright ownership. You may not use this file 
 * except in compliance with the License. For usage restrictions see the 
 * LICENSE.txt file distributed with this work
 * #L%
 */
package com.paremus.dosgi.discovery.cluster.comms;

import static com.paremus.dosgi.discovery.cluster.comms.MessageType.ACKNOWLEDGMENT;
import static com.paremus.dosgi.discovery.cluster.comms.MessageType.ANNOUNCEMENT;
import static com.paremus.dosgi.discovery.cluster.comms.MessageType.REMINDER;
import static com.paremus.dosgi.discovery.cluster.comms.MessageType.REQUEST_REANNOUNCEMENT;
import static com.paremus.dosgi.discovery.cluster.comms.MessageType.REVOCATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.timeout;
import static org.osgi.framework.Constants.OBJECTCLASS;
import static org.osgi.util.converter.Converters.standardConverter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

import com.paremus.cluster.ClusterInformation;
import com.paremus.dosgi.discovery.cluster.impl.Config;
import com.paremus.dosgi.discovery.cluster.local.LocalDiscoveryListener;
import com.paremus.dosgi.discovery.cluster.remote.RemoteDiscoveryNotifier;
import com.paremus.net.info.ClusterNetworkInformation;
import com.paremus.netty.tls.ParemusNettyTLS;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;

@RunWith(MockitoJUnitRunner.class)
public class SocketCommsTest {

	private static final UUID COMMS_ID = new UUID(123,456);
	private static final UUID REMOTE_ID = new UUID(234,567);
	
	@Mock
	RemoteDiscoveryNotifier notifier;
	@Mock
	ClusterInformation clusterInformation;
	@Mock
	ParemusNettyTLS tls;
	@Mock
	ClusterNetworkInformation fni;
	@Mock
	LocalDiscoveryListener localListener;
	
	Semaphore s = new Semaphore(0);
	
	SocketComms comms;
	
	Config config;
	private NioEventLoopGroup group; 
	private EventExecutorGroup worker; 
	
	@Before
	public void setUp() throws Exception {
		
		group = new NioEventLoopGroup(1);
		
		worker = new DefaultEventExecutorGroup(1);
		
		comms =  new SocketComms(COMMS_ID, group, tls, localListener, notifier, worker);
		
		Mockito.doAnswer(i -> {
			s.release();
			return null;
		}).when(notifier).announcementEvent(Mockito.any(EndpointDescription.class), Mockito.anyInt());
		Mockito.doAnswer(i -> {
			s.release();
			return null;
		}).when(notifier).revocationEvent(Mockito.anyString(), Mockito.anyInt());
		
		Mockito.when(fni.getBindAddress()).thenReturn(InetAddress.getLoopbackAddress());
		
		config = standardConverter().convert(new HashMap<>()).to(Config.class);
	}
	
	@After
	public void tearDown() throws Exception {
		comms.destroy().sync();
		group.shutdownGracefully(10, 1000, TimeUnit.MILLISECONDS);
		worker.shutdownGracefully(10, 1000, TimeUnit.MILLISECONDS);
	}

	@Test
	public void testPublishSmallEndpoint() throws Exception {
		comms.bind(fni, config);
		try (DatagramSocket ds = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
			EndpointDescription ed = getTestEndpointDescription(true, false);
			comms.publishEndpoint(ed, 5, REMOTE_ID, (InetSocketAddress) ds.getLocalSocketAddress());
			
			ds.setSoTimeout(1000);
			DatagramPacket dp = new DatagramPacket(new byte[65535], 65535);
			ds.receive(dp);
			
			checkPlainEndpointAnnounce(ed, dp);
		}
	}

	@Test
	public void testRevokeEndpoint() throws Exception{
		comms.bind(fni, config);
		try (DatagramSocket ds = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
			EndpointDescription ed = getTestEndpointDescription(true, true);
			comms.revokeEndpoint(ed.getId(), 5, REMOTE_ID, (InetSocketAddress) ds.getLocalSocketAddress());
			
			ds.setSoTimeout(1000);
			DatagramPacket dp = new DatagramPacket(new byte[65535], 65535);
			ds.receive(dp);
			
			ByteArrayInputStream bais = new ByteArrayInputStream(dp.getData(), dp.getOffset(), dp.getLength());
			
			//Version 2, plain text
			assertEquals(2, bais.read());
			
			DataInput di = new DataInputStream(bais);
			assertEquals(MessageType.REVOCATION.ordinal(), di.readByte());
			assertEquals(ed.getId(), di.readUTF());
			assertEquals(5, di.readInt());
		}
	}

	@Test
	public void testRepublishNoAck() throws Exception {
		comms.bind(fni, config);
		try (DatagramSocket ds = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
			EndpointDescription ed = getTestEndpointDescription(true, false);
			comms.publishEndpoint(ed, 5, REMOTE_ID, (InetSocketAddress) ds.getLocalSocketAddress());
			
			ds.setSoTimeout(1000);
			DatagramPacket dp = new DatagramPacket(new byte[65535], 65535);
			ds.receive(dp);
			
			checkPlainEndpointAnnounce(ed, dp);
			
			//Wait a bit
			Thread.sleep(1000);
			//We should receive a rebroadcast within a second
			ds.receive(dp);
			checkPlainEndpointAnnounce(ed, dp);
		}
	}

	@Test
	public void testNoRepublishWhenAck() throws Exception{
		comms.bind(fni, config);
		try (DatagramSocket ds = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
			EndpointDescription ed = getTestEndpointDescription(true, false);
			comms.publishEndpoint(ed, 5, REMOTE_ID, (InetSocketAddress) ds.getLocalSocketAddress());
			
			ds.setSoTimeout(1000);
			DatagramPacket dp = new DatagramPacket(new byte[65535], 65535);
			ds.receive(dp);
			
			checkPlainEndpointAnnounce(ed, dp);

			//Ack the packet
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(baos);
			dos.writeByte(2);
			dos.writeByte(ACKNOWLEDGMENT.code());
			dos.writeLong(REMOTE_ID.getMostSignificantBits());
			dos.writeLong(REMOTE_ID.getLeastSignificantBits());
			dos.writeUTF(ed.getId());
			dos.writeInt(5);
			dos.close();
			
			dp = new DatagramPacket(baos.toByteArray(), baos.size(), InetAddress.getLoopbackAddress(), comms.getUdpPort());
			ds.send(dp);
			
			//Wait a bit
			Thread.sleep(500);
			//We should not receive a rebroadcast
			
			try {
				ds.receive(dp);
				fail("Should not rebroadcast");
			} catch(SocketTimeoutException ste) {}
		}
	}

	@Test
	public void testNoRepublishWhenStopCalling() throws Exception {
		comms.bind(fni, config);
		try (DatagramSocket ds = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
			EndpointDescription ed = getTestEndpointDescription(true, false);
			comms.publishEndpoint(ed, 5, REMOTE_ID, (InetSocketAddress) ds.getLocalSocketAddress());
			
			ds.setSoTimeout(1000);
			DatagramPacket dp = new DatagramPacket(new byte[65535], 65535);
			ds.receive(dp);
			
			checkPlainEndpointAnnounce(ed, dp);
			
			comms.stopCalling(REMOTE_ID, ds.getLocalSocketAddress());
			
			//Wait a bit
			Thread.sleep(500);
			//We should not receive a rebroadcast
			
			try {
				ds.receive(dp);
				fail("Should not rebroadcast");
			} catch(SocketTimeoutException ste) {}
		}
	}

	private void checkPlainEndpointAnnounce(EndpointDescription ed, DatagramPacket dp)
			throws IOException {
		ByteBuf buf = Unpooled.wrappedBuffer(dp.getData(), dp.getOffset(), dp.getLength());
		
		//Version 2, plain text
		assertEquals(2, buf.readByte());
		assertEquals(MessageType.ANNOUNCEMENT.code(), buf.readUnsignedByte());
		
		EndpointDescription received = EndpointSerializer.deserializeEndpoint(buf);
		assertEquals(ed.getId(), received.getId());
		assertEquals(5, buf.readInt());
		
		assertEquals(0, buf.readableBytes());
	}
	
	private EndpointDescription getTestEndpointDescription(boolean local, boolean big) {
		Map<String, Object> m = new LinkedHashMap<String, Object>();

        // required
        m.put(OBJECTCLASS, new String[]{"com.acme.HelloService", "some.other.Service"});
        m.put(RemoteConstants.ENDPOINT_FRAMEWORK_UUID, local ? COMMS_ID.toString() : REMOTE_ID.toString());
        m.put(RemoteConstants.ENDPOINT_ID, "http://myhost:8080/commands");
        m.put(RemoteConstants.ENDPOINT_SERVICE_ID, Long.valueOf(42));
        m.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, "my.config.type");
        
        if(big) {
        	for(int i = 0; i < 10; i++) {
        		m.put(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        	}
        }

        return new EndpointDescription(m);
	}
	
	@Test
	public void testDestroy() throws Exception {
		comms.bind(fni, config);
		int port = comms.getUdpPort();
		
		try (DatagramSocket ds = new DatagramSocket(port, InetAddress.getLoopbackAddress())) {
			fail("Should be taken");
		} catch (BindException be) {}
		
		comms.destroy().sync();
		
		Thread.sleep(10);
		
		try (DatagramSocket ds = new DatagramSocket(port, InetAddress.getLoopbackAddress())) {
		} catch (BindException be) {
			if(System.getenv("GITLAB_CI") != null) {
				System.out.println("The Docker build doesn't always release sockets cleanly.");
				// Docker sometimes gets in the way here.
				Thread.sleep(5000);
				try (DatagramSocket ds = new DatagramSocket(port, InetAddress.getLoopbackAddress())) {
				}
			} else {
				throw be;
			}
		}
	}

	@Test
	public void testReceiveAnnouncement() throws Exception {
		comms.bind(fni, config);
		try (DatagramSocket ds = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
			EndpointDescription ed = getTestEndpointDescription(true, false);
			
			ByteBuf buffer = Unpooled.buffer();
			buffer.writeByte(2);
			buffer.writeByte(ANNOUNCEMENT.code());
			EndpointSerializer.serialize(ed, buffer);
			buffer.writeInt(5);
			
			ds.setSoTimeout(1000);
			DatagramPacket dp = new DatagramPacket(buffer.array(), buffer.readerIndex(), buffer.readableBytes(), 
					InetAddress.getLoopbackAddress(), comms.getUdpPort());
			ds.send(dp);
			
			assertTrue(s.tryAcquire(1000, TimeUnit.MILLISECONDS));
			
			ArgumentCaptor<EndpointDescription> captor = ArgumentCaptor.forClass(EndpointDescription.class);
			Mockito.verify(notifier).announcementEvent(captor.capture(), Mockito.eq(5));
			checkPlainEndpointAnnounce(captor.getValue(), dp);
			
			dp = new DatagramPacket(new byte[65535], 65535);
			
			ds.receive(dp);
			
			try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(dp.getData(), dp.getOffset(), dp.getLength()))) {
				assertEquals(2, dis.readByte());
				assertEquals(ACKNOWLEDGMENT.ordinal(), dis.readByte());
				assertEquals(COMMS_ID, new UUID(dis.readLong(), dis.readLong()));
				assertEquals(ed.getId(), dis.readUTF());
				assertEquals(5, dis.readInt());
			}
		}
	}

	@Test
	public void testReceiveRevocation() throws Exception {
		comms.bind(fni, config);
		try (DatagramSocket ds = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
			EndpointDescription ed = getTestEndpointDescription(true, false);
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (DataOutputStream dos = new DataOutputStream(baos)) {
				dos.writeByte(2);
				dos.writeByte(REVOCATION.ordinal());
				dos.writeUTF(ed.getId());
				dos.writeInt(5);
				dos.close();
			}
			
			ds.setSoTimeout(1000);
			DatagramPacket dp = new DatagramPacket(baos.toByteArray(), baos.size(), 
					InetAddress.getLoopbackAddress(), comms.getUdpPort());
			ds.send(dp);
			
			assertTrue(s.tryAcquire(1000, TimeUnit.MILLISECONDS));
			
			Mockito.verify(notifier).revocationEvent(Mockito.eq(ed.getId()), Mockito.eq(5));
			
			dp = new DatagramPacket(new byte[65535], 65535);
			
			ds.receive(dp);
			
			try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(dp.getData(), dp.getOffset(), dp.getLength()))) {
				assertEquals(2, dis.readByte());
				assertEquals(ACKNOWLEDGMENT.ordinal(), dis.readByte());
				assertEquals(COMMS_ID, new UUID(dis.readLong(), dis.readLong()));
				assertEquals(ed.getId(), dis.readUTF());
				assertEquals(5, dis.readInt());
			}
		}
	}
	
	@Test
	public void testSendReminder() throws Exception {
		comms.bind(fni, config);
		try (DatagramSocket ds = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
			
			EndpointDescription ed = getTestEndpointDescription(true, false);
			InetSocketAddress isa = (InetSocketAddress) ds.getLocalSocketAddress();
			comms.sendReminder(Collections.singleton(ed.getId()), 7, REMOTE_ID, isa);
			
			ds.setSoTimeout(1000);
			DatagramPacket dp = new DatagramPacket(new byte[65535], 65535);
			ds.receive(dp);
			
			try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(dp.getData(), dp.getOffset(), dp.getLength()))) {
				assertEquals(2, dis.readByte());
				assertEquals(REMINDER.ordinal(), dis.readByte());
				assertEquals(COMMS_ID, new UUID(dis.readLong(), dis.readLong()));
				assertEquals(7, dis.readInt());
				assertEquals(1, dis.readUnsignedShort());
				assertEquals(ed.getId(), dis.readUTF());
			}
		}
	}

	@Test
	public void testRespondToReminder() throws Exception {
		comms.bind(fni, config);
		try (DatagramSocket ds = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
			
			EndpointDescription ed = getTestEndpointDescription(true, false);
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(baos);

			dos.writeByte(2);
			dos.writeByte(REMINDER.ordinal());
			dos.writeLong(REMOTE_ID.getMostSignificantBits());
			dos.writeLong(REMOTE_ID.getLeastSignificantBits());
			dos.writeInt(17);
			dos.writeShort(1);
			dos.writeUTF(ed.getId());
			dos.close();
			
			ds.setSoTimeout(1000);
			DatagramPacket dp = new DatagramPacket(baos.toByteArray(), 0, baos.size(), 
					InetAddress.getLoopbackAddress(), comms.getUdpPort());
			ds.send(dp);
			
			
			dp = new DatagramPacket(new byte[65535], 65535);
			ds.receive(dp);
			
			try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(dp.getData(), dp.getOffset(), dp.getLength()))) {
				assertEquals(2, dis.readByte());
				assertEquals(ACKNOWLEDGMENT.ordinal(), dis.readByte());
				assertEquals(COMMS_ID, new UUID(dis.readLong(), dis.readLong()));
				assertEquals(COMMS_ID, UUID.fromString(dis.readUTF()));
				assertEquals(17, dis.readInt());
			}
			
			ds.receive(dp);
			try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(dp.getData(), dp.getOffset(), dp.getLength()))) {
				assertEquals(2, dis.readByte());
				assertEquals(REQUEST_REANNOUNCEMENT.ordinal(), dis.readByte());
				dis.readLong(); dis.readLong();
				assertEquals(COMMS_ID, new UUID(dis.readLong(), dis.readLong()));
				assertEquals(17, dis.readInt());
				assertEquals(1, dis.readUnsignedShort());
				assertEquals(ed.getId(), dis.readUTF());
			}
		}
	}

	@Test
	public void testRespondToRequest() throws Exception {
		comms.bind(fni, config);
		
		UUID reannouncementId = UUID.randomUUID();
		try (DatagramSocket ds = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
			
			EndpointDescription ed = getTestEndpointDescription(true, false);
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(baos);
			
			dos.writeByte(2);
			dos.writeByte(REQUEST_REANNOUNCEMENT.ordinal());
			dos.writeLong(reannouncementId.getMostSignificantBits());
			dos.writeLong(reannouncementId.getLeastSignificantBits());
			dos.writeLong(REMOTE_ID.getMostSignificantBits());
			dos.writeLong(REMOTE_ID.getLeastSignificantBits());
			dos.writeInt(27);
			dos.writeShort(1);
			dos.writeUTF(ed.getId());
			dos.close();
			
			ds.setSoTimeout(1000);
			DatagramPacket dp = new DatagramPacket(baos.toByteArray(), 0, baos.size(), 
					InetAddress.getLoopbackAddress(), comms.getUdpPort());
			ds.send(dp);
			
			
			dp = new DatagramPacket(new byte[65535], 65535);
			ds.receive(dp);
			
			try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(dp.getData(), dp.getOffset(), dp.getLength()))) {
				assertEquals(2, dis.readByte());
				assertEquals(ACKNOWLEDGMENT.ordinal(), dis.readByte());
				assertEquals(COMMS_ID, new UUID(dis.readLong(), dis.readLong()));
				assertEquals(reannouncementId, UUID.fromString(dis.readUTF()));
				assertEquals(27, dis.readInt());
			}
			Mockito.verify(localListener, timeout(1000)).republish(ed.getId(), REMOTE_ID);
		}
	}

}
