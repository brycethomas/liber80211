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

/**
 * Holds values for both invariant properties we retrieve from the connected
 * device, as well as mutable state such as device channel. Yeah, I know, global
 * mutable state is bad. It just happens to be a preferred form of complexity
 * right now than long parameter lists or a bunch of objects being marshalled
 * around.
 * */
public class DeviceProperties {
	public static int eeprom_width = -1;
	public static Boolean asic_revision = null;
	public static HardwareRevision hardware_revision = null;
	public static byte[] mac = { 0, 0, 0, 0, 0, 0 };
	public static Ieee80211Channel[] channels = {new Ieee80211Channel(),new Ieee80211Channel(),new Ieee80211Channel(),
	new Ieee80211Channel(),new Ieee80211Channel(),new Ieee80211Channel(),new Ieee80211Channel(),new Ieee80211Channel(),
	new Ieee80211Channel(),new Ieee80211Channel(),new Ieee80211Channel(),new Ieee80211Channel(),new Ieee80211Channel(),
	new Ieee80211Channel()};
}
