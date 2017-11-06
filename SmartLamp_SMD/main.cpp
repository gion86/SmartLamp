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
 * Built for ATMega328P 8Mhz, using AVR USBasp programmer.
 * VERSION 0.1
 */

#include <avr/io.h>
#include <avr/sleep.h>
#include <avr/pgmspace.h>
#include <avr/interrupt.h>
#include <time.h>

#include <Arduino.h>

#include <Wire.h>               // Wire I2C library
#include <DS3232RTC.h>          // RTC library
#include <Timezone.h>           // Timezone library

// Input/output pin defines
#define LED_RED             9   // RGB strip: red pin
#define LED_GR             10   // RGB strip: green pin
#define LED_BLUE           11   // RGB strip: blue pin

#define BLU_STATE           2   // BLE module state pin
#define BLU_RESET           3   // BLE module reset pin

#define RTC_INT_SQW         4   // INT/SQW pin from RTC

#define BLU_LED             6   // BLE module connection LED

// Serial defines
#define SERIAL_BAUD      9600   // For at mode and for data mode (CC41, HM-10 and MLT-BT05)

// Sleep defines
#define SLEEP_TIMEOUT   5000L   // Timeout before sleep

#define CMD_LENGTH         80   // Command buffer length

#define RULES_TZ_ADD        0   // Timezone rules EEPROM start address

// LED defines
#define PERIOD          5000L   // LED crossfade total duration
#define MAX_ANALOG_V      255   // Analog output max value for RGB

// States
#define STEP_SLEEP         0
#define STEP_READ_CMD      10
#define STEP_FADE          15

// Global constants
// CET Time Zone (Rome, Berlin) -> UTC/GMT + 1
// These rules are written in the EEPROM, at address RULES_TZ_ADD. The EEPROM by default gets erased by the
// erase procedure on upload, which clears FLASH, EEPROM and lock bits. To preserve the EEPROM memory fuse
// EESAVE has to be set. In this case EEPROM will not be erased during erase procedure.
// But the fuse has to be cleared if you want to clear and/or reflash EEPROM.
const TimeChangeRule CEST = {"CEST", Last, Sun, Mar, 2, 120};     // Central European Summer Time (DST)
const TimeChangeRule CET  = {"CET ", Last, Sun, Oct, 3, 60};      // Central European Standard Time

// Global variables
TwoWire bus;                    // USIWire instance (I2C bus)
DS3232RTC RTC(bus);             // DS3232 RTC instance (I2C bus)

size_t count = 0;
bool data = false;
unsigned long prevMillis = 0;   // Millis counter to sleep
bool rtcInitOk = false;         // Communication OK with RTC

//Timezone myTZ(RULES_TZ_ADD);    // Constructor to read rules stored at EEPROM address RULES_TZ_ADD
Timezone myTZ(CET, CEST);     // Constructor to build object with TimeChangeRule

TimeChangeRule *tcr;            // Pointer to the time change rule, use to get TZ abbreviations

uint8_t step = STEP_READ_CMD;

// ########################################################
// Time manipulation functions
// ########################################################

/*
 * Converts the date/time to standard Unix epoch format, using time.h library (avr-libc)
 *
 * Param:
 * - int16_t YYYY: year (given as ex. 2017)
 * - int8_t MM: month [1, 12]
 * - int8_t DD: day of the month [1, 31]
 * - int8_t hh: hour [0, 23]
 * - int8_t mm: minute [0, 59]
 * - int8_t ss: second [0, 59]
 */
static time_t tmConvert_t(int16_t YYYY, int8_t MM, int8_t DD, int8_t hh, int8_t mm, int8_t ss) {
  struct tm tm;
  tm.tm_year = YYYY - 1900 + 30;    // avr-libc time.h: years since 1900 + y2k epoch difference (2000 - 1970)
  tm.tm_mon  = MM - 1;              // avr-libc time.h: months in [0, 11]
  tm.tm_mday = DD;
  tm.tm_hour = hh;
  tm.tm_min  = mm;
  tm.tm_sec  = ss;
  return mk_gmtime(&tm);
}

/*
 * Write to serial a digit with zero padding if needed.
 *
 * Param:
 * - int digits
 */
static void printDigits(int digits) {
  Serial.print(':');
  if (digits < 10)
    Serial.print('0');
  Serial.print(digits);
}

/*
 * Writes to Serial the clock (date and time) in human readable format.
 *
 * Param:
 * - time_t time: time from the time.h (avr-libc)
 * - const char *tz: string for the timezone, if not used set to NULL
 */
static void digitalClockDisplay(time_t time, const char *tz = NULL) {
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
  Serial.print(tm.tm_year + 1900 - 30);     // avr-libc time.h: years since 1900 + y2k epoch difference (2000 - 1970)

  if (tz != NULL) {
    Serial.print(' ');
    Serial.print(tz);
  }

  Serial.println();
}

// ########################################################
// Command parsing functions (from BLE Serial)
// ########################################################

/*
 * Returns the integer representation of the char data
 *
 * Param:
 * - char data: a ASCII char 0-9
 */
static inline int8_t atod(char data) {
  return data - '0';
}


static bool parseCommand(char *buffer) {
  // TODO data checks....

  // Set date and time
  char *s = strstr(buffer, "ST_");

  if (s != NULL && strlen(buffer) == 18) {
    int16_t YYYY;
    int8_t MM, DD, hh, mm, ss;

    YYYY = atod(buffer[7]) * 1000 + atod(buffer[8]) * 100 + atod(buffer[9]) * 10 + atod(buffer[10]);
    MM   = atod(buffer[5]) * 10 + atod(buffer[6]);
    DD   = atod(buffer[3]) * 10 + atod(buffer[4]);
    hh   = atod(buffer[12]) * 10 + atod(buffer[13]);
    mm   = atod(buffer[14]) * 10 + atod(buffer[15]);
    ss   = atod(buffer[16]) * 10 + atod(buffer[17]);

    // RTC.set()

    Serial.print("YYYY = ");
    Serial.println(YYYY);

    Serial.print("MM = ");
    Serial.println(MM);

    Serial.print("DD = ");
    Serial.println(DD);

    Serial.print("hh = ");
    Serial.println(hh);

    Serial.print("mm = ");
    Serial.println(mm);

    Serial.print("ss = ");
    Serial.println(ss);

    return true;
  }

  // Set alarm on weekday
  s = strstr(buffer, "AL_");

  if (s != NULL && strlen(buffer) == 10) {
    int8_t WD, hh, mm;

    WD   = atod(buffer[3]) * 10 + atod(buffer[4]);
    hh   = atod(buffer[6]) * 10 + atod(buffer[7]);
    mm   = atod(buffer[8]) * 10 + atod(buffer[9]);

    // RTC.setAlarm()

    Serial.print("WD = ");
    Serial.println(WD);

    Serial.print("hh = ");
    Serial.println(hh);

    Serial.print("mm = ");
    Serial.println(mm);

    return true;
  }

  // Disable alarm on weekday
  s = strstr(buffer, "AL_DIS_");

  if (s != NULL && strlen(buffer) == 9) {
    uint8_t WD;

    WD = atod(buffer[7]) * 10 + atod(buffer[8]);

    Serial.print("WD = ");
    Serial.println(WD);

    return true;
  }

  // Set lamp fade time before or after the alarm
  s = strstr(buffer, "FT_");

  if (s != NULL && strlen(buffer) == 9) {
    uint16_t ss;

    ss = atod(buffer[5]) * 1000 + atod(buffer[6]) * 100 + atod(buffer[7]) * 10 + atod(buffer[8]);

    if (buffer[3] == 'A') {
      // after
    } else {
      // before
    }

    Serial.print("ss = ");
    Serial.println(ss);

    return true;
  }

  // Set RGB color for the lamp LED
  s = strstr(buffer, "RGB_");

  if (s != NULL && strlen(buffer) == 15) {
    uint8_t red, gr, blue;

    red  = atod(buffer[4]) * 100 + atod(buffer[5]) * 10 + atod(buffer[6]);
    gr   = atod(buffer[8]) * 100 + atod(buffer[9]) * 10 + atod(buffer[10]);
    blue = atod(buffer[12]) * 100 + atod(buffer[13]) * 10 + atod(buffer[14]);

    Serial.print("red = ");
    Serial.println(red);

    Serial.print("gr = ");
    Serial.println(gr);

    Serial.print("blue = ");
    Serial.println(blue);

    return true;
  }

  // Error: unrecognized command!!
  return false;
}

// ########################################################
// AVR specific functions
// ########################################################

/*
 * Put the micro to sleep
 */
static void system_sleep() {
  set_sleep_mode(SLEEP_MODE_PWR_DOWN);
  sleep_enable();
  sleep_mode();

  // sleeping ...
  sleep_disable(); // wake up fully
}

/*
 *  PCINT Interrupt Service Routine (unused)
 */
ISR(PCINT2_vect) {
  // Don't do anything here but we must include this
  // block of code otherwise the interrupt calls an
  // uninitialized interrupt handler.
}

/*
 * Set various power reduction options
 */
static void powerReduction() {
    // Disable digital input buffer on ADC pins
    DIDR0 = (1 << ADC5D) | (1 << ADC4D) | (1 << ADC3D) | (1 << ADC2D) | (1 << ADC1D) | (1 << ADC0D);

    // Disable digital input buffer on Analog comparator pins
    DIDR1 |= (1 << AIN1D) | (1 << AIN0D);

    // Disable Analog Comparator interrupt
    ACSR &= ~(1 << ACIE);

    // Disable Analog Comparator
    ACSR |= (1 << ACD);

    // Disable unused peripherals to save power
    // Disable ADC (ADC must be disabled before shutdown)
    ADCSRA &= ~(1 << ADEN);

    // Enable power reduction register except:
    // - USART0: for serial communications
    // - TWI module: for I2C communications
    // - TIMER0: for millis()
    PRR = 0xFF & (~(1 << PRUSART0)) & (~(1 << PRTWI)) & (~(1 << PRTIM0));
}


// ========================================================
// |                        SETUP                         |
// ========================================================

void setup() {
  uint8_t retcode;

  Serial.begin(SERIAL_BAUD);
  bus.begin();

  // Pinmode set
  pinMode(LED_BLUE, OUTPUT);
  pinMode(LED_GR, OUTPUT);
  pinMode(LED_RED, OUTPUT);
  pinMode(BLU_STATE, INPUT);
  pinMode(BLU_RESET, OUTPUT);
  pinMode(RTC_INT_SQW, INPUT);
  pinMode(BLU_LED, OUTPUT);

  time_t t;
  t = tmConvert_t(2017, 11, 01, 11, 00, 00);

  if ((retcode = RTC.set(t)) == 0)
    rtcInitOk = true;
  else {
    Serial.print(F("RTC err: "));
    Serial.println(retcode);
  }

  time_t utc = RTC.get();
  Serial.print(F("UTC: "));
  digitalClockDisplay(utc, "UTC");

  time_t local = myTZ.toLocal(utc, &tcr);
  Serial.print(F("Local: "));
  digitalClockDisplay(local, tcr->abbrev);

  // Power settings
  powerReduction();

  // Interrupt configuration
  PCMSK2 |= _BV(PCINT20);           // Pin change mask: listen to portD bit 4 (D4) (RTC_INT_SQW)
  PCMSK2 |= _BV(PCINT16);           // Pin change mask: listen to portD bit 0 (D0) (Serial RX)
  PCICR  |= _BV(PCIE2);             // Enable PCINT interrupt on portD

  prevMillis = millis();
}

// ========================================================
// |                        LOOP                          |
// ========================================================

void loop() {
  char buffer[CMD_LENGTH];
  uint8_t led_value;
  double x = 0;

  switch (step) {
    case STEP_SLEEP:
      // Sleep state

      Serial.println(F("Sleeping..."));
      delay(50);
      system_sleep();
      Serial.println(F("Waking up..."));
      digitalClockDisplay(RTC.get(), "UTC");

      // Necessary to reset the alarm flag on RTC!
      if (RTC.alarm(ALARM_1)) {
        Serial.println(F("From alarm..."));
      }

      prevMillis = millis();
      step = STEP_READ_CMD;

      break;

    case STEP_READ_CMD:
      // Read command state (from serial connection to bluetooth module)

      while (Serial.available() && count < CMD_LENGTH - 1) {
        char c = (char) Serial.read();

        prevMillis = millis();      // Update prevMillis to reset sleep timeout

        if (c == '\r' || c == '\n') {
          if (c == '\n') {
            data = true;
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

        // Test
        //strcpy(buffer, "ST_05112017_141607");
        //strcpy(buffer, "AL_05_1418");
        //strcpy(buffer, "RGB_055_129_255");
        //strcpy(buffer, "AL_DIS_07");
        //strcpy(buffer, "FT_A_1400");
        Serial.println(buffer);

        parseCommand(buffer);

        if (strcmp(buffer, "ON") == 0) {
          RTC.setAlarm(ALM1_MATCH_MINUTES, 00, 01, 11, 0);
          RTC.alarmInterrupt(ALARM_1, true);
          step = STEP_FADE;
        } else if (strcmp(buffer, "OFF") == 0) {
          analogWrite(LED_BLUE, 0);
          analogWrite(LED_RED, 0);
          analogWrite(LED_GR, 0);
          step = STEP_SLEEP;
        }

        time_t utc = RTC.get();
        Serial.print(F("UTC: "));
        digitalClockDisplay(utc, "UTC");

        time_t local = myTZ.toLocal(utc, &tcr);
        Serial.print(F("Local: "));
        digitalClockDisplay(local, tcr->abbrev);
        count = 0;
        data = false;
        delay(50);
      }

      if (millis() - prevMillis >= SLEEP_TIMEOUT) {
        step = STEP_SLEEP;
      }

      break;

    case STEP_FADE:
      // LED fade state

      x = (millis() % PERIOD) / (double) PERIOD;
      led_value = MAX_ANALOG_V * sin(PI * x);

      Serial.print(F("x: "));
      Serial.print(x);
      Serial.print(F(", c: "));
      Serial.println(led_value);

      analogWrite(LED_BLUE, led_value);
      analogWrite(LED_RED, led_value);
      analogWrite(LED_GR, led_value);

      if (x >= 0.99) {
        analogWrite(LED_BLUE, 0);
        analogWrite(LED_RED, 0);
        analogWrite(LED_GR, 0);

        prevMillis = millis();      // Update prevMillis to reset sleep timeout
        step = STEP_READ_CMD;
      }

      break;

    default:
      break;
  }
}
