# SMART LAMP
### Smart lamp to wakeup with sunrise
##### Through hole version

The project is a LED strip control board to generate a smart wakeup light, which 
simulates the sunrise light.
On board there is a real time clock, to  keep track of the actual date and time 
even on power loss, and some MOSFET to drive the LEDs with PWM.
On a separate breakout board I'm using a BLE chipset to connect with an Android 
application, to setup date, time, alarms and LED light.

Everything is soldered on the same PCB. The micro is an Atmel ATTiny84.

The project is compiled for Atmel AVR, with standard AVR GCC toolchain. 
Eclipse was used to create and compile the projects, with the AVR Eclipse Plugin.

This project consists of a main C++ file and some libraries:

* **DS3232RTC** : real time clock library, to read the actual date and time. The sensors 
  library have been modified to work with the an external instance of the I2C bus 
  control class, and each functionality has been tested with the hardware. 
* **TimeZone** : Arduino library to account for DST (summer time) with the RTC clock. 
  The timezone rules are saved into an EEPROM image (to save program memory on the 
  ATTiny84): the image is in Intel HEX format.
  (reference <https://github.com/gion86/Timezone>, version with time.h from avr-libc).
* **USIWire** : Wire library for the small ATTiny, were the I2C management 
  hardware is not present. The library uses the internal USI hardware to communicate 
  over serial connection, and implement I2C protocol (reference <https://github.com/puuu/USIWire>).
* **ATTiny core**  (reference <https://github.com/gion86/ATTinyCore/tree/newduino> and 
  <https://github.com/SpenceKonde/ATTinyCore>)


The project folder contains:

* the bill of material;
* datasheet of used components;
* electrical schematic and board (made with Eagle);

Some pictures can be found at https://photos.app.goo.gl/nYQq9dkLF3Ui9xUt2
