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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.app.Activity;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.widget.TextView;

/**
 * Does all the heavy lifting of communicating over the wire with the USB WiFi card.
 * */
public class USBIO {
	// for debugging and writing info to screen
	private static TextView messages;
	private static OutputStreamWriter file_messages;
	public static boolean do_log = true;


	// careful with this one - we use it as a shared variable
	// that is potentially modified based on both vendor and product id's as well as hardware revision,
	// so it's more than a convenience cache variable.
	private static DeviceType devType = null; 

	// PUBLIC USB IO METHODS //
	public static void set_text_view(TextView tv) {
		messages = tv;
	}
	
	public static void set_log_file(OutputStreamWriter output) {
		file_messages = output;
	}
	
	public static int get_eeprom_width(UsbDeviceConnection conn) {
		// if we've read it before it'll be cached and no need for any USB I/O.
		if (DeviceProperties.eeprom_width < 0) {
			int PCI_EEPROM_WIDTH_93C66 = 8;
			int PCI_EEPROM_WIDTH_93C46 = 6;

			byte[] buff = new byte[4];

			int wValue = 0xff44;
			buff = rtl818xRead32(conn, wValue);
			ByteBuffer bb = ByteBuffer.wrap(buff);
			if ((bb.getInt() & (1 << 6)) != 0) {
				DeviceProperties.eeprom_width = PCI_EEPROM_WIDTH_93C66;
			} else {
				DeviceProperties.eeprom_width = PCI_EEPROM_WIDTH_93C46;
			}
			//writeMessage(String.format("EEPROM width is %d", eepromWidth));
		}
		return DeviceProperties.eeprom_width;
	}

	
	 public static byte[] get_mac_address(UsbDeviceConnection conn){
		 if(DeviceProperties.mac[0] == 0 && DeviceProperties.mac[1] == 0 && 
				 DeviceProperties.mac[2] == 0 && DeviceProperties.mac[3] == 0 && 
				 DeviceProperties.mac[4] == 0 && DeviceProperties.mac[5] == 0) { 
			 byte[] highOrder16Bits = eeprom93cx6Read(conn,
						ScalarConstants.EEPROM_CMD, ScalarConstants.RTL8187_EEPROM_MAC_ADDR);
			 byte[] midOrder16Bits = eeprom93cx6Read(conn,
					 ScalarConstants.EEPROM_CMD, ScalarConstants.RTL8187_EEPROM_MAC_ADDR + 1);
			 byte[] lowOrder16Bits = eeprom93cx6Read(conn,
					 ScalarConstants.EEPROM_CMD, ScalarConstants.RTL8187_EEPROM_MAC_ADDR + 2);
			 DeviceProperties.mac[0] = highOrder16Bits[0];
			 DeviceProperties.mac[1] = highOrder16Bits[1];
			 DeviceProperties.mac[2] = midOrder16Bits[0];
			 DeviceProperties.mac[3] = midOrder16Bits[1];
			 DeviceProperties.mac[4] = lowOrder16Bits[0];
			 DeviceProperties.mac[5] = lowOrder16Bits[1];
		 }
		 return DeviceProperties.mac;
	 }

	// corresponds to the first black magic write inside the linux driver probe
	public static void perform_probe_black_magic_write_one(UsbDeviceConnection conn) {
		rtl818xWrite8(conn, ScalarConstants.EEPROM_CMD, ScalarConstants.RTL818X_EEPROM_CMD_CONFIG);
	}
	
	// corresponds to the first black magic read/write inside the linux driver probe
	public static byte perform_probe_black_magic_read_write_one(UsbDeviceConnection conn) {
		int wValue = 0xff5e;
		byte reg = (byte) (rtl818xRead8(conn, wValue) & ~1);
		rtl818xWrite8(conn, wValue, (byte)(reg | 1));
		return reg;
	}
	
	// corresponds to the first double write black magic inside the linux driver probe
	public static void perform_probe_black_magic_write_write_one(UsbDeviceConnection conn, byte reg) {
		// note we don't read in the asic rev, as i'm *assuming* (ass out of you and me) that we don't need
		// it and that the read doesn't have side effects.
		int wValue = 0xff5e;
		rtl818xWrite8(conn, wValue, reg);
		wValue = 0xff50;
		byte RTL818X_EEPROM_CMD_NORMAL = (0 << 6);
		rtl818xWrite8(conn, wValue, RTL818X_EEPROM_CMD_NORMAL);
	}
	
	// the Ubuntu 12.04 C implementation stores priv->is_rtl8187b to represent
	// device type.  We can just call this method whenever we need to know.
	public static DeviceType getDeviceType(UsbDevice device) throws Exception {
		if (devType == null) {
			int vendorId = device.getVendorId();
			int productId = device.getProductId();
			if (vendorId == 3034 && productId == 33159) { // vendorId = 0x0bda,
															// productId =
															// 0x8187
				devType = DeviceType.RTL8187;
			} else if (vendorId == 0x050d && productId == 0x705e) { // pretty
																	// sure we
																	// can use
																	// hex here
				devType = DeviceType.RTL8187B;
			}
			// TODO: add support for other devices that use the RTL8187 or
			// some variant later.
			// the rules can be found in the rtl8187_table[] in the linux driver
			else {
				throw new Exception("Unsupported Device plugged in");
				// TODO: throw an exception
			}
		}
		return devType;
	}
	
	public static void setup_channels_part_one(UsbDeviceConnection conn) {
		writeMessage("Entered setupChannelsPartOne.\n");
		int count = 0;
		for (int i = 0; i < 3; i++) {
			byte[] txPwr = eeprom93cx6Read(conn, ScalarConstants.EEPROM_CMD, ScalarConstants.RTL8187_EEPROM_TXPWR_CHAN_1 + i);
			//milliSleep(5000); // Superfluous 
			DeviceProperties.channels[count].hw_value = (short)(txPwr[1]); // equivalent to (txpwr & 0xff) on a short.
			count++;
			DeviceProperties.channels[count].hw_value = (short)(txPwr[0]); // equivalent to (txpwr >> 8) on a short.
			count++;
		}
		for (int i = 0; i < 2; i++) {
			byte[] txPwr = eeprom93cx6Read(conn, ScalarConstants.EEPROM_CMD, ScalarConstants.RTL8187_EEPROM_TXPWR_CHAN_4 + i);
			DeviceProperties.channels[count].hw_value = (short)(txPwr[1]); count++;
			DeviceProperties.channels[count].hw_value = (short)(txPwr[0]); count++;
		}
		
		//TODO: at the moment we don't use the result of this eeprom read.  we probably should.
		eeprom93cx6Read(conn, ScalarConstants.EEPROM_CMD, ScalarConstants.RTL8187_EEPROM_TXPWR_BASE);
		writeMessage("Exiting setupChannelsPartOne.\n");
	}
	
	public static void setup_channels_part_two(UsbDeviceConnection conn) {
		// TODO: we only implement the branch applicable to (!priv->is_rtl8187b) here, so we don't support other cards.
		int count = 10; // TODO: this is fragile because we're basing this starting number on setupChannelPartOne having been called prior.
		for (int i = 0; i < 2; i++) {
			byte[] txPwr = eeprom93cx6Read(conn, ScalarConstants.EEPROM_CMD, ScalarConstants.RTL8187_EEPROM_TXPWR_CHAN_6 + i);
			DeviceProperties.channels[count++].hw_value = txPwr[1];
			DeviceProperties.channels[count++].hw_value = txPwr[0];
		}
	}
	
	public static boolean get_asic_revision(UsbDeviceConnection conn) {
		if (DeviceProperties.asic_revision == null) {
			writeMessage("Reading ASIC revision...\n");
			int wValue = 0xfffe;
			int revision = rtl818xRead8(conn, wValue) & 0x3;
			if (revision == 0) {
				DeviceProperties.asic_revision = false;
			}
			else {
				DeviceProperties.asic_revision = true;
			}
			writeMessage("ASIC revision read.\n");
		}
		return DeviceProperties.asic_revision;
	}
	
	public static HardwareRevision get_hardware_revision(UsbDeviceConnection conn, UsbDevice device) {
		if (DeviceProperties.hardware_revision == null) {
			writeMessage("Reading hardware revision...\n");
			DeviceType devType = null;
			try {
				devType = getDeviceType(device);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (devType != DeviceType.RTL8187B) {
				// this seems to be the branch that's executed for my first ALFA
				// AWUS036H card.
				byte[] buff = new byte[4];
				buff = rtl818xRead32(conn, ScalarConstants.TX_CONF);
				ByteBuffer bb = ByteBuffer.wrap(buff);
				int bbInt = bb.getInt();
				bbInt &= ScalarConstants.RTL818X_TX_CONF_HWVER_MASK;
				if (bbInt == ScalarConstants.RTL818X_TX_CONF_R8187VD_B) {
					DeviceProperties.hardware_revision = HardwareRevision.RTL8187BvB;
					devType = DeviceType.RTL8187B;
				} else if (bbInt == ScalarConstants.RTL818X_TX_CONF_R8187VD) {
					// the linux driver checks for this but doesn't set the
					// hardware revision for
					// whatever reason.
					// i.e. do nothing.
				} else {
					// do nothing again.
				}
			} else {
				writeMessage("Uh-oh... executing funky hardware revision branch.\n");
				// this branch is untested and may cause funky broken support
				// with other wifi cards
				// TODO: implement this branch.  other wifi cards will almost certainly break without it.
			}
			writeMessage("Hardware revision read.\n");
		}
		return DeviceProperties.hardware_revision;
	}
	
    
	public static rtl818xRfOps rtl8187_detect_rf(UsbDeviceConnection conn, UsbDevice device) {
		try {
			if (getDeviceType(device) != DeviceType.RTL8187B) {
				byte[] dataOne = ByteBuffer.allocate(2).putShort((short) 0x01B7).array();
				rtl8225Write(conn, 0, dataOne);
				
				byte[] reg8 = rtl8225Read(conn, 8);
				byte[] reg9 = rtl8225Read(conn, 9);
				
				byte[] dataTwo = ByteBuffer.allocate(2).putShort((short) 0x00B7).array();
				rtl8225Write(conn, 0, dataTwo);
				
				if (ByteBuffer.wrap(reg8).getShort() != (short) 0x588 || ByteBuffer.wrap(reg9).getShort() != (short) 0x700) {
					writeMessage("This card will use rtl8225RfOps radio ops.\n");
					return new rtl8225RfOps();
				}
				else {
					writeMessage("This card will use rtl8225z2RfOps radio ops.\n");
					return new rtl8225z2RfOps();
				}
				
				
			}
			else {
				writeMessage("This card will use rtl8225z2bRfOps radio ops.\n");
				return new rtl8225z2bRfOps();
			}
		} 
		catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}
	
	public static void config_rtl_8187_leds(UsbDeviceConnection conn) {
		int wValue = 0xff50;
		byte[] reg = eeprom93cx6Read(conn, wValue, 0x3f); // i think 0x3f is right here...
		//reg &= 0xff; // TODO: there's more to be done here with reg.  at the moment
		// just wanted the read to be done in case of side effects.
		// TODO: finish the delayed work part of this method.
	}
	
	public static void rtl8187_rfkill_init(UsbDeviceConnection conn) {
		//TODO: we're meant to be able to call rtl8187IsRadioEnabled() to determine
		// whether something or other is on or off, but because I did a lame implementation of
		// that method, it always returns true.
		rtl8187IsRadioEnabled(conn);
	}
	
	public static void rtl8187_rfkill_poll(UsbDeviceConnection conn) {
		//TODO: again this is a slackers implementation that I'm just using at the moment in case of
		// important side effects.
		rtl8187IsRadioEnabled(conn);
	}
	
	
	public static RxThread rtl8187_start(UsbDeviceConnection conn, UsbEndpoint ep, Activity parentActivity) {
		// TODO: I've cut out a whole load of logic for dealing with rtl8187b, so
		// this would need to be added before we could support a non-rtl8187 card.
		int ret = 0;
		ret = rtl8187InitHardware(conn);
		if (ret != 0) {
			writeMessage("rtl8187InitHardware() returned non-zero for some reason.\n");
		}
		
		byte[] data = { (byte) 0xFF, (byte) 0xFF };
		rtl818xWrite16(conn, 0xff3c, data, 0x0000);
		byte[] data2 = ByteBuffer.allocate(4).putInt(0xffffffff).array();
		rtl818xWrite32(conn, 0xff08, data2);
		rtl818xWrite32(conn, 0xff0c, data2);
		
		// inlined equivalent of rtl8187_init_urbs(dev);
		// doesn't exactly mirror linux driver behaviour as we do precisely 16 iterations
		// regardless of whether async interruptions fulfilled during loop where linux driver
		// would enqueue another to replace it.  I think...
	    int byteBuffLen = 2500;
	    for (int i = 0; i < 16; i++) {
	    	UsbRequest request = new UsbRequest();
	    	request.initialize(conn, ep);
	    	BulkMessage msg = new BulkMessage(byteBuffLen);
	    	if (msg.queueRead(request)) {
	    		writeMessage("Bulk queued successfully.\n");
	    	}
	    	else {
	    		writeMessage("Bulk queue failed.\n");
	    	}
	    }
	    
		RxThread wt = new RxThread(conn/*, buff*/, parentActivity, ep);
		wt.messages = messages;
		wt.fileMessages = file_messages;
		Thread t = new Thread(wt);
		t.start();
		
		int reg = (1 << 31) | (1 << 28) | (1 << 23) | (1 << 20) | (1 << 18) | ( 7 << 13 ) | (7 << 10 ) | (1 << 3) | (1 << 1) | ScalarConstants.RTL818X_RX_CONF_MONITOR;
		byte[] regArr = ByteBuffer.allocate(4).putInt(reg).array();
		rtl818xWrite32(conn, 0xff44, regArr);
		int newReg = (int)rtl818xRead8(conn, 0xffbc);
		newReg &= ~(1 << 0);
		newReg |= (1 << 1);
		rtl818xWrite8(conn, 0xffbc, (byte)newReg);
		newReg = (int)rtl818xRead8(conn, 0xff9c);
		newReg &= ~(1 << 0);
		newReg &= ~(1 << 1);
		newReg &= ~(1 << 2);
		rtl818xWrite8(conn, 0xff9c, (byte)newReg);
		
		newReg = (1 << 31) | (7 << 21) | (1 << 19);
		byte[] newRegArr = ByteBuffer.allocate(4).putInt(newReg).array();
		rtl818xWrite32(conn, 0xff40, newRegArr);
		
		newReg = rtl818xRead8(conn, 0xff37);
		newReg |= (1 << 2);
		newReg |= (1 << 3);
		rtl818xWrite8(conn, 0xff37, (byte)newReg);
		
		// TODO: write the remainder of the rtl8187Start code (INIT_DELAYED_WORK?).
		return wt;
	}
	
	public static void rtl8187_add_interface(UsbDeviceConnection conn, byte[] macAddress) {
		rtl818xWrite8(conn, 0xff50, (byte)0xc0);
		rtl818xWrite8(conn, 0xff00, macAddress[0]);
		rtl818xWrite8(conn, 0xff01, macAddress[1]);
		rtl818xWrite8(conn, 0xff02, macAddress[2]);
		rtl818xWrite8(conn, 0xff03, macAddress[3]);
		rtl818xWrite8(conn, 0xff04, macAddress[4]);
		rtl818xWrite8(conn, 0xff05, macAddress[5]);
		rtl818xWrite8(conn, 0xff50, (byte)0x00);
	}
	


	// PRIVATE USB I/O METHODS //

	// Write to the messages textview
	private static void writeMessage(String message) {
		if (do_log == true) {
			//messages.append(/*System.nanoTime() + ":" + */message); // comment this line out to stop writing messages to textview
			try {
				file_messages.write(System.currentTimeMillis() + "\t" + message);
			} catch (IOException e) {
				messages.append("failed to write to file.\n");
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	private static boolean rtl8187IsRadioEnabled(UsbDeviceConnection conn) {
		byte gpio = rtl818xRead8(conn, 0xff91);
		//TODO: what we write here should actually be gpio & ~priv->rfkill_mask,
		// so this method's external validity is probably messed up.
		rtl818xWrite8(conn, 0xff91, (byte)0x00);
		gpio = rtl818xRead8(conn, 0xff92);
		//TODO: this method may actually be fairly useless in its current form as I don't
		// properly determine a return value.  Only reason I've implemented it at all so far
		// is in case the read/writes have important side effects.
		return true;
	}
	
	// Read 8 bits.
	private static byte rtl818xRead8(UsbDeviceConnection conn, int address) {
		byte[] readBuff = {0x00};
		conn.controlTransfer(0xc0, 0x05, address, 0x0000, readBuff,
				0x0001, 2000);
		//R = Read, address, read byte
		writeMessage(String.format("R %02x %02x\n", address, readBuff[0]));
		//writeMessage(String.format("read %02x from address %02x.\n", readBuff[0], address));
		return readBuff[0];
	}
	
	// Read 16 bits.
	private static byte[] rtl818xRead16(UsbDeviceConnection conn, int address) {
		byte[] readBuff = new byte[2];
		conn.controlTransfer(0xc0, 0x05, address, 0x0000, readBuff,
				0x0002, 2000);
		ByteBuffer bb = ByteBuffer.wrap(readBuff);
		short bbShort = bb.getShort();
		//R = Read, address, read bytes
		writeMessage(String.format("R %02x %02x%02x\n", address, readBuff[0], readBuff[1]));
		byte[] beResult = leShort2BeByteArray(bbShort);
		//writeMessage(String.format("read %02x%02x from address %02x.\n", beResult[0], beResult[1], address));
		return beResult;
	}

	// Read 32 bits.
	private static byte[] rtl818xRead32(UsbDeviceConnection conn,
			int address) {
		byte[] readBuff = new byte[4];
		conn.controlTransfer(0xc0, 0x05, address, 0x0000, readBuff,
				0x0004, 2000);
		//R = Read, address, read bytes
		// looks good.  EDIT: Nope!  Prints different things ond different runs! (non-deterministic).
		writeMessage(String.format("R %02x %02x%02x%02x%02x\n", address, readBuff[0], readBuff[1], readBuff[2], readBuff[3])); 
		ByteBuffer bb = ByteBuffer.wrap(readBuff);
		int bbInt = bb.getInt();
		byte[] beResult = leInt2BeByteArray(bbInt);
		// for some reason if I do logging of readBuff[0], [1], [2], [3] here, i get different values than what I get logging it
		// immediately after the control transfer.  I think ByteBuffer.wrap(thingo) might mutate thingo in some way.
		/*writeMessage(String.format("read %02x%02x%02x%02x from address %02x.\n", 
				beResult[0], beResult[1], beResult[2], beResult[3], address));*/
		return beResult;
	}

	// Write 8 bits.
	private static void rtl818xWrite8(UsbDeviceConnection conn,
			int address, byte data) {
		// in USB spec speak, address here is "wValue".
		byte[] writeBuff = { data };
		conn.controlTransfer(0x40, 0x05, address, 0x0000, writeBuff,
				0x0001, 2000);
		//W = Write, address, wIndex, data
		writeMessage(String.format("W %02x %04x %02x\n", address, 0x0000, data));
		//writeMessage(String.format("wrote %02x to address %02x.\n", data, address));
	}
	
	// Write 16 bits.
	private static void rtl818xWrite16(UsbDeviceConnection conn, int address, byte[] data, int wIndex) {
		// this may be the only read or write method that ever uses wIndex, hence why it has an extra param
		byte[] writeBuff = reverseByteOrder(data, 2);
		conn.controlTransfer(0x40, 0x05, address, wIndex, writeBuff,
				0x0002, 2000);
		//W = Write, address, wIndex, data.  Seems that usbmon displays data in its little endian wire form, so we do that too.
		writeMessage(String.format("W %04x %04x %02x%02x\n", address, wIndex, writeBuff[0], writeBuff[1]));
		//writeMessage(String.format("wrote %02x%02x to address %02x.\n", data[0], data[1], address));		
	}
	
	// Write 32 bits.
	private static void rtl818xWrite32(UsbDeviceConnection conn, int address, byte[] data) {
		byte[] writeBuff = reverseByteOrder(data, 4);
		conn.controlTransfer(0x40, 0x05, address, 0x0000, writeBuff,
				0x0004, 2000);
		//W = Write, address, wIndex, data.  Seems that usbmon displays data in its little endian wire form, so we do that too.
		writeMessage(String.format("W %02x %04x %02x%02x%02x%02x\n", address, 0x0000, writeBuff[0], writeBuff[1], writeBuff[2], writeBuff[3]));
		//writeMessage(String.format("wrote %02x%02x%02x%02x to address %02x.\n", data[0], data[1], data[2], data[3], address));
	}
	
	// rtl8225 Write method
	private static void rtl8225Write(UsbDeviceConnection conn, int address, byte[] data) {
		if (get_asic_revision(conn)) {
			rtl8225Write8051(conn, address, data);
		}
		else {
			// TODO: rtl8225 bitbangin' time.  needs to be implemented if we want to support other cards.
		}
	}
	
	private static byte[] rtl8225Read(UsbDeviceConnection conn, int address) {
		byte[] reg80 = rtl818xRead16(conn, 0xff80);
		byte[] reg82 = rtl818xRead16(conn, 0xff82);
		byte[] reg84 = rtl818xRead16(conn, 0xff84);
		
		reg80 = ByteBuffer.allocate(2).putShort((short) (ByteBuffer.wrap(reg80).getShort() & ~0xF)).array();
		
		short reg82OR0x000F = (short) (ByteBuffer.wrap(reg82).getShort() | 0x000F);
		rtl818xWrite16(conn, 0xff82, ByteBuffer.allocate(2).putShort(reg82OR0x000F).array(), 0x0000);
		short reg84OR0x000F = (short) (ByteBuffer.wrap(reg84).getShort() | 0x000F);
		rtl818xWrite16(conn, 0xff84, ByteBuffer.allocate(2).putShort(reg84OR0x000F).array(), 0x0000);
		
		short reg80ORFour = (short) (ByteBuffer.wrap(reg80).getShort() | (1 << 2));
		rtl818xWrite16(conn, 0xff80, ByteBuffer.allocate(2).putShort(reg80ORFour).array(), 0x0000);
		haveASleep(4000);
		rtl818xWrite16(conn, 0xff80, reg80, 0x0000);
		haveASleep(5000);
		
		for (int i = 4; i >= 0; i--) {
			short reg = (short) (ByteBuffer.wrap(reg80).getShort() | ((address >> i) & 1));
			//writeMessage(String.format("shortReg equals %02x",reg));
			
			if ((i & 1) == 0) {
				rtl818xWrite16(conn, 0xff80, ByteBuffer.allocate(2).putShort(reg).array(), 0x0000);
				haveASleep(1000);
			}
			
			short regORTwo = (short) (reg | (1 << 1));
			rtl818xWrite16(conn, 0xff80, ByteBuffer.allocate(2).putShort(regORTwo).array(), 0x0000);
			haveASleep(2000);
			rtl818xWrite16(conn, 0xff80, ByteBuffer.allocate(2).putShort(regORTwo).array(), 0x0000);
			haveASleep(2000);
			
			if ((i & 1) != 0) {
				rtl818xWrite16(conn, 0xff80, ByteBuffer.allocate(2).putShort(reg).array(), 0x0000);
				haveASleep(1000);
			}
		}
		
		short reg80ORTen = (short) (ByteBuffer.wrap(reg80).getShort() | (1 << 3) | (1 << 1));
		rtl818xWrite16(conn, 0xff80, ByteBuffer.allocate(2).putShort(reg80ORTen).array(), 0x0000);
		haveASleep(2000);
		short reg80OREight = (short) (ByteBuffer.wrap(reg80).getShort() | (1 << 3));
		rtl818xWrite16(conn, 0xff80, ByteBuffer.allocate(2).putShort(reg80OREight).array(), 0x0000);
		haveASleep(2000);
		rtl818xWrite16(conn, 0xff80, ByteBuffer.allocate(2).putShort(reg80OREight).array(), 0x0000);
		haveASleep(2000);
		
		short out = 0;
		for (int i = 11; i >= 0; i--) {
			rtl818xWrite16(conn, 0xff80, ByteBuffer.allocate(2).putShort(reg80OREight).array(), 0x0000);
			haveASleep(1000);
			rtl818xWrite16(conn, 0xff80, ByteBuffer.allocate(2).putShort(reg80ORTen).array(), 0x0000);
			haveASleep(2000);
			rtl818xWrite16(conn, 0xff80, ByteBuffer.allocate(2).putShort(reg80ORTen).array(), 0x0000);
			haveASleep(2000);
			rtl818xWrite16(conn, 0xff80, ByteBuffer.allocate(2).putShort(reg80ORTen).array(), 0x0000);
			haveASleep(2000);
			
			byte[] thingo = rtl818xRead16(conn, 0xff86);
			if ((short) (ByteBuffer.wrap(thingo).getShort() & (1 << 1)) != 0) {
				out |= 1 << i;
			}
			
			rtl818xWrite16(conn, 0xff80, ByteBuffer.allocate(2).putShort(reg80OREight).array(), 0x0000);
			haveASleep(2000);
		}
		
		short reg80ORTwelve = (short) (ByteBuffer.wrap(reg80).getShort() | (1 << 3) | (1 << 2));
		rtl818xWrite16(conn, 0xff80, ByteBuffer.allocate(2).putShort(reg80ORTwelve).array(), 0x0000);
		haveASleep(2000);
		
		rtl818xWrite16(conn, 0xff82, reg82, 0x0000);
		rtl818xWrite16(conn, 0xff84, reg84, 0x0000);
		rtl818xWrite16(conn, 0xff80, ByteBuffer.allocate(2).putShort((short) 0x03A0).array(), 0x0000);
		
		byte[] outArr = ByteBuffer.allocate(2).putShort(out).array();
		// TODO: not sure if we need to switch byte-order here.  doesn't look like it.
		return outArr;
	}
	
	private static void rtl8225Write8051(UsbDeviceConnection conn, int address, byte[] data) {
		int RF_PINS_OUTPUT = 0x80;
		int RF_PINS_ENABLE = 0x82;
		int RF_PINS_SELECT = 0x84;
		int wValue = 0xff80;
		byte[] reg80 = rtl818xRead16(conn, wValue);
		wValue = 0xff82;
		byte[] reg82 = rtl818xRead16(conn, wValue);
		wValue = 0xff84;
		byte[] reg84 = rtl818xRead16(conn, wValue);
		
		reg80 = ByteBuffer.allocate(2).putShort((short) (ByteBuffer.wrap(reg80).getShort() & ~(0x3 << 2))).array();
		reg84 = ByteBuffer.allocate(2).putShort((short) (ByteBuffer.wrap(reg84).getShort() & ~0xF)).array();
		
		wValue = 0xff82;
		short reg82ORDWith0x0007 = (short) (ByteBuffer.wrap(reg82).getShort() | 0x0007);
		rtl818xWrite16(conn, wValue, ByteBuffer.allocate(2).putShort(reg82ORDWith0x0007).array(), 0x0000);
		wValue = 0xff84;
		short reg84ORDWith0x0007 = (short) (ByteBuffer.wrap(reg84).getShort() | 0x0007);
		rtl818xWrite16(conn, wValue, ByteBuffer.allocate(2).putShort(reg84ORDWith0x0007).array(), 0x0000);
		haveASleep(10000);
		wValue = 0xff80;
		short reg80ORDWithFour = (short) (ByteBuffer.wrap(reg80).getShort() | (1 << 2));
		rtl818xWrite16(conn, wValue, ByteBuffer.allocate(2).putShort(reg80ORDWithFour).array(), 0x0000);
		haveASleep(2000);
		rtl818xWrite16(conn, wValue, reg80, 0x0000);
		haveASleep(10000);
		rtl818xWrite16(conn, address, data, 0x8225); // weird write that actually specifies wIndex
		rtl818xWrite16(conn, wValue, ByteBuffer.allocate(2).putShort(reg80ORDWithFour).array(), 0x0000);
		haveASleep(10000);
		rtl818xWrite16(conn, wValue, ByteBuffer.allocate(2).putShort(reg80ORDWithFour).array(), 0x0000);
		wValue = 0xff84;
		rtl818xWrite16(conn, wValue, reg84, 0x0000);
	}

	private static byte[] eeprom93cx6Read(UsbDeviceConnection conn,
			int address, int word) {
		// TODO: pretty sure the "address" parameter on this method always receives the same
		// argument of 0xff50.  Might want to refactor it out after double checking this is the case.
		// ** STARTUP ** //
		boolean[] relevantFlags = rtl8187EepromRegisterRead(conn,
				address);
		boolean dataIn = relevantFlags[0];
		boolean dataOut = relevantFlags[1];
		boolean dataClock = relevantFlags[2];
		boolean chipSelect = relevantFlags[3];
		// regardless of what relevant flags returns, part of startup seems to
		// be to always set these 5 bools this way
		dataIn = false;
		dataOut = false;
		dataClock = false;
		chipSelect = true;
		boolean driveData = true;
		rtl8187EepromRegisterWrite(conn, address, dataIn, dataOut,
				dataClock, chipSelect);
		eeprom93cx6PulseHigh(conn, address, dataIn, dataOut,
				chipSelect);
		eeprom93cx6PulseLow(conn, address, dataIn, dataOut,
				chipSelect);

		// ** CHOOSE READ OPCODE/WORD TO READ ** //
		int command = (ScalarConstants.PCI_EEPROM_READ_OPCODE << get_eeprom_width(conn)) | word;
		int PCI_EEPROM_WIDTH_OPCODE = 3;
		eeprom93cx6WriteBits(conn, address, command,
				PCI_EEPROM_WIDTH_OPCODE + get_eeprom_width(conn));
		
		// ** READ 16 BITS ** //
		short buff = eeprom93cx6ReadBits(conn, address);
		writeMessage("eeprom93cx6ReadBits returned " + buff + "\n");

		// ** CLEANUP ** //
		eeprom93cx6Cleanup(conn, address);

		return leShort2BeByteArray(buff);
	}

	private static boolean[] rtl8187EepromRegisterRead(UsbDeviceConnection conn, int address) {
		byte reg = rtl818xRead8(conn, address);

		boolean dataIn = ((reg & ScalarConstants.RTL818X_EEPROM_CMD_WRITE) != 0);
		boolean dataOut = ((reg & ScalarConstants.RTL818X_EEPROM_CMD_READ) != 0);
		boolean dataClock = ((reg & ScalarConstants.RTL818X_EEPROM_CMD_CK) != 0);
		boolean chipSelect = ((reg & ScalarConstants.RTL818X_EEPROM_CMD_CS) != 0);

		boolean[] relevantFlags = { dataIn, dataOut, dataClock, chipSelect };
		return relevantFlags;
	}

	private static void rtl8187EepromRegisterWrite(UsbDeviceConnection conn, int address, boolean dataIn, boolean dataOut,
			boolean dataClock, boolean chipSelect) {
		int RTL818X_EEPROM_CMD_PROGRAM = (2 << 6);
		byte reg = (byte) RTL818X_EEPROM_CMD_PROGRAM;
		if (dataIn) {
			reg |= ScalarConstants.RTL818X_EEPROM_CMD_WRITE;
		}
		if (dataOut) {
			reg |= ScalarConstants.RTL818X_EEPROM_CMD_READ;
		}
		if (dataClock) {
			reg |= ScalarConstants.RTL818X_EEPROM_CMD_CK;
		}
		if (chipSelect) {
			reg |= ScalarConstants.RTL818X_EEPROM_CMD_CS;
		}

		rtl818xWrite8(conn, address, reg);
		try {
			// sleep for 10 microseconds
			Thread.sleep(0, 10000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void eeprom93cx6PulseHigh(UsbDeviceConnection conn, int address, boolean dataIn, boolean dataOut,
			boolean chipSelect) {
		boolean dataClock = true;
		rtl8187EepromRegisterWrite(conn, address, dataIn, dataOut,
				dataClock, chipSelect);
		try {
			// sleep for 450 nanoseconds
			Thread.sleep(0, 450);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void eeprom93cx6PulseLow(UsbDeviceConnection conn, int address, boolean dataIn, boolean dataOut,
			boolean chipSelect) {
		boolean dataClock = false;
		rtl8187EepromRegisterWrite(conn, address, dataIn, dataOut,
				dataClock, chipSelect);
		try {
			// sleep for 450 nanoseconds
			Thread.sleep(0, 450);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void eeprom93cx6WriteBits(UsbDeviceConnection conn, int address, int data, int bitCount) {
		boolean[] relevantFlags = rtl8187EepromRegisterRead(conn, address);
		boolean dataIn = false; // these two get set to 0 regardless of the relevant flags
		boolean dataOut = false;
		boolean dataClock = relevantFlags[2];
		boolean chipSelect = relevantFlags[3];
		
		//writeMessage(String.format("Data (i.e. command?) is %x\n", data));
		for (int i = bitCount; i > 0; i--) {
			//dataIn = !!(data & (1 << (i - 1)));
			// check if bit needs setting
			if ((data & (1 << (i - 1))) != 0) {
				dataIn = true;
			}
			else {
				dataIn = false;
			}
			//writeMessage("dataIn is" + dataIn + "\n");
			// write to eeprom register
			rtl8187EepromRegisterWrite(conn, address, dataIn, dataOut,
			dataClock, chipSelect);
			//pulse
			eeprom93cx6PulseHigh(conn, address, dataIn, dataOut, chipSelect);
			eeprom93cx6PulseLow(conn, address, dataIn, dataOut, chipSelect);
		}
		dataIn = false;
		rtl8187EepromRegisterWrite(conn, address, dataIn, dataOut, dataClock, chipSelect);
	}
	
	
	private static short eeprom93cx6ReadBits(UsbDeviceConnection conn, int address) {
		boolean[] relevantFlags = rtl8187EepromRegisterRead(conn, address);
		boolean dataIn = false;
		boolean dataOut = false;
		boolean dataClock = relevantFlags[2];
		boolean chipSelect = relevantFlags[3];
		int bitCount = 16; // i think this will always be a constant...
		short buff = 0;
		for (int i = bitCount; i > 0; i--) {
			eeprom93cx6PulseHigh(conn, address, dataIn, dataOut, chipSelect);
			relevantFlags = rtl8187EepromRegisterRead(conn, address);
			dataIn = false;
			dataOut = relevantFlags[1];
			dataClock = relevantFlags[2];
			chipSelect = relevantFlags[3];
			if (dataOut) {
				buff |= (1 << (i - 1));
			}
			eeprom93cx6PulseLow(conn, address, dataIn, dataOut, chipSelect);
		}
		return buff;
	}
	
	private static void eeprom93cx6Cleanup(UsbDeviceConnection conn, int address) {
		boolean[] relevantFlags = rtl8187EepromRegisterRead(conn, address);
		boolean dataIn = false;
		boolean dataOut = relevantFlags[1];
		boolean dataClock = relevantFlags[2];
		boolean chipSelect = false;
		rtl8187EepromRegisterWrite(conn, address, dataIn, dataOut,
				dataClock, chipSelect);
		eeprom93cx6PulseHigh(conn, address, dataIn, dataOut, chipSelect);
		eeprom93cx6PulseLow(conn, address, dataIn, dataOut, chipSelect);
	}
	
	private static int rtl8187InitHardware(UsbDeviceConnection conn) {
		//writeMessage("Entered rtl8187InitHardware().\n"); milliSleep(3000);
		rtl8187SetAnaparam(conn, true);
		
		rtl818xWrite16(conn, 0xff3c, ByteBuffer.allocate(2).putShort((short)0x0000).array(), 0x000);
		
		milliSleep(200);
		rtl818xWrite8(conn, 0xfe18, (byte)0x10);
		rtl818xWrite8(conn, 0xfe18, (byte)0x11);
		rtl818xWrite8(conn, 0xfe18, (byte)0x00);
		milliSleep(200);
		
		int res = rtl8187CmdReset(conn);
		if (res != 0) {
			return res;
		}
		
		rtl8187SetAnaparam(conn, true);
		
		rtl818xWrite16(conn, 0xff84, ByteBuffer.allocate(2).putShort((short)0x0000).array(), 0x0000);
		rtl818xWrite8(conn, 0xff91, (byte)0x00);
		
		rtl818xWrite16(conn, 0xff84, ByteBuffer.allocate(2).putShort((short)(4 << 8)).array(), 0x0000);
		rtl818xWrite8(conn, 0xff91, (byte)0x1);
		rtl818xWrite8(conn, 0xff90, (byte)0x00);
		
		rtl818xWrite8(conn, 0xff50, (byte)(3 << 6));
		
		rtl818xWrite16(conn, 0xfff4, ByteBuffer.allocate(2).putShort((short)0xffff).array(), 0x0000);
		byte reg = rtl818xRead8(conn, 0xff52);
		reg &= 0x3F;
		reg |= 0x80;
		rtl818xWrite8(conn, 0xff52, reg);
		
		rtl818xWrite8(conn, 0xff50, (byte)(0 << 6));
		
		rtl818xWrite32(conn, 0xff48, ByteBuffer.allocate(4).putInt(0x00000000).array());
		rtl818xWrite8(conn, 0xffb0, (byte)0x00);
		rtl818xWrite8(conn, 0xffbe, (byte)0x00);
		
		rtl818xWrite8(conn, 0xff34, (byte)((8 << 4) | 0));
		rtl818xWrite16(conn, 0xff2c, ByteBuffer.allocate(2).putShort((short)0x01F3).array(), 0x0000);
		

		
		rtl818xWrite16(conn, 0xff84, ByteBuffer.allocate(2).putShort((short)0x0000).array(), 0x0000);
		rtl818xWrite8(conn, 0xff91, (byte)0x00);
		reg = rtl818xRead8(conn, 0xfe53);
		rtl818xWrite8(conn, 0xfe53, (byte)(reg | (1 << 7)));
		rtl818xWrite16(conn, 0xff84, ByteBuffer.allocate(2).putShort((short)(4 << 8)).array(), 0x0000);
		rtl818xWrite8(conn, 0xff91, (byte)0x20);
		rtl818xWrite8(conn, 0xff90, (byte)0x00);
		rtl818xWrite16(conn, 0xff80, ByteBuffer.allocate(2).putShort((short)0x80).array(), 0x0000);
		rtl818xWrite16(conn, 0xff84, ByteBuffer.allocate(2).putShort((short)0x80).array(), 0x0000);
		rtl818xWrite16(conn, 0xff82, ByteBuffer.allocate(2).putShort((short)0x80).array(), 0x0000);
		milliSleep(100);//haveASleep(100000000); haveASleep seems to failwhale with big values like this for some reason

		rtl818xWrite32(conn, 0xff8c, ByteBuffer.allocate(4).putInt(0x000a8008).array());
		rtl818xWrite16(conn, 0xff2c, ByteBuffer.allocate(2).putShort((short)0xffff).array(), 0x0000);
		rtl818xWrite32(conn, 0xff88, ByteBuffer.allocate(4).putInt(0x00100044).array());
		rtl818xWrite8(conn, 0xff50, (byte)(3 << 6));
		rtl818xWrite8(conn, 0xff59, (byte)0x44);
		rtl818xWrite8(conn, 0xff50, (byte)(0 << 6));
		rtl818xWrite16(conn, 0xff82, ByteBuffer.allocate(2).putShort((short)0x1ff7).array(), 0x0000);
		milliSleep(100);//haveASleep(100000000);
		//writeMessage("Instigating a superfulous 5 second sleep.\n"); milliSleep(5000);
		
		writeMessage("Entering rtl8225z2RfInit()...\n");
		rtl8225z2RfInit(conn); // like priv->rf-init(dev); in C source.
        writeMessage("Exited rtl8225z2RfInit().\n");
		
		rtl818xWrite16(conn, 0xff2c, ByteBuffer.allocate(2).putShort((short)0x01f3).array(), 0x0000);
		reg = (byte)(rtl818xRead8(conn, 0xff5e) & (byte)(~1));
		rtl818xWrite8(conn, 0xff5e, (byte)(reg | (byte)(1)));
		rtl818xWrite16(conn, 0xfffe, ByteBuffer.allocate(2).putShort((short)0x10).array(), 0x0000);
		rtl818xWrite8(conn, 0xfffc, (byte)0x80);
		rtl818xWrite8(conn, 0xffff, (byte)0x60);
		rtl818xWrite8(conn, 0xff5e, reg);
		
		return 0;
	}
	
	private static int rtl8187CmdReset(UsbDeviceConnection conn) {
		byte reg = rtl818xRead8(conn, 0xff37);
		reg &= (1 << 1);
		reg |= (1 << 4);
		rtl818xWrite8(conn, 0xff37, reg);
		
		int i = 10;
		do {
			milliSleep(2);
			if ((rtl818xRead8(conn, 0xff37) & (1 << 4)) == 0) {
				break;
			}
		} while (--i > 0);
		
		if (i == 0) {
			writeMessage("ERROR: reset timeout.\n");
			return 238;
		}
		
		rtl818xWrite8(conn, 0xff50, (byte)(1 << 6));
		
		i = 10;
		do {
			milliSleep(4);
			if ((rtl818xRead8(conn, 0xff50) & (3 << 6)) == 0) {
				break;
			}
		} while (--i > 0);
		
		if (i == 0) {
			writeMessage("ERROR: eeprom reset timeout.\n");
			return 238;
		}
		return 0;
	}
	
	
	private static void rtl8187SetAnaparam(UsbDeviceConnection conn, boolean rfon) {
		int anaparam, anaparam2;
		if(rfon) {
			anaparam =  0xa0000a59;//anaparam = 0x45090658;
			anaparam2 = 0x860c7312;
		}
		else {
			anaparam = 0xa00beb59;
			anaparam2 = 0x840dec11;
		}
		rtl818xWrite8(conn, 0xff50, (byte)(3 << 6));
		byte reg = rtl818xRead8(conn, 0xff59);
		reg |= (byte) (1 << 6);
		rtl818xWrite8(conn, 0xff59, reg);
		rtl818xWrite32(conn, 0xff54, ByteBuffer.allocate(4).putInt(anaparam).array());
		rtl818xWrite32(conn, 0xff60, ByteBuffer.allocate(4).putInt(anaparam2).array());
		reg &= (byte)(~(1 << 6));
		rtl818xWrite8(conn, 0xff59, reg);
		rtl818xWrite8(conn, 0xff50, (byte)(0 << 6));
	}
	
	private static void rtl8225z2RfInit(UsbDeviceConnection conn) {
		rtl8225Write(conn, 0x0, ByteBuffer.allocate(2).putShort((short)0x2bf).array());
		rtl8225Write(conn, 0x1, ByteBuffer.allocate(2).putShort((short)0xee0).array());
		rtl8225Write(conn, 0x2, ByteBuffer.allocate(2).putShort((short)0x44d).array());
		rtl8225Write(conn, 0x3, ByteBuffer.allocate(2).putShort((short)0x441).array());
		rtl8225Write(conn, 0x4, ByteBuffer.allocate(2).putShort((short)0x8c3).array());
		rtl8225Write(conn, 0x5, ByteBuffer.allocate(2).putShort((short)0xc72).array());
		rtl8225Write(conn, 0x6, ByteBuffer.allocate(2).putShort((short)0x0e6).array());
		rtl8225Write(conn, 0x7, ByteBuffer.allocate(2).putShort((short)0x82a).array());
		rtl8225Write(conn, 0x8, ByteBuffer.allocate(2).putShort((short)0x03f).array());
		rtl8225Write(conn, 0x9, ByteBuffer.allocate(2).putShort((short)0x335).array());
		rtl8225Write(conn, 0xa, ByteBuffer.allocate(2).putShort((short)0x9d4).array());
		rtl8225Write(conn, 0xb, ByteBuffer.allocate(2).putShort((short)0x7bb).array());
		rtl8225Write(conn, 0xc, ByteBuffer.allocate(2).putShort((short)0x850).array());
		rtl8225Write(conn, 0xd, ByteBuffer.allocate(2).putShort((short)0xcdf).array());
		rtl8225Write(conn, 0xe, ByteBuffer.allocate(2).putShort((short)0x02b).array());
		rtl8225Write(conn, 0xf, ByteBuffer.allocate(2).putShort((short)0x114).array());
		milliSleep(100);
		
		rtl8225Write(conn, 0x0, ByteBuffer.allocate(2).putShort((short)0x1b7).array());
		
		int i = 0;
		// 95 iterations at time of writing
		for (short gain: ArrayConstants.rtl8225z2_rxgain) {
			rtl8225Write(conn, 0x1, ByteBuffer.allocate(2).putShort((short)(i+1)).array());
			rtl8225Write(conn, 0x2, ByteBuffer.allocate(2).putShort(gain).array());
			i++;
		}
		
		rtl8225Write(conn, 0x3, ByteBuffer.allocate(2).putShort((short)0x080).array());
		rtl8225Write(conn, 0x5, ByteBuffer.allocate(2).putShort((short)0x004).array());
		rtl8225Write(conn, 0x0, ByteBuffer.allocate(2).putShort((short)0x0b7).array());
		rtl8225Write(conn, 0x2, ByteBuffer.allocate(2).putShort((short)0xc4d).array());
		
		milliSleep(200);
		rtl8225Write(conn, 0x2, ByteBuffer.allocate(2).putShort((short)0x44d).array());
		milliSleep(100);
		
		short rtl8225Read6ToShort = ByteBuffer.wrap(rtl8225Read(conn, 6)).getShort();
		// this condition seems to evaluate to false in Linux, so this code branch may never
		// get exercised normally.
		if ((rtl8225Read6ToShort & (short)(1 << 7)) == 0) {
			rtl8225Write(conn, 0x02, ByteBuffer.allocate(2).putShort((short)0x0c4d).array());
			milliSleep(200);
			rtl8225Write(conn, 0x02, ByteBuffer.allocate(2).putShort((short)0x044D).array());
			milliSleep(100);
			rtl8225Read6ToShort = ByteBuffer.wrap(rtl8225Read(conn, 6)).getShort();
			if ((rtl8225Read6ToShort & (short)(1 << 7)) == 0) {
				writeMessage("RF Calibration Failed!\n");
			}
		}
		
		milliSleep(200);
		
		rtl8225Write(conn, 0x0, ByteBuffer.allocate(2).putShort((short)0x2bf).array());
		
		i = 0;
		for (short agc: ArrayConstants.rtl8225_agc) {
			rtl8225WritePhyOfdm(conn, 0xb, agc);
			rtl8225WritePhyOfdm(conn, 0xa, 0x80 + i);
			i++;
		}
		
		milliSleep(1);
		
		rtl8225WritePhyOfdm(conn, 0x00, 0x01);
		rtl8225WritePhyOfdm(conn, 0x01, 0x02);
		rtl8225WritePhyOfdm(conn, 0x02, 0x42); //writePhyOfdm(conn, 0x02, 0x62); haveASleep(1000000); // ?? why does the hex dump show 0x42 and not 0x62? NEVERMIND
		rtl8225WritePhyOfdm(conn, 0x03, 0x00); 
		rtl8225WritePhyOfdm(conn, 0x04, 0x00); 
		rtl8225WritePhyOfdm(conn, 0x05, 0x00); 
		rtl8225WritePhyOfdm(conn, 0x06, 0x40);
		rtl8225WritePhyOfdm(conn, 0x07, 0x00);
		rtl8225WritePhyOfdm(conn, 0x08, 0x40);
		rtl8225WritePhyOfdm(conn, 0x09, 0xfe);
		rtl8225WritePhyOfdm(conn, 0x0a, 0x08);
		rtl8225WritePhyOfdm(conn, 0x0b, 0x80);
		rtl8225WritePhyOfdm(conn, 0x0c, 0x01);
		rtl8225WritePhyOfdm(conn, 0x0d, 0x43);
		rtl8225WritePhyOfdm(conn, 0x0e, 0xd3);
		rtl8225WritePhyOfdm(conn, 0x0f, 0x38);
		rtl8225WritePhyOfdm(conn, 0x10, 0x84);
		rtl8225WritePhyOfdm(conn, 0x11, 0x07);
		rtl8225WritePhyOfdm(conn, 0x12, 0x20);
		rtl8225WritePhyOfdm(conn, 0x13, 0x20);
		rtl8225WritePhyOfdm(conn, 0x14, 0x00);
		rtl8225WritePhyOfdm(conn, 0x15, 0x40);
		rtl8225WritePhyOfdm(conn, 0x16, 0x00);
		rtl8225WritePhyOfdm(conn, 0x17, 0x40);
		rtl8225WritePhyOfdm(conn, 0x18, 0xef);
		rtl8225WritePhyOfdm(conn, 0x19, 0x19);
		rtl8225WritePhyOfdm(conn, 0x1a, 0x20);
		rtl8225WritePhyOfdm(conn, 0x1b, 0x15);
		rtl8225WritePhyOfdm(conn, 0x1c, 0x04);
		rtl8225WritePhyOfdm(conn, 0x1d, 0xc5);
		rtl8225WritePhyOfdm(conn, 0x1e, 0x95);
		rtl8225WritePhyOfdm(conn, 0x1f, 0x75);
		rtl8225WritePhyOfdm(conn, 0x20, 0x1f);
		rtl8225WritePhyOfdm(conn, 0x21, 0x17);
		rtl8225WritePhyOfdm(conn, 0x22, 0x16);
		rtl8225WritePhyOfdm(conn, 0x23, 0x80);
		rtl8225WritePhyOfdm(conn, 0x24, 0x46);
		rtl8225WritePhyOfdm(conn, 0x25, 0x00);
		rtl8225WritePhyOfdm(conn, 0x26, 0x90);
		rtl8225WritePhyOfdm(conn, 0x27, 0x88);
		
		
		rtl8225WritePhyOfdm(conn, 0x0b, ArrayConstants.rtl8225z2_gain_bg[4*3]);
		rtl8225WritePhyOfdm(conn, 0x1b, ArrayConstants.rtl8225z2_gain_bg[4*3 + 1]);
		rtl8225WritePhyOfdm(conn, 0x1d, ArrayConstants.rtl8225z2_gain_bg[4*3 + 2]);
		rtl8225WritePhyOfdm(conn, 0x21, 0x37);
		
		
		rtl8225WritePhyCck(conn, 0x0, 0x98);
		rtl8225WritePhyCck(conn, 0x3, 0x20);
		rtl8225WritePhyCck(conn, 0x4, 0x7e);
		rtl8225WritePhyCck(conn, 0x5, 0x12);
		rtl8225WritePhyCck(conn, 0x6, 0xfc);
		rtl8225WritePhyCck(conn, 0x7, 0x78);
		rtl8225WritePhyCck(conn, 0x8, 0x2e);
		rtl8225WritePhyCck(conn, 0x10, 0x9b);//writePhyCck(conn, 0x10, 0x93); haveASleep(1000000); // trace shows 9b, not 93 ??? NEVERMIND
		rtl8225WritePhyCck(conn, 0x11, 0x88);
		rtl8225WritePhyCck(conn, 0x12, 0x47);
		rtl8225WritePhyCck(conn, 0x13, 0xd0);
		rtl8225WritePhyCck(conn, 0x19, 0x00);
		rtl8225WritePhyCck(conn, 0x1a, 0xa0);
		rtl8225WritePhyCck(conn, 0x1b, 0x08);
		rtl8225WritePhyCck(conn, 0x40, 0x86);
		rtl8225WritePhyCck(conn, 0x41, 0x8d);
		rtl8225WritePhyCck(conn, 0x42, 0x15);
		rtl8225WritePhyCck(conn, 0x43, 0x18);
		rtl8225WritePhyCck(conn, 0x44, 0x36);
		rtl8225WritePhyCck(conn, 0x45, 0x35);
		rtl8225WritePhyCck(conn, 0x46, 0x2e);
		rtl8225WritePhyCck(conn, 0x47, 0x25);
		rtl8225WritePhyCck(conn, 0x48, 0x1c);
		rtl8225WritePhyCck(conn, 0x49, 0x12);
		rtl8225WritePhyCck(conn, 0x4a, 0x09);
		rtl8225WritePhyCck(conn, 0x4b, 0x04);
		rtl8225WritePhyCck(conn, 0x4c, 0x05);
		
		rtl818xWrite8(conn, 0xff5b, (byte)0x0d); milliSleep(1);
		
		rtl8225z2RfSetTxPower(conn, 1);
		
		rtl8225WritePhyCck(conn, 0x10, 0x9b);
		rtl8225WritePhyOfdm(conn, 0x26, 0x90);
		
		rtl818xWrite8(conn, 0xff9f, (byte)0x03);
		milliSleep(1);
		rtl818xWrite32(conn, 0xff94, ByteBuffer.allocate(4).putInt(0x3dc00002).array());
	}
	
    private static void rtl8225z2RfSetTxPower(UsbDeviceConnection conn, int channel) {
    	// TODO: this is a nasty nasty implementation.  I inline stuff that makes it
    	// non-reusable because of variables it depends on that I didn't set up 
    	// properly earlier in the probe initialisation stuff.  Don't call this function
    	// unless you definitely know my inlining remains applicable to what you're doing.
    	rtl8225WritePhyCck(conn, 0x44, 0x36);
    	rtl8225WritePhyCck(conn, 0x45, 0x35);
    	rtl8225WritePhyCck(conn, 0x46, 0x2e);
    	rtl8225WritePhyCck(conn, 0x47, 0x25);
    	rtl8225WritePhyCck(conn, 0x48, 0x1c);
    	rtl8225WritePhyCck(conn, 0x49, 0x12);
    	rtl8225WritePhyCck(conn, 0x4a, 0x09);
    	rtl8225WritePhyCck(conn, 0x4b, 0x04);
    	
    	rtl818xWrite8(conn, 0xff9d, (byte)0x0a);
    	
    	milliSleep(1);
    	
    	rtl818xWrite8(conn, 0xff50, (byte)(3 << 6));
    	int reg = (int)rtl818xRead8(conn, 0xff59);  // TODO: we should be using this value rather than inlining 0xcd in next line.
    	rtl818xWrite8(conn, 0xff59, (byte)(0xcd)); //rtl818xWrite8(conn, 0xff59, (byte)(reg | (1 << 6)));
    	rtl818xWrite32(conn, 0xff60, ByteBuffer.allocate(4).putInt(0x860c7312).array());
    	rtl818xWrite8(conn, 0xff59, (byte)(0x8d)); //rtl818xWrite8(conn, 0xff59, (byte)(reg & ~(1 << 6)));
    	rtl818xWrite8(conn, 0xff50, (byte)(0 << 6));
    	
    	rtl8225WritePhyOfdm(conn, 2, 0x42);
    	rtl8225WritePhyOfdm(conn, 5, 0x00);
    	rtl8225WritePhyOfdm(conn, 6, 0x40);
    	rtl8225WritePhyOfdm(conn, 7, 0x00);
    	rtl8225WritePhyOfdm(conn, 8, 0x40);
    	
    	rtl818xWrite8(conn, 0xff9e, (byte)0x12);
    	
    	milliSleep(1);
    }
	
    
    
	// i make some exceptions here and pass data as an int rather than byte[] as we have to do 
	// a lot of bit-shifting that is probably easier starting off with an int.
	
    private static void rtl8225WritePhyOfdm(UsbDeviceConnection conn, int address, int data) {
    	rtl8187WritePhy(conn, address, data);
    }
    
    private static void rtl8187WritePhy(UsbDeviceConnection conn, int address, int data) {
    	data <<= 8;
    	data |= address | 0x80;
    	
    	// WARNING: I have substituted literals oxff7f, 0xff7e, oxff7d, oxff7c here for
    	// things which might in future change (PHY).
    	rtl818xWrite8(conn, 0xff7f, (byte)((data >> 24) & 0xff));
    	rtl818xWrite8(conn, 0xff7e, (byte)((data >> 16) & 0xff));
    	rtl818xWrite8(conn, 0xff7d, (byte)((data >> 8) & 0xff));
    	rtl818xWrite8(conn, 0xff7c, (byte)(data & 0xff));
    }
    
    private static void rtl8225WritePhyCck(UsbDeviceConnection conn, int address, int data) {
    	rtl8187WritePhy(conn, address, data | 0x10000);
    }
    
    /*private static void writePhyOfdm(UsbDeviceConnection conn, int address, int data) {
		data = data & 0xff;
		rtl8185WritePhy(conn, address, data);
	}*/
	
	/*private static void rtl8185WritePhy(UsbDeviceConnection conn, int address, int data) {
		address |= 0x80;
		int phyw = ((data<<8) | address);
		
		rtl818xWrite8(conn, 0xff7f, (byte)((phyw & 0xff000000) >> 24));
		rtl818xWrite8(conn, 0xff7e, (byte)((phyw & 0x00ff0000) >> 16));
		rtl818xWrite8(conn, 0xff7d, (byte)((phyw & 0x0000ff00) >> 8));
		rtl818xWrite8(conn, 0xff7c, (byte)((phyw & 0x000000ff)));
	}*/
	
	/*private static void writePhyCck(UsbDeviceConnection conn, int address, int data) {
		data = data & 0xff;
		rtl8185WritePhy(conn, address, data | 0x10000);
	}*/
	
//	private static void writeRtl8225(UsbDeviceConnection conn,int address, byte[] data) {
//		// this method was implemented almost exclusively based off of hex trace as I couldn't locate it's C implementation
//		// we don't even capture the result of these three reads, but i do them anyway in case of side effects
//		rtl818xRead16(conn, 0xff80);
//		rtl818xRead16(conn, 0xff82);
//		rtl818xRead16(conn, 0xff84);
//		rtl818xWrite16(conn, 0xff82, ByteBuffer.allocate(2).putShort((short)0x1ff7).array(),0x0000);
//		rtl818xWrite16(conn, 0xff84, ByteBuffer.allocate(2).putShort((short)0x0087).array(), 0x0000);
//		rtl818xWrite16(conn, 0xff80, ByteBuffer.allocate(2).putShort((short)0x0084).array(), 0x0000);
//		rtl818xWrite16(conn, 0xff80, ByteBuffer.allocate(2).putShort((short)0x0080).array(), 0x0000);
//		rtl818xWrite16(conn, address, data, 0x8225); // NOTE: I may need to reverse wIndex endianness, but I don't think so.
//		rtl818xWrite16(conn, 0xff80, ByteBuffer.allocate(2).putShort((short)0x0084).array(), 0x0000);
//		rtl818xWrite16(conn, 0xff80, ByteBuffer.allocate(2).putShort((short)0x0084).array(), 0x0000);
//		rtl818xWrite16(conn, 0xff84, ByteBuffer.allocate(2).putShort((short)0x0080).array(), 0x0000);
//	}
	
//	private static void rtl8225HostPciInit(UsbDeviceConnection conn) {
//		
//		// TODO: finish writing this method
//	}
	
	// PRIVATE UTILITY METHODS //
	private static byte[] leShort2BeByteArray(short s) {
		ByteBuffer buff = ByteBuffer.allocate(2);
		buff.putShort(s);
		//buff.order(ByteOrder.LITTLE_ENDIAN);
		//buff.flip(); // apparently not required
		byte[] tmp = {0, 0};
		tmp[0] = buff.array()[1];
		tmp[1] = buff.array()[0];
		return tmp;
	}
	
	private static byte[] leInt2BeByteArray(int i) {
		ByteBuffer buff = ByteBuffer.allocate(4);
		buff.putInt(i);
		//buff.order(ByteOrder.LITTLE_ENDIAN);
		//buff.flip(); // apparently not required
		byte[] tmp = {0, 0, 0, 0};
		tmp[0] = buff.array()[3];
		tmp[1] = buff.array()[2];
		tmp[2] = buff.array()[1];
		tmp[3] = buff.array()[0];
		return tmp;
	}
	
	private static byte[] reverseByteOrder(byte[] arr, int length) {
		byte[] newArr = new byte[length];
		int currArrIndex = length -1;
		for (int i = 0; i < length; i++) {
			newArr[currArrIndex] = arr[i];
			currArrIndex -= 1;
		}
		return newArr;
	}
	
	private static void haveASleep(int nanoseconds) {
		try {
			Thread.sleep(0, nanoseconds);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static void milliSleep(int milliseconds) {
		try {
			Thread.sleep(milliseconds);
		} catch (InterruptedException e) {
			// TODO something more useful in catch block
			e.printStackTrace();
		}
	}
}
