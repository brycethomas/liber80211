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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.util.Arrays;

import com.brycestrosoft.liber80211.R;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

/*
 * The primary Android activity that you seen when the app launches.
 */
public class MainActivity extends Activity {
    
    private RxThread _rx;
    private UsbDeviceConnection _conn;
    private UsbInterface _intf_0;
    private TextView _txt_general_info;
    private OutputStreamWriter _osw_log;
    private BroadcastReceiver _usb_detached_receiver;
    LocationManager _loc_manager;
    LocationListener _loc_listener;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // setup a receiver to be notified when the Wi-Fi card is removed.
        _usb_detached_receiver = finish_on_usb_detached();
        
        // the textview that displays info about what was heard over the air.
        final TextView txt_rx =(TextView)findViewById(R.id.textView2);
        txt_rx.setMovementMethod(new ScrollingMovementMethod()); 
        
        // get the USB stuff that we'll need to pass to our I/O methods
        Intent intent = getIntent();
        UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        UsbManager usb_manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        _conn = usb_manager.openDevice(device);
        _intf_0 = device.getInterface(0);
        boolean force_claim = true;
        _conn.claimInterface(_intf_0, force_claim);
        
        // Setup a log file.
        File external_root = null;
        try {
        	if(!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())){
        		//TODO: I'm not sure this toast will actually display properly.  I seem to have
        		// issues displaying toast even at the very beginning of the try block outside of
        		// the if statement.
        	    Toast.makeText(this, "External SD card not mounted", Toast.LENGTH_LONG).show();
        	}
        	
        	external_root = Environment.getExternalStorageDirectory();
        	String log_file_name = "liber80211_" + String.valueOf(System.currentTimeMillis()) + ".txt";
        	FileOutputStream log_file = new FileOutputStream(new File(external_root, log_file_name));
			_osw_log = new OutputStreamWriter(log_file);
		} catch (FileNotFoundException e) {
			txt_rx.append("...__..." + e.getClass().toString() + "\n" + e.getMessage() + "\n" + e.getLocalizedMessage() + 
        			   "\n" + Arrays.toString(e.getStackTrace()) + "\n");
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        // give USBIO an output stream and textview it can use for logging.
        USBIO.set_log_file(_osw_log);
        USBIO.set_text_view(txt_rx);
        
        try {
        	USBIO.do_log = true;
        	/*** THIS IS WHERE WE GET DOWN TO BUSINESS ON THE WIRE. ***/
        	// rtl8187_probe() here is meant to behave (on the wire) like rtl8187_probe() in the Linux driver.
        	rtl8187_probe(_conn, device);
        	// rtl8187_rfkill_poll() here is meant to behave (on the wire) like rtl8187_rfkill_poll() in the Linux driver.
        	USBIO.rtl8187_rfkill_poll(_conn); // seems to be first "_ops" function called after probe phase.
        	// TODO: linux driver does rfkill_init'ing here
        	USBIO.do_log = true;
        	UsbEndpoint ep = _intf_0.getEndpoint(0);
        	// rtl8187_start() here is meant to behave (on the wire) like rtl8187_start() in the Linux driver.
        	_rx = USBIO.rtl8187_start(_conn, ep, this);
        	USBIO.do_log = true;
        	// rtl8187_add_interface() here is meant to behave (on the wire) like rtl8187_add_interface() in the Linux driver.
        	USBIO.rtl8187_add_interface(_conn, DeviceProperties.mac);
        }
        catch (Exception e) {
        	txt_rx.append(e.getClass().toString() + "\n" + e.getMessage() + "\n" + e.getLocalizedMessage() + 
        			   "\n" + Arrays.toString(e.getStackTrace()) + "\n");
        }
        
        // display some of the info
        _txt_general_info=(TextView)findViewById(R.id.textViewBla);
        _txt_general_info.append(device.getVendorId() + "\n");
        _txt_general_info.append("eeprom width is " + DeviceProperties.eeprom_width + "\n");   
        _txt_general_info.append(String.format("MAC is %02X:%02X:%02X:%02X:%02X:%02X\n",DeviceProperties.mac[0],
        		DeviceProperties.mac[1],DeviceProperties.mac[2],DeviceProperties.mac[3],
        		DeviceProperties.mac[4],DeviceProperties.mac[5]));
        _txt_general_info.append("Interface count: " + device.getInterfaceCount() + "\n");
        _txt_general_info.append("Interface 1 endpoint count: " + device.getInterface(0).getEndpointCount() + "\n");
        if (external_root != null) {
            _txt_general_info.append("Text logging file dir is: " + external_root.getAbsolutePath() + "\n");
        }
        
        
        _loc_manager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        
        _loc_listener = new LocationListener() {
        	public void onLocationChanged(Location location) {
        		String time = String.valueOf(location.getTime());
        		String lat = String.valueOf(location.getLatitude());
        		String lon = String.valueOf(location.getLongitude());
        		String alt = "NA";
        		if (location.hasAltitude()) {
        			alt = String.valueOf(location.getAltitude());	
        		}
        		String acc = "NA";
        		if (location.hasAccuracy()) {
        			acc = String.valueOf(location.getAccuracy());
        		}
        		String speed = "NA";
        		if (location.hasSpeed()) {
        			speed = String.valueOf(location.getSpeed());
        		}
        		String bearing = "NA";
        		if (location.hasBearing()) {
        			bearing = String.valueOf(location.getBearing());
        		}
        		String provider = "NA";
        		if (location.getProvider() != null) {
        			provider = location.getProvider();
        		}
        		String loc_reading_format = "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s.\n";
        		txt_rx.append(System.currentTimeMillis() + "\t" +
        				String.format(loc_reading_format, time, lat, lon, alt, acc, speed, bearing, provider));
        		try {
					_osw_log.write(System.currentTimeMillis() + "\t" +
							String.format(loc_reading_format, time, lat, lon, alt, acc, speed, bearing, provider));
				} catch (IOException e) {
					txt_rx.append("stuffed up writing location reading to file.\n");
				}
        		
        	}
        	
        	public void onStatusChanged(String provider, int status, Bundle extras) {}
        	
        	public void onProviderEnabled(String provider) {}
        	
        	public void onProviderDisabled(String provider) {}
        };
        
        _loc_manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, _loc_listener);
        _loc_manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, _loc_listener);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	Intent intent;
    	switch (item.getItemId()) {
    	case R.id.menu_about:
    		intent = new Intent(this, AboutActivity.class);
    		startActivity(intent);
    		return true;
    	default:
    		return super.onOptionsItemSelected(item);
    	}
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	/*_rx.stopThread()*/
    	_conn.releaseInterface(_intf_0);
    	_conn.close();
    	try {
			_osw_log.flush();
	    	_osw_log.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	unregisterReceiver(_usb_detached_receiver);
    	// TODO: we really need an exit button in the app that kills it completely,
    	// rather than relying on the user to go into running apps and kill it.
    	_loc_manager.removeUpdates(_loc_listener);

    	_txt_general_info.append("Cleanup done.\n");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
    
    private int rtl8187_probe(UsbDeviceConnection conn, UsbDevice device) {
    	// we (ab)use this to prime the DeviceProperties eeprom width cached value for other methods.
    	USBIO.get_eeprom_width(conn); 
    	USBIO.perform_probe_black_magic_write_one(conn);
    	// another (ab)use to prime DeviceProperties, this time mac address.
    	USBIO.get_mac_address(conn);
    	USBIO.setup_channels_part_one(conn);
        byte mysteryReg = USBIO.perform_probe_black_magic_read_write_one(conn);
        // another (ab)use, this time asic revision priming.
        USBIO.get_asic_revision(conn);
        USBIO.perform_probe_black_magic_write_write_one(conn, mysteryReg);
        // more priming (ab)use.
        USBIO.get_hardware_revision(conn, device);
        USBIO.setup_channels_part_two(conn);
        // linux driver does some GPIO settings stuff here, but doesn't look like it applies to my AWUS 036H
        // TODO: at the moment we don't make use of the return value to rtl818xRfOps here, 
        // but will need to if we want to support other cards in future.
        
        // meant to behave (on the wire) like rtl8187_detect_rf() in the Linux Driver.
        rtl818xRfOps ops = USBIO.rtl8187_detect_rf(conn, device); 
        USBIO.do_log = true;
        
        // meant to behave (on the wire) like the conditional lines in #ifdef CONFIG_RTL8187_LEDS
        // in the linux driver.
        USBIO.config_rtl_8187_leds(conn);
        // meant to behave (on the wire) like rtl8187_rfkill_init() in the Linux driver.
        USBIO.rtl8187_rfkill_init(conn);
    	return 0;
    }
    
    /*
     * Sets up the activity to call finish() when the USB device is detached.
     * The returned BroadcastReceiver would typically be unregistered in the activity's onDestroy().
     * @return the BroadcastReceiver listening for the UsbManager.ACTION_USB_DEVICE_DETACHED event.
     */
    private BroadcastReceiver finish_on_usb_detached() {
        // setup a receiver to be notified when the Wi-Fi card is removed.
        BroadcastReceiver usb_detached_receiver = new BroadcastReceiver() {
        	public void onReceive(Context context, Intent intent) {
        		String action = intent.getAction();
        		// TODO: at the moment this assumes whatever was detached was *our* USB device.
        		// Might break with a hub and multiple devices.
        		if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
        			UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        			if (device != null) {
        				finish();
        			}
        		}
        	}
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usb_detached_receiver, filter);
        return usb_detached_receiver;
    }
}
