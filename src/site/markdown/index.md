
## A simple tool to download files from a Humax HDR-FOX T2

A few years ago, my husband and I bought a Humax HDR FOX T2 PVR. Its a very
nice device, but its only got a 1TB hard disk. Since format shifting is legal
in the UK (I think, I'm not an expert). I wrote some software to make it
easier to copy videos off the PVR.

## Features

  * Browse files stored on your PVR from your desktop
  * Download standard definition videos
  * Queue downloads
  * Reorder download queue
  * Remember queued downloads when shutdown      
  * Pause and resume downloads
  * "Unlock" encrypted high definition videos (but see the Warning below)

## Using the software

Download the latest version of the software (from github) and run it. You will
need a copy of [Java](https://java.com/) installed, at least version 1.7. 

There is an executable jar file, that should run anywhere with Java, and also
a Windows exe file (which is the same software, but [with a wrapper](https://launch4j.sf.net) that 
offers to install Java if it's not already installed).

The software is standalone and doesn't need to be installed.

Run it, and you'll get a window with a vertical split. Assuming that
your PVR is switched on, and connected to the same network as your computer,
the left hand side will fill with a map of the contents of the PVR.

Choose a download directory. When files are queued, they'll remember the 
currently chosen download directory, so you can set a download directory,
queue up a series, set another directory, queue up another series, and so on.

Browse around the files on the PVR, and drag files you want to download over to
the right hand side. Due to the limitations of the PVR, you can only download 
standard definition files this way. (The software won't let you queue up high
definition videos).

Once you've built up your download queue, click the "Start downloading" button,
and wait for the downloads to finish. Click "Stop downloading" to pause, and
drag files up and down the queue to re-order.

You can right click on high definition files in the left hand panel and unlock
them, and then use the PVR's interface to copy to a USB drive. But read the
warning below first.

## How it works

The PVR records standard and high definition broadcast TV from (in the UK) 
Freeview. The video files can be accessed in three ways (ignoring watching
them through the TV). 

First, the PVR acts as a DLNA media server. Second, you can copy files onto
a USB stick, and third, the PVR has a built in FTP server.

Downloading standard definition videos is easy. Just connect to the DLNA
service and "stream" video to a file on another computer. Exporting high
definition videos is a little harder, as the PVR encrypts the video files.

You can copy the encrypted videos through FTP or USB, and copy them back
to the PVR and play them fine (I'm told, I've never bothered trying). But
that's no help if I want to watch the video on my laptop.

Luckily, other people have already done the research, and it is possible to
use the FTP server to "unlock" high definition video. The video files still
need to be copied off via USB (which can take a *long* time), but it does
allow the user to store more than 1TB worth of high definition video.

## Warning

Downloading standard definition video via DLNA is (almost certainly) safe. 

Unlocking high definition video uses undocumented and reverse engineered information
that, occasional, corrupts recordings.

I've lost at least one movie using this program. The more you use it, the higher
the chance that you'll loose something too.

It is possible that using this software is illegal where you are. (It is possible
that *writing* this software was illegal). If you are unsure of your local laws 
regarding removal of copy protection, please consult an expert.

## Contributing

The source code for the parts of the software I wrote is 
[available on Github](https://github.com/moosemorals/media-browser), 
licensed under a very permissive MIT licence. Contributions are welcome,
from bug reports up to full-on re-writes.

## Copyright

This site and the software is Copyright (c) 2016 Osric Wilkinson [osric@fluffypeople.com](mailto:osric@fluffypeople.com).

The software is distributed under an MIT licence, except that it includes bundled
[libraries](dependencies.html#compile) that have their own licences.