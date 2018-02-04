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
#define FADE_TIME          10   // Default LED fade total duration [m]
#define MAX_PWM_CNT       655   // PWM timer max count (<= 65535 @ 16 bit) -> frequency = ? TODO

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
Timezone TZ(CEST, CET);         // Constructor to build object with TimeChangeRule

TimeChangeRule *tcr;            // Pointer to the time change rule, use to get TZ abbreviations

struct tm systemTime;           // Current system time (local timezone)

typedef struct {
  bool enabled;
  int8_t hh, mm;                // Time of day
  uint8_t fadeTime;             // Fade time in minutes, from the alarm wake up
} alarm;                        // Alarm structure

alarm alarms[7];                // Alarms array [0...6 as weekdays sun...sat]

// LED variables
uint8_t ledColor[] = {0, 0, 0}; // LED color

uint8_t step = STEP_SLEEP;


// ########################################################
// Serial debug functions
// ########################################################

/*
 * Write to serial a digit with zero padding if needed.
 *
 * Param:
 * - int digits
 */
static void inline printDigits(int digits) {
  if (digits < 10)
    Serial.print('0');
  Serial.print(digits);
}

/*
 * Writes to Serial the clock (date and time) in human readable format.
 *
 * Param:
 * - struct tm  *tm: struct tm from the time.h (avr-libc)
 * - const char *tz: string for the timezone, if not used set to NULL
 */
static void digitalClockDisplay(struct tm *tm, const char *tz = NULL) {
  // Digital clock display of the time
  printDigits(tm->tm_hour);
  Serial.print(':');
  printDigits(tm->tm_min);
  Serial.print(':');
  printDigits(tm->tm_sec);
  Serial.print(' ');
  Serial.print(tm->tm_mday);
  Serial.print('/');
  Serial.print(tm->tm_mon + 1);              // avr-libc time.h: months in [0, 11]
  Serial.print('/');
  Serial.print(tm->tm_year + 1900);          // avr-libc time.h: years since 1900

  Serial.print(' ');
  Serial.print(tm->tm_wday);

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

  memset((void*) &tm, 0, sizeof(tm));
  gmtime_r(&time, &tm);

  digitalClockDisplay(&tm, tz);
}

/**
 * Prints the alarm array for debug
 */
void printAlarms() {
  for (int i = 0; i < 7; ++i) {
    Serial.print("EN[");
    Serial.print(i);
    Serial.print("]: ");
    Serial.print(alarms[i].enabled);
    Serial.print(", ");
    printDigits(alarms[i].hh);
    Serial.print(":");
    printDigits(alarms[i].mm);
    Serial.println();
  }
  Serial.println();
}


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

  memset((void*) &tm, 0, sizeof(tm));

  tm.tm_year = YYYY - 1900;         // avr-libc time.h: years since 1900
  tm.tm_mon  = MM - 1;              // avr-libc time.h: months in [0, 11]
  tm.tm_mday = DD;
  tm.tm_hour = hh;
  tm.tm_min  = mm;
  tm.tm_sec  = ss;
  return mk_gmtime(&tm);
}

/**
 * Select and activate the next alarm in chronological order from the time sys_t.
 *
 * Param:
 * - struct tm *sys_t: system time (struct tm from the time.h avr-libc)
 *
 * Returns:
 * true if at least one alarm is enabled and active (on one weekday).
 */
static bool setNextAlarm(struct tm *sys_t) {
  if (sys_t->tm_wday < 0 || sys_t->tm_wday > 6) {
    return false;
  }

//#ifdef DEBUG
//  Serial.println();
//  Serial.println("NEXT ALARM");
//#endif

  // Check for one day more than a week (8) to account for the next alarm on the same weekday:
  // case where only on alarm is enable in the entire week.
  for (int i = sys_t->tm_wday, c = 0; i < sys_t->tm_wday + 8; ++i, ++c) {
    int i_m = i % 7;

    if (alarms[i_m].enabled) {

//#ifdef DEBUG
//        Serial.print("c, i_m = ");
//        Serial.print(c);
//        Serial.print(", ");
//        Serial.println(i_m);
//        Serial.println();
//#endif

      if (c > 0 || (i_m == sys_t->tm_wday &&
                   (sys_t->tm_hour < alarms[i_m].hh || (sys_t->tm_hour == alarms[i_m].hh && sys_t->tm_min < alarms[i_m].mm)))) {

        time_t alarm, utc_alarm;
        struct tm al_tm, utc_alarm_tm;

        // Length of month, given the year and month, where month is in the range 1 to 12.
        uint8_t m_l = month_length(sys_t->tm_year, sys_t->tm_mon + 1);

        memset((void*) &al_tm, 0, sizeof(al_tm));
        memset((void*) &utc_alarm_tm, 0, sizeof(utc_alarm_tm));

        // Alarm absolute time reconstruction.
        al_tm.tm_year = sys_t->tm_year;
        al_tm.tm_mon  = sys_t->tm_mon;
        al_tm.tm_mday = sys_t->tm_mday + c;
        al_tm.tm_hour = alarms[i_m].hh;
        al_tm.tm_min  = alarms[i_m].mm;
        al_tm.tm_sec  = 0;

        // Check for month and year overflow
        if (al_tm.tm_mday > m_l) {
          al_tm.tm_mday = al_tm.tm_mday % m_l;
          al_tm.tm_mon++;

          if (al_tm.tm_mon > 11) {
            al_tm.tm_mon = 0;
            al_tm.tm_year++;
          }
        }

//#ifdef DEBUG
//        Serial.print("al_tm.tm_year = ");
//        Serial.println(al_tm.tm_year);
//
//        Serial.print("m_l = ");
//        Serial.println(m_l);

//        Serial.print("m, d = ");
//        Serial.print(loc_tm.tm_mon);
//        Serial.print(", ");
//        Serial.println(loc_tm.tm_mday);
//        Serial.println();
//        digitalClockDisplay(&al_tm, "Local");
//#endif

        // Local alarm time to UTC time conversion
        alarm     = mk_gmtime(&al_tm);
        utc_alarm = TZ.toUTC(alarm);

//#ifdef DEBUG
//        Serial.print("Alarm local = ");
//        Serial.println(alarm);
//        Serial.print("Alarm UTC = ");
//        Serial.println(utc_alarm);
//#endif

        // time_t to struct tm conversion
        gmtime_r(&utc_alarm, &utc_alarm_tm);

#ifdef DEBUG
        Serial.print("Set alarm on ");
        digitalClockDisplay(&utc_alarm_tm, "UTC");
#endif

        // Set alarm to the RTC clock in UTC format!!
        RTC.setAlarm(ALM1_MATCH_DAY, 00, utc_alarm_tm.tm_min, utc_alarm_tm.tm_hour, utc_alarm_tm.tm_wday);
        RTC.alarmInterrupt(ALARM_1, true);
        return true;
      }
    }
  }

#ifdef DEBUG
  Serial.println("No alarm enabled!");
#endif

  RTC.alarmInterrupt(ALARM_1, false);
  return false;
}

/**
 * Reads system time from RTC clock.
 *
 * Param:
 * - struct tm *tm: the struct tm to be filled.
 *
 * Returns:
 * the system time in binary format (time_t avr-libc).
 */
static time_t getSysTime(struct tm *tm) {
  memset((void*) tm, 0, sizeof(*tm));
  time_t utc = RTC.get();
  time_t local = TZ.toLocal(utc, &tcr);
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

//#ifdef DEBUG
//    Serial.print("YYYY = ");
//    Serial.println(YYYY);
//
//    Serial.print("MM = ");
//    Serial.println(MM);
//
//    Serial.print("DD = ");
//    Serial.println(DD);
//
//    Serial.print("hh = ");
//    Serial.println(hh);
//
//    Serial.print("mm = ");
//    Serial.println(mm);
//
//    Serial.print("ss = ");
//    Serial.println(ss);
//#endif

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
      Serial.print("UTC:   ");
      digitalClockDisplay(utc, "UTC");

      time_t local = TZ.toLocal(utc, &tcr);
      Serial.print("Local: ");
      digitalClockDisplay(local, tcr->abbrev);
#endif

      // Get the current system time and set next alarm
      getSysTime(&systemTime);
      setNextAlarm(&systemTime);

      return true;
    } else {
      return false;
    }
  }

  // ------------------------------------------------------
  // Set alarm on weekday
  // ------------------------------------------------------
  s = strstr(buffer, "AL_");

  // buffer = "AL_05_1418" or buffer = "AL_05_1418_10"
  if (s != NULL && strlen(buffer) >= 10) {
    int8_t WD, hh, mm;
    uint8_t fadeTime = FADE_TIME;

    WD = atod(buffer[3]) * 10 + atod(buffer[4]);
    hh = atod(buffer[6]) * 10 + atod(buffer[7]);
    mm = atod(buffer[8]) * 10 + atod(buffer[9]);

    if (strlen(buffer) == 13) {
      uint8_t fadeTime = atod(buffer[11]) * 10 + atod(buffer[12]);

      if (fadeTime > 99) {
        return false;
      }
    }

//#ifdef DEBUG
//    Serial.print("WD = ");
//    Serial.println(WD);
//
//    Serial.print("hh = ");
//    Serial.println(hh);
//
//    Serial.print("mm = ");
//    Serial.println(mm);
//
//    Serial.print("ft = ");
//    Serial.println(fadeTime);
//#endif

    // Data checks
    if (WD < 0 || hh < 0 || mm < 0 || WD > 6 || hh > 23 || mm > 59) {
      return false;
    }

    alarms[WD].hh = hh;
    alarms[WD].mm = mm;
    alarms[WD].fadeTime = fadeTime;
    alarms[WD].enabled = true;

    eeprom_write_block((void*) &alarms, (void*) ALARMS_OFFSET, sizeof(alarms));

    printAlarms();

    // Get the current system time and set next alarm
    getSysTime(&systemTime);
    setNextAlarm(&systemTime);

    return true;
  }

  // ------------------------------------------------------
  // Disable alarm on weekday
  // ------------------------------------------------------
  s = strstr(buffer, "AL_DIS_");

  // buffer = "AL_DIS_06"
  if (s != NULL && strlen(buffer) == 9) {
    uint8_t WD;

    WD = atod(buffer[7]) * 10 + atod(buffer[8]);

//#ifdef DEBUG
//    Serial.print("WD = ");
//    Serial.println(WD);
//#endif

    // Data checks
    if (WD < 0 || WD > 6) {
      return false;
    }

    alarms[WD].enabled = false;

    eeprom_write_block((void*) &alarms, (void*) ALARMS_OFFSET, sizeof(alarms));

    // Get the current system time and set next alarm
    getSysTime(&systemTime);
    setNextAlarm(&systemTime);

    return true;
  }

  // ------------------------------------------------------
  // Set lamp fade time after the alarm
  // ------------------------------------------------------
  s = strstr(buffer, "FT_");

  // buffer = "FT_05_10"
  if (s != NULL && strlen(buffer) == 8) {
    uint8_t WD;
    uint8_t fadeTime;

    WD       = atod(buffer[3]) * 10 + atod(buffer[4]);
    fadeTime = atod(buffer[6]) * 10 + atod(buffer[7]);

//#ifdef DEBUG
//    Serial.print("WD = ");
//    Serial.println(WD);
//
//    Serial.print("ft = ");
//    Serial.println(fadeTime);
//#endif

    // Data checks
    if (WD < 0 || WD > 6 || fadeTime > 99) {
      return false;
    }

    // Set the alarm fade time
    alarms[WD].fadeTime = fadeTime;

    eeprom_write_block((void*) &alarms, (void*) ALARMS_OFFSET, sizeof(alarms));

    return true;
  }

  // ------------------------------------------------------
  // Set RGB color for the lamp LED
  // ------------------------------------------------------
  s = strstr(buffer, "RGB_");

  // buffer = "RGB_055_129_255"
  if (s != NULL && strlen(buffer) == 15) {
    uint8_t ledTemp[] = {0, 0, 0};

    ledTemp[0] = atod(buffer[4]) * 100 + atod(buffer[5]) * 10 + atod(buffer[6]);
    ledTemp[1] = atod(buffer[8]) * 100 + atod(buffer[9]) * 10 + atod(buffer[10]);
    ledTemp[2] = atod(buffer[12]) * 100 + atod(buffer[13]) * 10 + atod(buffer[14]);

//#ifdef DEBUG
//    Serial.print("red = ");
//    Serial.println(ledTemp[0]);
//
//    Serial.print("green = ");
//    Serial.println(ledTemp[1]);
//
//    Serial.print("blue = ");
//    Serial.println(ledTemp[2]);
//#endif

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
    // - TIMER1: for Fast PWM
    PRR = 0xFF & (~(1 << PRUSART0)) & (~(1 << PRTWI)) & (~(1 << PRTIM0)) & (~(1 << PRTIM1));
}

/*
 * Set PWM operation mode
 */
static void setupPWM() {
  // Set up counter1 A output at 25% and B output at 75%
  // using ICR1 as top (16bit), Fast PWM.

  // PB1 and PB2 output mode... done
  // DDRB |= (1 << DDB1)|(1 << DDB2);

  // Setup Fast PWM mode using ICR1 as TOP
  TCCR1A |= (1 << WGM11);
  TCCR1A &= ~(1 << WGM10);
  TCCR1B |= (1 << WGM12);
  TCCR1B |= (1 << WGM13);

  // Set none-inverting mode
  TCCR1A |= (1 << COM1A1)|(1 << COM1B1);

  // Set TOP to 16bit
  ICR1 = MAX_PWM_CNT;

  // Set PWM for 25% duty cycle @ 16bit
  OCR1A = MAX_PWM_CNT;

  // Set PWM for 75% duty cycle @ 16bit
  OCR1B = MAX_PWM_CNT;

  // Start timer...
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

  // Reset high
  digitalWrite(BLU_RESET, HIGH);

  // RTC connection check
  if ((retcode = RTC.checkCon()) != 0) {
    Serial.print("RTC err: ");
    Serial.println(retcode);

    // Signal the error with "retcode" number of flash on the LED
    for (int i = 0; i < retcode; ++i) {
      digitalWrite(BLU_LED, HIGH);
      delay(500);
      digitalWrite(BLU_LED, LOW);
      delay(500);
    }

    // Exit application code to infinite loop
    exit(retcode);
  }

  // Read alarms[] array from EEPROM: set bit EESAVE (high fuse byte = D7) to preserve EEPROM
  // through the chip erase cycle.
  eeprom_read_block((void*) &alarms, (void*) ALARMS_OFFSET, sizeof(alarms));

  // Check for default value of EEPROM (0xFF -> -1 to avoid C compiler signed/unsigned
  // comparison warning)
  if (alarms[0].hh == -1 && alarms[0].mm == -1) {
    memset((void*) &alarms, 0, sizeof(alarms));
    eeprom_write_block((void*) &alarms, (void*) ALARMS_OFFSET, sizeof(alarms));
  }

#ifdef DEBUG
  printAlarms();
#endif

  // Get the current system time from RTC
  getSysTime(&systemTime);

#ifdef DEBUG
  Serial.println("STARTUP system time");
  digitalClockDisplay(&systemTime, tcr->abbrev);
#endif

  // Set the next alarm.
  setNextAlarm(&systemTime);

  // Power settings
  powerReduction();

  // Interrupt configuration
  PCMSK2 |= _BV(PCINT20);           // Pin change mask: listen to portD bit 4 (D4) (RTC_INT_SQW)
  PCMSK2 |= _BV(PCINT16);           // Pin change mask: listen to portD bit 0 (D0) (Serial RX)
  PCICR  |= _BV(PCIE2);             // Enable PCINT interrupt on portD

  // Setup hardware Fast PWM
  setupPWM();

  prevMillis = millis();
  delay(50);
}

// ========================================================
// |                        LOOP                          |
// ========================================================

void loop() {
  char buffer[CMD_LENGTH];
  uint16_t led_value;
  float x = 0;

  float fadeTime;
  unsigned long millTest;

  switch (step) {
    case STEP_SLEEP:
      // ######################################################################
      // Sleep state
#ifdef DEBUG
      Serial.println("Sleeping...");
      Serial.println();
      Serial.println();
#endif

      delay(50);
      system_sleep();

      // Get the current system time from RTC
      getSysTime(&systemTime);

#ifdef DEBUG
      Serial.println("System time");
      digitalClockDisplay(&systemTime, tcr->abbrev);
#endif

      // Necessary to reset the alarm flag on RTC!
      if (RTC.alarm(ALARM_1)) {
#ifdef DEBUG
        Serial.println("Wake up from alarm 1");
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
      setNextAlarm(&systemTime);

      step = STEP_SLEEP;
      prevMillis = millis();
      //step = STEP_FADE;
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
//#ifdef DEBUG
//        Serial.print("COUNT = ");
//        Serial.println(count);
//#endif

        // Send back "OK" response as an acknowledge.
        if (parseCommand(buffer)) {
          Serial.println("OK");
        }

        count = 0;
        cmd = false;
        delay(50);
      }

      if (RTC.alarm(ALARM_1)) {
        step = SET_DAY_ALARM;
        //step = STEP_FADE;
      } else if (millis() - prevMillis >= SLEEP_TIMEOUT) {
        step = STEP_SLEEP;
      }

      break;

    case STEP_FADE:
      // ######################################################################
      // LED fade state

      // Start the timer with no prescaler
      TCCR1B |= (1 << CS10);

      // Fade time in millisecond
      fadeTime = alarms[systemTime.tm_wday].fadeTime * 60000.0;

      millTest = millis();

      if (RTC.alarm(ALARM_1)) {
        prevMillis = millis();
      }

      x = (millis() - prevMillis) / fadeTime;
      led_value = MAX_PWM_CNT * sin(PI * x);

#ifdef DEBUG
      Serial.print("millTest: ");
      Serial.print(millTest);
      Serial.print(", x: ");
      Serial.print(x);
      Serial.print(", c: ");
      Serial.println(led_value);
#endif

      // PWM duty cicle on pins PB1 and PB2
      OCR1A = led_value;
      OCR1B = led_value;

      // Software pin set (the PWM at PB3 is only 8 bit of resolution)
      if (PINB & (1 << PINB1))
        PORTB |= (1 << PORTB3);
      else
        PORTB &= ~(1 << PORTB3);

      if (x >= 0.99) {
        // Stop the PWM timer
        TCCR1B &= ~(1 << CS10);

        prevMillis = millis();      // Update prevMillis to reset sleep timeout

        step = STEP_READ_CMD;
      }

      break;

    default:
      break;
  }
}
