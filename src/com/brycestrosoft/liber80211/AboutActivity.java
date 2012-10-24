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
import java.io.InputStream;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
import android.widget.TextView;

public class AboutActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        
        TextView license = (TextView)findViewById(R.id.license);
        license.setText(read_gpl_license());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_about, menu);
        return true;
    }
    
    private String read_gpl_license() {
    	InputStream inputStream = getResources().openRawResource(R.raw.gpl_v2_license);
    	ByteArrayOutputStream  byteArrayOutputStream = new ByteArrayOutputStream();
    	int i;
    	try {
    		i = inputStream.read();
    		while (i != -1) {
    			byteArrayOutputStream.write(i);
    			i = inputStream.read();
    		}
    		inputStream.close();
    	}
    	catch (IOException e) {
    		// TODO throw an exception that will print this out to screen when it happens.
    		e.printStackTrace();
    	}
    	return byteArrayOutputStream.toString();
    }
}
