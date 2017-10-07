#include <avr/sleep.h>
#include <avr/iotnx4.h>

#include <Arduino.h>
#include <SoftwareSerial.h>

#define LED_PIN             4
#define RX_PIN              2
#define TX_PIN              3
#define STATE_PIN           7

// misc
#define SERIAL_BAUD      9600
#define BLE_BAUD         9600   // for at mode and for data mode (CC41, HM-10 and MLT-BT05)

#define LENGTH             80

SoftwareSerial ble(RX_PIN, TX_PIN);

// Put the micro to sleep
void system_sleep() {
  set_sleep_mode(SLEEP_MODE_PWR_DOWN);
  sleep_enable();
  sleep_mode();

  // sleeping ...
  sleep_disable(); // wake up fully
}

void setup() {
  OSCCAL = 0x80;

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

  // Disable ADC to save power
  ADCSRA = 0;

  PCMSK0 |= (1<<PCINT10);   // pin change mask: listen to portB bit 2 (D 2)
  GIMSK  |= (1<<PCIE0);     // enable PCINT interrupt
}

boolean data = false;

void loop() {
  size_t count = 0;
  char buffer[LENGTH];

  system_sleep();

  while (ble.available() && count < LENGTH - 1) {
    delay(5);
    char c = (char) ble.read();

    if (c == '\r' || c == '\n') {
      continue;
    }

    buffer[count] = c;
    count++;
    data = true;
  }

  if (data) {
    buffer[count] = '\0';
    Serial.print("COUNT = ");
    Serial.println(count);
    Serial.println(buffer);

    count = 0;
    data = false;

    if (strcmp(buffer, "ON") == 0) {
      digitalWrite(LED_PIN, HIGH);
    }

    if (strcmp(buffer, "OFF") == 0) {
      digitalWrite(LED_PIN, LOW);
    }
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
