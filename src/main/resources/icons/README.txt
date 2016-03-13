
To build the application icon, you'll need imagemagick installed.

Run

  convert -alpha on -background transparent -verbose PVR\ Icon\ Blue\ 256x256.png -define icon:auto-resize=64,48,32,20,16 app.ico

