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

import java.nio.ByteBuffer;

import android.hardware.usb.UsbRequest;

public class BulkMessage {
	ByteBuffer _buff;
	
	public BulkMessage(int size) {
		_buff = ByteBuffer.allocate(size);
	}
	
	// queue the read, returning flag indicating whether it queued successfully
	public boolean queueRead(UsbRequest req) {
		req.setClientData(this);
		return req.queue(_buff, _buff.capacity());
	}
	
	public ByteBuffer getByteBuffer() {
		return _buff;
	}
}
