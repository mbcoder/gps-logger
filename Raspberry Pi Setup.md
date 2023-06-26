# Setup instructions for a new Raspberry Pi

You will first need to make an SD card image for the operating system using the Raspberry Pi Imager downloaded from the Raspberry Pi [website](https://www.raspberrypi.com/software/).

After you've opened the Imager app, click on “CHOOSE OS” and from the menu choose the 'Raspberry Pi OS (64bit)'.   Note that the ArcGIS Maps SDK for Java libraries will not work with 32bit operating systems.

Once the Imaging process is complete, put the SD card in the Raspberry Pi and follow the setup instructions where you will set a username, password and connect to a network if available.

Once the Raspberry Pi is up and running, then open up the Terminal and follow the steps below:

- Perform a system upgrade with `sudo apt-get update`, then`sudo apt full-upgrade`

 - Install Java with `sudo apt install openjdk-17-jdk -y`

 - Install maven with `sudo apt install maven -y`

 - Obtain the early adopter zip distribution of the ArcGIS Maps SDK for Java.  
 - Unzip the file and run the script `sudo ./install-local.sh`


Note that the `install-local.sh` script is run under `sudo` as the Pi4J libraries need to also run an app with `sudo` level access.

Optionally for remote access to the Raspberry Pi, you may want to configure VNC access to the machine.
