liber80211
==========

liber80211 (pronounced *liberate-0211*) is an Android app that **supports Wi-Fi monitor mode without root**.  liber80211 achieves this by interfacing with a specific model of external USB Wi-Fi card, the well known [ALFA AWUS036H](http://www.amazon.com/gp/product/B000QYGNKQ/ref=as_li_qf_sp_asin_il_tl?ie=UTF8&camp=1789&creative=9325&creativeASIN=B000QYGNKQ&linkCode=as2&tag=liber80211-20).

![Galaxy Nexus hooked up to ALFA AWUS036H and USB battery pack through a USB OTG Y-cable](http://i.imgur.com/9AHs4.jpg)

![Galaxy Nexus running liber80211](http://i.imgur.com/5aTBE.jpg)

## How does it work?
Android 3.0 added support for [USB On-The-Go](http://en.wikipedia.org/wiki/USB_On-The-Go) (a.k.a. USB OTG, USB Host Mode) which allows the Android phone to act as the host computer and the plugged in USB device to act as the peripheral.  liber80211 is an Android user space port of the ALFA AWUS036H's kernel space Linux driver.  In other words, liber80211 mimics the behaviour of the Linux driver on the wire and the ALFA AWUS036H knows no different.

## What do I need to get started?
1. An [ALFA AWUS036H](http://www.amazon.com/gp/product/B000QYGNKQ/ref=as_li_qf_sp_asin_il_tl?ie=UTF8&camp=1789&creative=9325&creativeASIN=B000QYGNKQ&linkCode=as2&tag=liber80211-20).
2. A [USB OTG Cable](http://www.amazon.com/gp/product/B005QX7KYU/ref=as_li_qf_sp_asin_il_tl?ie=UTF8&camp=1789&creative=9325&creativeASIN=B005QX7KYU&linkCode=as2&tag=liber80211-20)
3. An Android handset that supports USB OTG (see below), [Galaxy Nexus](http://www.amazon.com/gp/product/B005ZEF01A/ref=as_li_qf_sp_asin_il_tl?ie=UTF8&camp=1789&creative=9325&creativeASIN=B005ZEF01A&linkCode=as2&tag=liber80211-20) recommended.

Once you have this hardware, download liber80211 from the Google Play store (or build from source) and plug in your ALFA AWUS036H.  liber80211 listens for this device to be plugged in and you should be prompted to launch it.   So don't launch liber80211 without the ALFA AWUS036H plugged in (it'll just crash).  Likewise, if you're running from source within e.g. Eclipse, liber80211 will crash when you run it because the Wi-Fi card isn't connected and it expects it to be.  That's ok though, as this will still have loaded liber80211 onto the phone, so you can disconnect the phone from the computer/plug in the ALFA AWUS036H now.

At time of writing, *USB OTG is a ghetto on Android*.  It has been supported in theory by the Android operating system since version 3.0.  In practice, many handsets manufacturers have done a lowsy job implementing the spec or don't provision enough power to run the Wi-Fi card.  I've done all my testing on a Galaxy Nexus.  There's also reports of the Nexus 7 tablet having good USB OTG support.  On other devices, your mileage may vary (let me know so I can put together a list).

## So what can liber80211 do for me today?
The brutally honest answer here is *not much*.  The good news is though, I've done a lot of the heavy lifting (porting the Linux kernel driver to Android user space).  I've released liber80211 at a very early stage as a minimal working example of a codebase that successfully puts the ALFA AWUS036H into monitor mode and receives bytes over the air.  At the moment liber80211 demonstrates this by displaying on screen the MAC address and ESSID of 802.11 `Probe Request` frames, a type of frame you typically would not see were you not in monitor mode.

## What's the vision for liber80211?
Simply, to liberate 802.11 on Android handsets.  To be able to transmit and receive anything the physical medium allows, unencumbered by artificial constraints on the hardware.  A few things I'd like to see supported:

1. Full packet capture.
2. Packet injection.
3. Ports of standard 802.11 penetration testing tools.
4. Support for other Wi-Fi cards.
5. A usable interface!
6. Framework for experimental new protocols. 

## How can I help improve liber80211 or say thanks?
You can donate beer ($5), sushi ($15) or a briefcase of cash (you choose amount):

<center>![Beer](http://i.imgur.com/7uZHi.jpg)
<p><a href="https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&amp;hosted_button_id=5Z4NPY6RWU3G4"><img src="https://www.paypalobjects.com/en_US/i/btn/btn_donate_SM.gif" alt="Donate to author" style="max-width:100%;"></a></p></center>

<center>![Sushi](http://i.imgur.com/FhKiQ.png)
<p><a href="https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&amp;hosted_button_id=HYK97ZBXK5SW8"><img src="https://www.paypalobjects.com/en_US/i/btn/btn_donate_SM.gif" alt="Donate to author" style="max-width:100%;"></a></p></center>

<center>![Briefcase of cash](http://i.imgur.com/LTdty.png)
<p><a href="https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&amp;hosted_button_id=Q3KSCBZP6TL2N"><img src="https://www.paypalobjects.com/en_US/i/btn/btn_donate_SM.gif" alt="Donate to author" style="max-width:100%;"></a></p></center>

Or, you can contribute code.  I am the first to concede the liber80211 codebase houses a 10 year supply of [Daily WTF](http://thedailywtf.com/) material, and I encourage you to help fix this.

## I want to hack on the code, where should I start?
If you're just interested in dissecting what is already being heard over the air in monitor mode, check out the `RxThread.java` file.  This is where the raw bytes come in and where you can add your own code to dissect frame types other than the few that I already partially dissect (Probe Request and Beacon).  Keep a copy of the 802.11 spec handy.  

If you feel like being awesome and improving the driver (adding support for packet injection maybe? hint, hint), take a look at `USBIO.java`.  This is where all the I/O over the wire between the phone and Wi-Fi card takes place.  Many of the method names in here correspond to those found in the Linux driver source (`/drivers/net/wireless/rtl818x/rtl8187/dev.c` in the Linux kernel source is a good starting place).  The stuff in `USBIO.java` is mostly just called from `MainActivity.java` right now, to initialize the EEPROM etc.

## Tell me more about the Ghetto that is USB OTG on Android
Well, firstly, USB OTG just doesn't work altogether on some Android phones, even when they're running Android >= 3.0 and come from a flagship line.  Take for example my Nexus S, running Android 4.1.  It won't even recognize a USB mouse receiver.  It's fun trying to guess whether you've just got a faulty USB OTG cable or your phone doesn't actually support it.

I actually purchased a Galaxy Nexus because at the time it was the only phone I was confident would work.  And work it did, for over a month.  Then one day it just suddenly stopped recognizing my ALFA AWUS036H for no apparent reason.  After fretting that I might have broken my Galaxy Nexus somehow, I finally tried connecting the ALFA AWUS036H via a powered hub, and it worked again.  To this day I still don't know precisely what happened.  Whether the battery in my Galaxy Nexus just reached a point where it decided it wasn't interested in driving the ALFA AWUS036H or what.  Either way, it no longer works without an external power source for me on my Galaxy Nexus (I now use a [USB OTG Y-cable](http://www.ebay.com/itm/Micro-USB-Host-OTG-Cable-USB-power-Samsung-S2-i9100-S3-i9300-i9220-i9250-/190710044655?_trksid=p2045573.m2042&_trkparms=aid%3D111000%26algo%3DREC.CURRENT%26ao%3D1%26asc%3D27%26meid%3D2956906707497516260%26pid%3D100033%26prg%3D1011%26rk%3D2%26sd%3D181000169041%26) with a USB battery pack instead of a powered hub).  

Another problem with USB OTG support on Android at the moment is that charging the phone and powering the accessory from an external power source at the same time seems to be a black art, if not impossible.  While the USB OTG spec seems to support it via Accessory Charger Adapter (ACA) mode, it seems to be very poorly/inconsistently implemented across handsets.  I haven't been able to get it to work on my Galaxy Nexus.

For the sake of completeness, I should also mention that my Galaxy Nexus now often likes to tell me that it's charging/wake itself up to do so, even when it's not plugged in at the wall.  A neat party trick to pretend your phone is wirelessly charging certainly, but a drain on the battery.  Even worse is when I actually *do* plug it into a wall socket and it lies about charging, and I find out the hard way the next day with a flat battery.  While I don't think this problem is directly related to my USB OTG work, *YOU SHOULD EXERCISE CAUTION AND UNDERSTAND THAT THIS IS EXPERIMENTAL SOFTWARE AND THINGS MAY BREAK.*.  I've found removing the battery for a few seconds corrects any issues if it gets into one of these abnormal states.  