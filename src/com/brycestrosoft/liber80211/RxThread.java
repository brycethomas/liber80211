/**
 * Copyright 2012 Bryce Thomas <bryce.m.thomas@gmail.com>
 * 
 * Based on the Linux RTL8187 driver, which is:
 * Copyright 2007 Michael Wu <flamingice@sourmilk.net>
 * Copyright 2007 Andrea Merello <andreamrl@tiscali.it>
 * 
 * liber80211 is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 */

package com.brycestrosoft.liber80211;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.app.Activity;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbRequest;
import android.widget.TextView;
import android.widget.Toast;


public class RxThread implements Runnable {
	private UsbDeviceConnection _conn;
	private Activity _parent_activity;
	private UsbEndpoint _ep;
	public boolean stop_thread = false;
	public TextView messages;
	public OutputStreamWriter fileMessages;
	private Set<String> _seen_macs;
	private Set<String> _seen_essids;
	public RxThread(UsbDeviceConnection conn, Activity parentActivity, UsbEndpoint ep) {
		_conn = conn;
		_parent_activity = parentActivity;
		_ep = ep;
		_seen_macs = new HashSet<String>();
		_seen_essids = new HashSet<String>(); 
	}
	public void run() {
		try {
			while (stop_thread == false) {
				UsbRequest req = _conn.requestWait();
				if (req == null) {
					break;
				}

				ByteBuffer data = ((BulkMessage)req.getClientData()).getByteBuffer();

				StringBuilder data_as_hex = new StringBuilder();
				for (byte b : data.array()) {
					data_as_hex.append(String.format("%02x", b));
				}
				
				// uncomment to write out the full hex string received from the USB peripheral.
				//writeMessage("***\n" + data_as_hex.toString() + "\n***\n", _parent_activity, true);
				
				if (extract_frame_control(data.array()).equals("8000")) {
					boolean show_log_on_screen = true;
					String beacon_bssid = extractBssid(data.array());
					String beacon_essid = extractEssid(data.array());
					write_message(String.format("BEACON\t%s\t%s.\n", beacon_bssid, beacon_essid), _parent_activity, show_log_on_screen);
				}
				else if (extract_frame_control(data.array()).equals("4000")) {
					String probe_request_sender_MAC = extract_probe_request_sender_MAC(data.array());
					String probe_request_essid = extract_probe_request_essid(data.array());
					boolean show_log_on_screen = true;
					// uncomment if you want to restrict printing on screen to only new MACs/ESSIDs
					/*boolean showLogOnScreen = false;
					if (!_seen_macs.contains(probeSenderMac)) {
						showLogOnScreen = true;
						_seen_macs.add(probeSenderMac);
					}
					else if (!_seen_essids.contains(probeEssid)) {
						showLogOnScreen = true;
						_seen_essids.add(probeEssid);
					}*/
					write_message(String.format("PROBE REQUEST\t%s\t%s.\n", probe_request_sender_MAC, probe_request_essid),
							_parent_activity, show_log_on_screen);
				}
				
				// enqueue another bulk read to replace the one we just received
				int byte_buff_len = 2500; // TODO: factor this out as we use a duplicate variable name/value in USBIO.java.
				UsbRequest request = new UsbRequest();
				request.initialize(_conn, _ep);
				BulkMessage msg = new BulkMessage(byte_buff_len);
				if (msg.queueRead(request)) {
					//writeMessage("Replacement bulk queued successfully.\n", _parent_activity, true);
				}
				else {
					//writeMessage("Replacement bulk queue failed.\n", _parent_activity, true);
				}
				
				//writeMessage("Destination address is: " + extractDestinationAddress(data.array()) + "\n", _parent_activity, true);
			}
			write_message("About to exit run()\n", _parent_activity);
		}
		catch (Exception e) {
			write_message(e.getClass().toString() + "\n" + e.getMessage() + "\n" + e.getLocalizedMessage() + 
     			   "\n" + Arrays.toString(e.getStackTrace()) + "\n", _parent_activity);
		}
	}
	
	private static String extract_frame_control(byte[] frame) {
		byte[] frame_control_bytes = Arrays.copyOfRange(frame, 0, 2);
		// TODO: we should probably reverse the endianness here and then also fix up the clients
		// of this method not to rely on wonky endianness.
		return byte_array_to_hex_string(frame_control_bytes);
	}
	
	private static String extract_destination_address(byte[] frame) {
		// Pull the destination MAC address out of a 802.11 frame
		// [x,y)
		byte[] dest_bytes = Arrays.copyOfRange(frame, 4, 10);
		return byte_array_to_hex_string(dest_bytes);
	}
	
	private static String extractBssid(byte[] frame) {
		byte[] bssid_bytes = Arrays.copyOfRange(frame, 16,22);
		return byte_array_to_hex_string(bssid_bytes);
	}
	
	private static String extractEssid(byte[] frame) throws UnsupportedEncodingException {
		int essid_length = (int)frame[37];
		byte[] essid_bytes = Arrays.copyOfRange(frame, 38, 38 + essid_length);
		return new String(essid_bytes, "US-ASCII");
		//return byteArrayToHexString(essidBytes);
	}
	
	private static String extract_probe_request_sender_MAC(byte[] frame) {
		byte[] mac_bytes = Arrays.copyOfRange(frame, 10, 16);
		return byte_array_to_hex_string(mac_bytes);
	}
	
	private static String extract_probe_request_essid(byte[] frame) throws UnsupportedEncodingException {
		int essid_length = (int)frame[25];
		if (essid_length != 0) {
			byte[] essid_bytes = Arrays.copyOfRange(frame, 26, 26 + essid_length);
			return new String(essid_bytes, "US-ASCII");
		}
		return "broadcast essid";
	}
	
	private static String byte_array_to_hex_string(byte[] arr) {
		StringBuilder sb = new StringBuilder();
		for (byte b: arr) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}
	
	private void write_message(final String message, Activity parent_activity) {
		// if caller doesn't specify whether to log to screen, do it by default.
		write_message(message, parent_activity, true);
	}
	
	// Write to the messages textview
	private void write_message(final String message, Activity parent_activity, boolean show_log_on_screen) {
		if (show_log_on_screen) {
			parent_activity.runOnUiThread(new Runnable() {
				public void run() {
					messages.append(System.currentTimeMillis() + "\t" + message);
				}
			});
		}
		//if (doLog == true) {
			try {
				fileMessages.write(System.currentTimeMillis() + "\t" + message);
			} catch (IOException e) {
				//TODO: add some toast here to display a message that the I/O buggered up.
				//fileMessages.write("failed to write to file.\n");
				//messages.append("failed to write to file.\n");
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		//}
	}
	
	public void stopThread() {
		stop_thread = true;
	}
}