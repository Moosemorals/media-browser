Tree
  * Make info pane descriptions follow focus
  * add queue and download next right click menu item

Download list
  * add clear queue  
  
Better error handling on downloads

If, when downloading a file that is not first in the queue, that file is moved
then that file doesn't resume downloading. Fix this.

Check what happens when you set download folder for a file to a folder
where partial already exists.
