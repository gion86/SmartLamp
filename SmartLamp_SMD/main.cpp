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
 * VERSION 0.2
 */

#include <avr/io.h>
#include <avr/eeprom.h>
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

#define ALARMS_OFFSET       0   // Alarms array EEPROM start address

// LED defines
#define FADE_TIME        5000   // LED crossfade total duration
#define MAX_ANALOG_V      255   // Analog output max value for RGB

// States
#define STEP_SLEEP         0
#define SET_DAY_ALARM      5
#define STEP_READ_CMD      10
#define STEP_FADE          15

// Global constants
// CET Time Zone (Rome, Berlin) -> UTC/GMT + 1
const TimeChangeRule CEST = {"CEST", Last, Sun, Mar, 2, 120};     // Central European Summer Time (DST)
const TimeChangeRule CET  = {"CET ", Last, Sun, Oct, 3, 60};      // Central European Standard Time

// Global variables
TwoWire bus;                    // USIWire instance (I2C bus)
DS3232RTC RTC(bus);             // DS3232 RTC instance (I2C bus)

unsigned long prevMillis = 0;   // Millis counter to sleep


// Command parsing variables
size_t count = 0;               // Char count on buffer (data parsing)
bool cmd = false;               // Command present on serial interface (BLE)

// Time variables
//Timezone myTZ(CET, CEST);       // Constructor to build object with TimeChangeRule
Timezone myTZ(CEST, CET);       // Constructor to build object with TimeChangeRule

TimeChangeRule *tcr;            // Pointer to the time change rule, use to get TZ abbreviations

struct tm systemTime;           // Current system time (local timezone)

typedef struct {
  int8_t hh, mm, ss;            // Time of day
  uint16_t fadeTime;            // Fade time in seconds, from the alarm wake up
  bool enabled;
} alarm;                        // Alarm structure

alarm alarms[7];                // Alarms array [0...6 as weekdays sun...sat]

// LED variables
uint8_t ledColor[] = {0, 0, 0}; // LED color

uint8_t step = STEP_SLEEP;

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

  memset(&tm, 0, sizeof(tm));

  //tm.tm_year = YYYY - 1900 + 30;    // avr-libc time.h: years since 1900 + y2k epoch difference (2000 - 1970)
  tm.tm_year = YYYY - 1900;         // avr-libc time.h: years since 1900 TODO test
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

/**
 * TODO
 */
static void digitalClockDisplay(struct tm tm, const char *tz = NULL) {
  // Digital clock display of the time
  Serial.print(tm.tm_hour);
  printDigits(tm.tm_min);
  printDigits(tm.tm_sec);
  Serial.print(' ');
  Serial.print(tm.tm_mday);
  Serial.print('/');
  Serial.print(tm.tm_mon + 1);              // avr-libc time.h: months in [0, 11]
  Serial.print('/');
  //Serial.print(tm.tm_year + 1900 - 30);     // avr-libc time.h: years since 1900 + y2k epoch difference (2000 - 1970)
  Serial.print(tm.tm_year + 1900);          // avr-libc time.h: years since 1900 TODO test

  Serial.print(' ');
  Serial.print(tm.tm_wday);

  if (tz != NULL) {
    Serial.print(' ');
    Serial.print(tz);
  }

  Serial.println();
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

  memset(&tm, 0, sizeof(tm));
  gmtime_r(&time, &tm);

  digitalClockDisplay(tm, tz);
}

/**
 * TODO
 */
static bool setNextAlarm(int8_t hour, int8_t min, int8_t wday) {
  if (wday < 0 || wday > 6) {
    return false;
  }

  for (int i = wday, c = 0; i < wday + 7; ++i, ++c) {
    int i_m = i % 7;

    if (alarms[i_m].enabled) {

      if (c > 0 || (i_m == wday && (hour < alarms[i_m].hh || (hour == alarms[i_m].hh && min < alarms[i_m].mm)))) {
#ifdef DEBUG
        Serial.print("Set alarm on ");
        Serial.print(alarms[i_m].hh);
        Serial.print(":");
        Serial.print(alarms[i_m].mm);
        Serial.print(" ");
        Serial.println(i_m);
#endif

        RTC.setAlarm(ALM1_MATCH_DAY, 00, alarms[i_m].mm, alarms[i_m].hh, i_m);
        RTC.alarmInterrupt(ALARM_1, true);
        return true;
      }
    }
  }

  RTC.alarmInterrupt(ALARM_1, false);
  return false;
}

/**
 * TODO
 */
static time_t getTime(struct tm *tm) {
  memset(tm, 0, sizeof(*tm));
  time_t utc = RTC.get();
  time_t local = myTZ.toLocal(utc, &tcr);
  gmtime_r(&local, tm);

  return local;
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

/*
 * Parses a command string from serial interface into predefined commands.
 * Also sets global variables depending on received command.
 *
 * Param:
 * - char *buffer: pointer to the char array, NULL-terminated.
 */
static bool parseCommand(char *buffer) {
  Serial.println(buffer);

  // ------------------------------------------------------
  // Set date and time
  // ------------------------------------------------------
  char *s = strstr(buffer, "ST_");

  // buffer = "ST_20171105_141607"
  if (s != NULL && strlen(buffer) == 18) {
    int16_t YYYY;
    int8_t MM, DD, hh, mm, ss;

    YYYY = atod(buffer[3]) * 1000 + atod(buffer[4]) * 100 + atod(buffer[5]) * 10 + atod(buffer[6]);
    MM   = atod(buffer[7]) * 10 + atod(buffer[8]);
    DD   = atod(buffer[9]) * 10 + atod(buffer[10]);
    hh   = atod(buffer[12]) * 10 + atod(buffer[13]);
    mm   = atod(buffer[14]) * 10 + atod(buffer[15]);
    ss   = atod(buffer[16]) * 10 + atod(buffer[17]);

#ifdef DEBUG
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
#endif

    // Date and time data checks
    if (YYYY < 1900 || MM < 1 || DD < 1 || hh < 0 || mm < 0 || ss < 0
        ||
        MM > 12 || DD > 31 || hh > 23 || mm > 59 || ss > 59) {
      return false;
    }

    time_t t;
    t = tmConvert_t(YYYY, MM, DD, hh, mm, ss);

    if (RTC.set(t) == 0) {
#ifdef DEBUG
      time_t utc = RTC.get();
      Serial.print("UTC: ");
      digitalClockDisplay(utc, "UTC");

      time_t local = myTZ.toLocal(utc, &tcr);
      Serial.print("Local: ");
      digitalClockDisplay(local, tcr->abbrev);
#endif

      // Get the current system time
      getTime(&systemTime);

      // TODO alarm init flag
      setNextAlarm(systemTime.tm_hour, systemTime.tm_min, systemTime.tm_wday);
      return true;
    } else {
      return false;
    }
  }

  // ------------------------------------------------------
  // Set alarm on weekday
  // ------------------------------------------------------
  s = strstr(buffer, "AL_");

  //buffer = "AL_05_1418"
  if (s != NULL && strlen(buffer) == 10) {
    int8_t WD, hh, mm;

    WD   = atod(buffer[3]) * 10 + atod(buffer[4]);
    hh   = atod(buffer[6]) * 10 + atod(buffer[7]);
    mm   = atod(buffer[8]) * 10 + atod(buffer[9]);

#ifdef DEBUG
    Serial.print("WD = ");
    Serial.println(WD);

    Serial.print("hh = ");
    Serial.println(hh);

    Serial.print("mm = ");
    Serial.println(mm);
#endif

    // Data checks
    if (WD < 0 || hh < 0 || mm < 0 || WD > 6 || hh > 23 || mm > 59) {
      return false;
    }

    alarms[WD].hh = hh;
    alarms[WD].mm = mm;
    alarms[WD].ss = 0;
    alarms[WD].fadeTime = FADE_TIME;
    alarms[WD].enabled = true;

    eeprom_write_block((void*) &alarms, (void*) ALARMS_OFFSET, sizeof(alarms));

    // Get the current system time
    getTime(&systemTime);

    setNextAlarm(systemTime.tm_hour, systemTime.tm_min, systemTime.tm_wday);
    return true;
  }

  // ------------------------------------------------------
  // Disable alarm on weekday
  // ------------------------------------------------------
  s = strstr(buffer, "AL_DIS_");

  //buffer = "AL_DIS_07"
  if (s != NULL && strlen(buffer) == 9) {
    uint8_t WD;

    WD = atod(buffer[7]) * 10 + atod(buffer[8]);

#ifdef DEBUG
    Serial.print("WD = ");
    Serial.println(WD);
#endif

    // Data checks
    if (WD < 0 || WD > 6) {
      return false;
    }

    alarms[WD].enabled = false;

    eeprom_write_block((void*) &alarms, (void*) ALARMS_OFFSET, sizeof(alarms));

    // Get the current system time
    getTime(&systemTime);

    setNextAlarm(systemTime.tm_hour, systemTime.tm_min, systemTime.tm_wday);
    return true;
  }

  // ------------------------------------------------------
  // Set lamp fade time after the alarm
  // ------------------------------------------------------
  s = strstr(buffer, "FT_");

  //buffer = "FT_1400_1800"
  if (s != NULL && strlen(buffer) == 9) {

    for (int i = 0; i < 7; ++i) {
     uint16_t ssb = atod(buffer[3]) * 1000 + atod(buffer[4]) * 100 + atod(buffer[5]) * 10 + atod(buffer[6]);
     uint16_t ssa = atod(buffer[8]) * 1000 + atod(buffer[9]) * 100 + atod(buffer[10]) * 10 + atod(buffer[11]);

     // TODO set the alarm time
     alarms[i].fadeTime = ssa + ssb;
    }

    eeprom_write_block((void*) &alarms, (void*) ALARMS_OFFSET, sizeof(alarms));

#ifdef DEBUG
    Serial.print("ss = ");
    Serial.println(alarms[0].fadeTime);
#endif

    return true;
  }

  // ------------------------------------------------------
  // Set RGB color for the lamp LED
  // ------------------------------------------------------
  s = strstr(buffer, "RGB_");

  //buffer = "RGB_055_129_255"
  if (s != NULL && strlen(buffer) == 15) {
    uint8_t ledTemp[] = {0, 0, 0};

    ledTemp[0] = atod(buffer[4]) * 100 + atod(buffer[5]) * 10 + atod(buffer[6]);
    ledTemp[1] = atod(buffer[8]) * 100 + atod(buffer[9]) * 10 + atod(buffer[10]);
    ledTemp[2] = atod(buffer[12]) * 100 + atod(buffer[13]) * 10 + atod(buffer[14]);

#ifdef DEBUG
    Serial.print("red = ");
    Serial.println(ledTemp[0]);

    Serial.print("green = ");
    Serial.println(ledTemp[1]);

    Serial.print("blue = ");
    Serial.println(ledTemp[2]);
#endif

    // Data checks
    if (ledTemp[0] < 0 || ledTemp[1] < 0 || ledTemp[2] < 0
        ||
        ledTemp[0] > 255 || ledTemp[1] > 255 || ledTemp[2] > 255) {
      return false;
    }

    memcpy((void*) ledColor, (void*) ledTemp, sizeof(ledColor));

#ifdef DEBUG
    for (int i = 0; i < 3; ++i) {
      Serial.print("ledColor[");
      Serial.print(i);
      Serial.print("] = ");
      Serial.println(ledColor[i]);
    }
#endif

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

  // RTC connection check
  if ((retcode = RTC.checkCon()) != 0) {
    Serial.print("RTC err: ");
    Serial.println(retcode);

    // Signal the error with "retcode" number of flash on the LED
    for (int i = 0; i < retcode; ++i) {
      digitalWrite(BLU_LED, (i % 2 ? HIGH : LOW));
      delay(500);
    }

    // Exit application code to infinite loop
    exit(retcode);
  }

  // Power settings
  powerReduction();

  // Interrupt configuration
  PCMSK2 |= _BV(PCINT20);           // Pin change mask: listen to portD bit 4 (D4) (RTC_INT_SQW)
  PCMSK2 |= _BV(PCINT16);           // Pin change mask: listen to portD bit 0 (D0) (Serial RX)
  PCICR  |= _BV(PCIE2);             // Enable PCINT interrupt on portD

  prevMillis = millis();

  // Read alarms[] array from EEPROM
  eeprom_read_block((void*) &alarms, (void*) ALARMS_OFFSET, sizeof(alarms));

  // Get the current system time
  getTime(&systemTime);

  for (int i = 0; i < 7; ++i) {
     alarms[i].enabled = false;
  }
}

// ========================================================
// |                        LOOP                          |
// ========================================================

void loop() {
  char buffer[CMD_LENGTH];
  uint8_t led_value;
  float x = 0;

  switch (step) {
    case STEP_SLEEP:
      // ######################################################################
      // Sleep state
#ifdef DEBUG
      Serial.println("Sleeping...");
#endif

      delay(50);
      system_sleep();

      // Get the current system time
      getTime(&systemTime);

#ifdef DEBUG
      Serial.println("System time");
      digitalClockDisplay(systemTime, "Local");
#endif

      // Necessary to reset the alarm flag on RTC!
      if (RTC.alarm(ALARM_1)) {
#ifdef DEBUG
        Serial.println("From alarm 1");
#endif
        delay(50);
        //step = STEP_FADE;
        step = SET_DAY_ALARM;
      } else if (RTC.alarm(ALARM_2)) {
#ifdef DEBUG
        Serial.println("From alarm 2");
#endif
        delay(50);
        step = SET_DAY_ALARM;
      } else {
        step = STEP_READ_CMD;
      }

      prevMillis = millis();

      break;

    case SET_DAY_ALARM:
      // ######################################################################
      // Set alarm for the day "tm_wday"

      setNextAlarm(systemTime.tm_hour, systemTime.tm_min, systemTime.tm_wday);

      step = STEP_SLEEP;
      // TODO step = STEP_FADE;
      break;

    case STEP_READ_CMD:
      // ######################################################################
      // Read command state (from serial connection to bluetooth module)

      while (Serial.available() && count < CMD_LENGTH - 1) {
        char c = (char) Serial.read();

        prevMillis = millis();      // Update prevMillis to reset sleep timeout

        if (c == '\r' || c == '\n') {
          if (c == '\n') {
            // Command present
            cmd = true;

            // Set string delimiter
            buffer[count] = '\0';
            break;
          }
          continue;
        }

        buffer[count] = c;
        count++;
      }

      if (cmd) {
#ifdef DEBUG
        Serial.print("COUNT = ");
        Serial.println(count);
#endif

        // Send back "OK" response as an acknowledge.
        if (parseCommand(buffer)) {
          Serial.println("OK");
        }

        count = 0;
        cmd = false;
        delay(50);
      }

      if (RTC.alarm(ALARM_1)) {
        step = STEP_FADE;
      } else if (RTC.alarm(ALARM_2)) {
        step = SET_DAY_ALARM;
      } else if (millis() - prevMillis >= SLEEP_TIMEOUT) {
        step = STEP_SLEEP;
      }

      break;

    case STEP_FADE:
      // ######################################################################
      // LED fade state

      x = (millis() % FADE_TIME) / (float) FADE_TIME;
      led_value = MAX_ANALOG_V * sin(PI * x);

#ifdef DEBUG
      Serial.print("x: ");
      Serial.print(x);
      Serial.print(", c: ");
      Serial.println(led_value);
#endif

      analogWrite(LED_BLUE, led_value);
      analogWrite(LED_RED, led_value);
      analogWrite(LED_GR, led_value);

      if (x >= 0.99) {
        analogWrite(LED_BLUE, 0);
        analogWrite(LED_RED, 0);
        analogWrite(LED_GR, 0);

        prevMillis = millis();      // Update prevMillis to reset sleep timeout

        if (RTC.alarm(ALARM_2)) {
          step = SET_DAY_ALARM;
        } else {
          step = STEP_READ_CMD;
        }
      }

      break;

    default:
      break;
  }
}
