#include <avr/sleep.h>

#include <Arduino.h>

#include <DS3232RTC.h>
//TODO #include <TinyWireM.h>
#include <USIWire.h>

// Input/output defines
#define LED_BLUE            5
#define LED_GR              3
#define LED_RED             2

#define BLU_STATE           1
#define BLU_RESET           0

#define RTC_INT_SQW        10

// Serial defines
#define SERIAL_BAUD      9600   // For at mode and for data mode (CC41, HM-10 and MLT-BT05)

// I2C defines
#define SDA                 4
#define SCL                 6

#define SLEEP_TIMEOUT  30000L   // Timeout before sleep
#define LENGTH             80   // Command buffer length


// Global variables
DS3232RTC RTC;
USIWire bus;                    // USIWire instance (I2C bus)

boolean data = false;
unsigned long prevMillis = 0;

// Put the micro to sleep
void system_sleep() {
  set_sleep_mode(SLEEP_MODE_PWR_DOWN);
  sleep_enable();
  sleep_mode();

  // sleeping ...
  sleep_disable(); // wake up fully
}

void printDigits(int digits) {
    // utility function for digital clock display: prints preceding colon and leading 0
    Serial.print(':');
    if(digits < 10)
        Serial.print('0');
    Serial.print(digits);
}

void digitalClockDisplay(void) {
    // digital clock display of the time
    Serial.print(hour());
    printDigits(minute());
    printDigits(second());
    Serial.print(' ');
    Serial.print(day());
    Serial.print(' ');
    Serial.print(month());
    Serial.print(' ');
    Serial.print(year());
    Serial.println();
}

// PCINT Interrupt Service Routine (unused)
ISR(PCINT0_vect) {
  // Don't do anything here but we must include this
  // block of code otherwise the interrupt calls an
  // uninitialized interrupt handler.
}

void setup() {
  OSCCAL = 0x86;                    // Calibrated OSSCAL value with TinyTuner

  Serial.begin(SERIAL_BAUD);

  // Pinmode set
  pinMode(LED_BLUE, OUTPUT);
  pinMode(LED_GR, OUTPUT);
  pinMode(LED_RED, OUTPUT);
  pinMode(BLU_STATE, INPUT);
  pinMode(BLU_RESET, OUTPUT);
  pinMode(RTC_INT_SQW, INPUT);

  Serial.println(F("START SERIAL"));
  Serial.print(F("Initial value of OSCCAL is 0x"));
  Serial.println(OSCCAL, HEX);

//  setSyncProvider(RTC.get);   // the function to get the time from the RTC
//  if(timeStatus() != timeSet)
//      Serial.println("Unable to sync with the RTC");
//  else
//      Serial.println("RTC has set the system time");

  ADCSRA  = 0;                      // Disable ADC to save power
  MCUCR  |= _BV(BODS);              // BOD disabled

  PCMSK0 |= _BV(PCINT0);            // Pin change mask: listen to portA bit 0 (D10)
  PCMSK0 |= _BV(PCINT2);            // Pin change mask: listen to portA bit 2 (D8)
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
      for(int fadeValue = 0 ; fadeValue <= 255; fadeValue +=5) {
          analogWrite(LED_BLUE, fadeValue);
          delay(100);
        }
      delay(1000);
      digitalWrite(LED_BLUE, LOW);

      for(int fadeValue = 0 ; fadeValue <= 255; fadeValue +=5) {
        analogWrite(LED_RED, fadeValue);
        delay(100);
      }
      delay(1000);
      digitalWrite(LED_RED, LOW);

      for(int fadeValue = 0 ; fadeValue <= 255; fadeValue +=5) {
        analogWrite(LED_GR, fadeValue);
        delay(100);
      }
      delay(1000);
      digitalWrite(LED_GR, LOW);
    }

    if (strcmp(buffer, "OFF") == 0) {
      digitalWrite(LED_BLUE, LOW);
      digitalWrite(LED_RED, LOW);
      digitalWrite(LED_GR, LOW);
    }

    //digitalClockDisplay();
  }
}
