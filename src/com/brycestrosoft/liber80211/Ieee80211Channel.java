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

public class Ieee80211Channel {
	Ieee80211Band band;
	short center_freq;
	short hw_value;
	int flags;
	int max_antenna_gain;
	int max_power;
	int max_reg_power;
	boolean beacon_found;
	int orig_flags;
	int orig_mag;
	int orig_mpwr;
}
