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

/*
 * Important constants.
 */
public class ScalarConstants {
	public static final byte RTL818X_EEPROM_CMD_CONFIG = (byte) (3 << 6);
	public static final int RTL8187_EEPROM_MAC_ADDR = 0x07;
	public static final int RTL8187_EEPROM_TXPWR_BASE = 0x05;
	public static final int RTL8187_EEPROM_TXPWR_CHAN_1 = 0x16;
	public static final int RTL8187_EEPROM_TXPWR_CHAN_6 = 0x1b;
	public static final int RTL8187_EEPROM_TXPWR_CHAN_4 = 0x3d;
	public static final int RTL818X_TX_CONF_HWVER_MASK = (7 << 25);
	public static final int RTL818X_TX_CONF_R8187VD_B = (6 << 25);
	public static final int RTL818X_TX_CONF_R8187VD = (5 << 25);
	
	public static final int RTL818X_RX_CONF_MONITOR = (1 << 0);
	
	
	public static final int EEPROM_CMD = 0xff50;
	public static final int TX_CONF = 0xff40;
	
	public static int RTL818X_EEPROM_CMD_WRITE = (1 << 1);
	public static int RTL818X_EEPROM_CMD_READ = (1 << 0);
	public static int RTL818X_EEPROM_CMD_CK = (1 << 2);
	public static int RTL818X_EEPROM_CMD_CS = (1 << 3);
	public static int PCI_EEPROM_READ_OPCODE = 0x06;
}
