# SMART LAMP
### Smart lamp to wakeup with sunrise
##### SMD version

The project is a LED strip control board to generate a smart wakeup light, which 
simulates the sunrise light.
On board there is a real time clock, to  keep track of the actual date and time 
even on power loss, and some MOSFET to drive the LEDs with PWM.
On a separate breakout board I'm using a BLE chipset to connect with an Android 
application, to setup date, time, alarms and LED light.

Everything is soldered on the same PCB. The micro is an Atmel ATMega328P.

The project is compiled for Atmel AVR, with standard AVR GCC toolchain. 
Eclipse was used to create and compile the projects, with the AVR Eclipse Plugin.

This project consists of a main C++ file and some libraries:

* **DS3232RTC** : real time clock library, to read the actual date and time. The library 
  have been modified to work with the an external instance of the I2C bus control 
  class, and each functionality has been tested with the hardware;
* **TimeZone** : Arduino library to account for DST (summer time) with the RTC clock
  (reference <https://github.com/gion86/Timezone>, version with time.h from avr-libc);
* **Wire** : I2C library for Atmel micros. The library includes the correct source file for 
  the hardware used (reference <https://github.com/gion86/Wire>); 
  In the case of the Mega micro the I2C hardware module is used with the TwoWire 
  library (reference <https://github.com/esp8266/Arduino/tree/master/libraries/Wire>);
* **ATMega core**  (reference <https://github.com/gion86/ArduinoCore> and 
  <https://github.com/arduino/Arduino/tree/master/hardware/arduino/avr/cores/arduino>)


The project folder contains:

* Android application for Bluetooth Low Energy communication;
* the bill of material;
* datasheet of used components;
* electrical schematic and board (made with Eagle);

Some pictures can be found at https://photos.app.goo.gl/nYQq9dkLF3Ui9xUt2
