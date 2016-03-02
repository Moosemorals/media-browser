
# MediaBrowser

## A simple tool to download files from a Humax HDR-FOX T2

I've got a Humax HDR FOX T2 PVR, and I'd like to be able to 
easily download programs from it, since its only (only!) got
1TB of space.

There are three ways to get files off the T2. It has an FTP
server, it is a DLNA/Upnp server, and you can copy from 
a USB stick.

None of these three ways is satisfactory on its own, due to 
weird copy protection rules. However, other people have done
the research to fix most of these problems.

The device can record in standard and high definition. Standard
def files are easy, just connect to the DLNA server and download
them. High Def is harder, you need to connect to the FTP server
download a file, flip a couple of bits and re-upload the file.
This marks the file as exportable, and you can then copy it onto
an external USB drive.

This tool automates a bunch of the process. It provides a view
of the files availible on the device, and you can queue and download
standard definition files directly. It will also flip the appropriate
bits to allow dowloading of HD files to USB.

Currently working:

  * Standard definition downloading

In progress

  * High definition bit flipping.

## Dependencies

Upnp supoprt is provided by the [Cling](http://4thline.org/projects/cling) 
library.

FTP support is provided by the [Apache Commons net](https://commons.apache.org/proper/commons-net/)
library.

HTTP support is provided by the [Apache Commons HTTP](https://hc.apache.org/) library.


