# Tree
  * Make info pane descriptions follow focus
  * add queue and download next right click menu item

# Download list
  * add clear queue  
  
# File management

  So I've checked out, and renaming files looks pretty easy. I should also
be able to do things like create, rename and delete directories (although
I may need to empty the directory first). 

## Issues:
  * User interface. 
    Drag and drop will work fine for move, add a "new folder" option to
    right click (although working out which parent folder might be 
    a little tricky). Probably add a 'rename' option to right click
    and I want to think about a 'bulk rename' option for multi-select.

  * Lag between updates
    FTP updates right away, but the DLNA stuff doesn't, and there's no
    real way to tell, other than polling.

    It may be worth changing the way I'm thinking about this stuff. Use
    DLNA to get the IP address, scan with FTP to get all the files, and
    only then do the DLNA scan to find download paths for things that
    can be downloaded.

    I think I'm also going to want to keep track of what files have been
    moved so that I can spot 'ghost' copies - files that are in one place
    on FTP and another place with DLNA. 

    Time for a database?

  * Testing
    I don't want to screw this one up. If I can make it a full on 
    

* Check for files that have been downloaded

