
# MediaBrowser

# WARNING!

Flipping bits in an undocumented, proprietary, and mostly hidden file is 
intrinsically unsafe. This tool comes with **NO WARRANTY** and may cause
damage including (but not limited to) making a file unreadable, locking your
PVR, bricking your PVR, setting your PVR on fire, or turning your PVR into an
artificial intelligence bent on world destruction. *And it would be **your** fault!*

**Use this tool at your own risk.**


## A simple tool to download files from a Humax HDR-FOX T2

I've got a Humax HDR FOX T2 PVR, and I'd like to be able to 
easily download programs from it, since its only (only!) got
1TB of space.

There are three ways to get files off the T2. It has an FTP
server, it is a Upnp server, and you can copy to a USB stick.

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
of the files available on the device, and you can queue and download
standard definition files directly. It will also flip the appropriate
bits to allow downloading of HD files to USB.

## Instalation

At the moment, its clone the repo, build with maven and then run. My 
husband's PC runs Windows, and he doesn't quite understand jar files,
so I use [Launch4j](http://launch4j.sourceforge.net/) to hide the
jar in an exe. 

I'm looking into Windows Installers, updates may follow.

## Use

Run the jar or the exe, and a window will open. The program will poke
around your local network to find your Humax. Assuming it can find one,
it will connect to the Upnp service and the FTP service (using the default
'0000' password) and scan for files.

When the list of files has been built, you can right click on a file to queue
it, or to remove the copy protection flag.


## Dependencies

Upnp supoprt is provided by the [Cling](http://4thline.org/projects/cling) 
library.

FTP support is provided by the [Apache Commons net](https://commons.apache.org/proper/commons-net/)
library.

I'm using [Joda Time](http://www.joda.org/joda-time/) for some date related 
stuff

## Licence

My code is released under the [MIT licence](LICENCE.txt). The various libraries 
[have their own licences](http://moosemorals.github.io/media-browser/dependencies.html#Licenses).