package com.mbcoder.iot.gpslogger;

import com.esri.arcgisruntime.location.NmeaLocationDataSource;
import com.pi4j.io.serial.Serial;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class SerialReader implements Runnable {

  //private final Console console;
  private final Serial serial;
  private NmeaLocationDataSource nmeaLocationDataSource;

  private boolean continueReading = true;

  public SerialReader(Serial serial, NmeaLocationDataSource nmeaLocationDataSource) {
    this.serial = serial;
    this.nmeaLocationDataSource = nmeaLocationDataSource;
  }

  public void stopReading() {
    continueReading = false;
  }

  @Override
  public void run() {
    // We use a buffered reader to handle the data received from the serial port
    BufferedReader br = new BufferedReader(new InputStreamReader(serial.getInputStream()));

    try {
      // Data from the GPS is recieved in lines
      String line = "";

      // Read data until the flag is false
      while (continueReading) {
        // First we need to check if there is data available to read.
        // The read() command for pigio-serial is a NON-BLOCKING call,
        // in contrast to typical java input streams.
        var available = serial.available();

        if (available > 0) {
          //byte b = (byte) br.read();

          //byte[] ba = new byte[1];
          //ba[0] = b;
          //nmeaLocationDataSource.pushData(ba);



          for (int i = 0; i < available; i++) {
            byte b = (byte) br.read();
            if (b < 32) {
              // All non-string bytes are handled as line breaks
              if (!line.isEmpty()) {
                // Here we should add code to parse the data to a GPS data object
                //System.out.println("Data: '" + line + "'");
                nmeaLocationDataSource.pushData(line.getBytes());
                line = "";
              }
            } else {
              line += (char) b;
            }


          }


        } else {
          Thread.sleep(10);
        }
      }
    } catch (Exception e) {
      System.out.println("Error reading data from serial: " + e.getMessage());
      System.out.println(e.getStackTrace());
    }
  }
}

