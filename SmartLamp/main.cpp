#include <avr/sleep.h>
#include <avr/iotnx4.h>

#include <Arduino.h>
#include <NeoSWSerial.h>

#include <DS3232RTC.h>
//TODO #include <TinyWireM.h>
#include <USIWire.h>

// Input/output defines
#define LED_PIN             5

#define LED_BLUE            5
#define LED_GR              8
#define LED_RED             7

#define BLU_STATE           1
#define BLU_RESET           9
#define RTC_INT_SQW        10

// Serial defines
#define RX_PIN              2
#define TX_PIN              3
#define SERIAL_BAUD      9600
#define BLE_BAUD         9600   // For at mode and for data mode (CC41, HM-10 and MLT-BT05)

#define SLEEP_TIMEOUT  10000L   // Timeout before sleep
#define LENGTH             80


// Global variables
NeoSWSerial ble(RX_PIN, TX_PIN);
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

void setup() {
  OSCCAL = 0x86;                // Calibrated OSSCAL value with TinyTuner

  Serial.begin(SERIAL_BAUD);
  ble.begin(BLE_BAUD);

  pinMode(LED_PIN, OUTPUT);

  delay(100);

  //ble.println("AT");
  ble.println("AT+SLEEP");

  //ble.println("START BLE");
  Serial.println("START SERIAL");

  Serial.print(F("Initial value of OSCCAL is 0x"));
  Serial.println(OSCCAL, HEX);

//  setSyncProvider(RTC.get);   // the function to get the time from the RTC
//  if(timeStatus() != timeSet)
//      Serial.println("Unable to sync with the RTC");
//  else
//      Serial.println("RTC has set the system time");

  // Disable ADC to save power
  ADCSRA = 0;

  PCMSK0 |= (1<<PCINT10);       // Pin change mask: listen to portB bit 2 (D 2)
  GIMSK  |= (1<<PCIE0);         // Enable PCINT interrupt
}


void loop() {
  size_t count = 0;
  char buffer[LENGTH];

  // FIXME Wake from sleep with new CORE can't read first serial bytes....

  if (millis() - prevMillis >= SLEEP_TIMEOUT) {
    prevMillis = millis();
    Serial.print(F("Sleeping..."));
    system_sleep();
  }

  while (ble.available() && count < LENGTH - 1) {
    delay(2);
    char c = (char) ble.read();

    prevMillis = millis();      // Update prevMillis to reset sleep timeout

    if (c == '\r' || c == '\n') {
      if (c == '\n') {
        data = true;
        ble.flush();
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
      digitalWrite(LED_PIN, HIGH);
    }

    if (strcmp(buffer, "OFF") == 0) {
      digitalWrite(LED_PIN, LOW);
    }

    //digitalClockDisplay();
  }

  /*//read from the HM-10 and print in the Serial
  if (ble.available()) {
    delay(5);
    Serial.write(ble.read());
  }

  //read from the Serial and print to the HM-10
  if (Serial.available()) {
    delay(5);
    ble.write(Serial.read());
  } */
}
