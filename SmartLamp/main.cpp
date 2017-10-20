/*
 *  This file is part of SmartLamp application.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

/*
 * Built for Attiny84 8Mhz, using AVR USBasp programmer.
 * VERSION 0.2
 */

#include <avr/io.h>
#include <avr/sleep.h>
#include <time.h>

#include <Arduino.h>

#include <USIWire.h>
#include <DS3232RTC.h>

// Input/output defines
#define LED_RED             2   // RGB strip: red pin
#define LED_GR              3   // RGB strip: green pin
#define LED_BLUE            5   // RGB strip: blue pin

#define BLU_STATE           1   // Bluetooth module state pin
#define BLU_RESET           0   // Bluetooth module reset pin

#define RTC_INT_SQW        10   // INT/SQW pin from RTC

// I2C defines
#define SDA                 4   // SDA pin
#define SCL                 6   // SCL pin

// Serial defines
#define SERIAL_BAUD      9600   // For at mode and for data mode (CC41, HM-10 and MLT-BT05)

#define SLEEP_TIMEOUT   5000L   // Timeout before sleep
#define LENGTH             80   // Command buffer length


// Global variables
USIWire bus;                    // USIWire instance (I2C bus)
DS3232RTC RTC(bus);             // DS3232 RTC instance (I2C bus)

boolean data = false;
unsigned long prevMillis = 0;   // Millis counter to sleep
bool rtcInitOk = false;         // Communication OK with RTC

// Put the micro to sleep
void system_sleep() {
  set_sleep_mode(SLEEP_MODE_PWR_DOWN);
  sleep_enable();
  sleep_mode();

  // sleeping ...
  sleep_disable(); // wake up fully
}

/*
 * Converts the date/time to standard Unix epoch format, using time.h library (avr-libc)
 *
 * Param:
 * - int16_t YYYY: year (Gregorian format: ex. 2017)
 * - int8_t MM: month
 * - int8_t DD: day of the month
 * - int8_t hh: hour
 * - int8_t mm: minute
 * - int8_t ss: second
 */
time_t tmConvert_t(int16_t YYYY, int8_t MM, int8_t DD, int8_t hh, int8_t mm, int8_t ss) {
  struct tm tm;
  tm.tm_year = YYYY - 1900 + 30;    // avr-libc time.h: years since 1900 + y2k epoch difference (2000 - 1970)
  tm.tm_mon  = MM - 1;              // avr-libc time.h: months in [0, 11]
  tm.tm_mday = DD;
  tm.tm_hour = hh;
  tm.tm_min  = mm;
  tm.tm_sec  = ss;
  return mk_gmtime(&tm);
}

void printDigits(int digits) {
  Serial.print(':');
  if (digits < 10)
    Serial.print('0');
  Serial.print(digits);
}

/*
 * Prints the time in time_t, using the standard Unix epoch format, even if the time_t type is from
 * time.h (avr-libc) and is in Y2K epoch format.
 *
 * Param:
 *  - time_t time: time since Unix epoch
 */
void digitalClockDisplay(time_t time) {
  struct tm tm;

  gmtime_r(&time, &tm);

  // Digital clock display of the time
  Serial.print(tm.tm_hour);
  printDigits(tm.tm_min);
  printDigits(tm.tm_sec);
  Serial.print(' ');
  Serial.print(tm.tm_mday);
  Serial.print('/');
  Serial.print(tm.tm_mon + 1);              // avr-libc time.h: months in [0, 11]
  Serial.print('/');
  Serial.println(tm.tm_year + 1900 - 30);   // avr-libc time.h: years since 1900 + y2k epoch difference (2000 - 1970)
}

// PCINT Interrupt Service Routine (unused)
ISR(PCINT0_vect) {
  // Don't do anything here but we must include this
  // block of code otherwise the interrupt calls an
  // uninitialized interrupt handler.
}

void setup() {
  byte retcode;
  time_t ts;

  OSCCAL = 0x86;                    // Calibrated OSSCAL value with TinyTuner

  Serial.begin(SERIAL_BAUD);
  bus.begin();

  // Pinmode set
  pinMode(LED_BLUE, OUTPUT);
  pinMode(LED_GR, OUTPUT);
  pinMode(LED_RED, OUTPUT);
  pinMode(BLU_STATE, INPUT);
  pinMode(BLU_RESET, OUTPUT);
  pinMode(RTC_INT_SQW, INPUT);

  Serial.print(F("Initial value of OSCCAL is 0x"));
  Serial.println(OSCCAL, HEX);

  ts = tmConvert_t(2017, 10, 20, 23, 05, 00);

  if ((retcode = RTC.set(ts)) == 0)
    rtcInitOk = true;
  else {
    Serial.print(F("RTC Set error: "));
    Serial.print(retcode);
  }

  Serial.print(F("TS: "));
  Serial.println(ts);
  digitalClockDisplay(ts);

  Serial.print(F("RTC set to: "));
  Serial.println(RTC.get());
  digitalClockDisplay(RTC.get());

  ADCSRA  = 0;                      // Disable ADC to save power
  MCUCR  |= _BV(BODS);              // BOD disabled

  PCMSK0 |= _BV(PCINT0);            // Pin change mask: listen to portA bit 0 (D10) (RTC_INT_SQW)
  PCMSK0 |= _BV(PCINT2);            // Pin change mask: listen to portA bit 2  (D8) (Serial RX - AIN1)
  GIMSK  |= _BV(PCIE0);             // Enable PCINT interrupt on portA
}

void loop() {
  size_t count = 0;
  char buffer[LENGTH];

  // FIXME Wake from sleep with new CORE can't read first serial bytes....

  if (millis() - prevMillis >= SLEEP_TIMEOUT) {
    prevMillis = millis();
    Serial.println(F("Sleeping..."));
    system_sleep();
    Serial.println(F("Waking up..."));
    digitalClockDisplay(RTC.get());

    // Necessary to reset the alarm flag on RTC!
    if (RTC.alarm(ALARM_1)) {
      Serial.println(F("From alarm..."));
    }
  }

  while (Serial.available() && count < LENGTH - 1) {
    delay(2);
    char c = (char) Serial.read();

    prevMillis = millis();      // Update prevMillis to reset sleep timeout

    if (c == '\r' || c == '\n') {
      if (c == '\n') {
        data = true;
        Serial.flush();
        break;
      }
      continue;
    }

    buffer[count] = c;
    count++;
  }

  if (data) {
    buffer[count] = '\0';
    //Serial.print("COUNT = ");
    //Serial.println(count);
    Serial.println(buffer);

    count = 0;
    data = false;

    if (strcmp(buffer, "ON") == 0) {
      digitalWrite(LED_BLUE, HIGH);

      RTC.setAlarm(ALM1_MATCH_MINUTES, 00, 10, 23, 0);
      RTC.alarmInterrupt(ALARM_1, true);

//      for(int fadeValue = 0 ; fadeValue <= 255; fadeValue +=5) {
//          analogWrite(LED_BLUE, fadeValue);
//          delay(100);
//        }
//      delay(1000);
//      digitalWrite(LED_BLUE, LOW);
//
//      for(int fadeValue = 0 ; fadeValue <= 255; fadeValue +=5) {
//        analogWrite(LED_RED, fadeValue);
//        delay(100);
//      }
//      delay(1000);
//      digitalWrite(LED_RED, LOW);
//
//      for(int fadeValue = 0 ; fadeValue <= 255; fadeValue +=5) {
//        analogWrite(LED_GR, fadeValue);
//        delay(100);
//      }
//      delay(1000);
//      digitalWrite(LED_GR, LOW);
    }

    if (strcmp(buffer, "OFF") == 0) {
      digitalWrite(LED_BLUE, LOW);
      digitalWrite(LED_RED, LOW);
      digitalWrite(LED_GR, LOW);
    }

    digitalClockDisplay(RTC.get());
  }
}
