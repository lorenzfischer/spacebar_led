/*
* This example works for ESP8266 & ESP32 and uses the NeoPixelBus library instead of the one bundle
* Sketch written by Joey Babcock - https://joeybabcock.me/blog/, and Scott Lawson (Below) 
* Codebase created by ScottLawsonBC - https://github.com/scottlawsonbc
*/
#include <NeoPixelBus.h>

#if defined(ESP8266)
#include <ESP8266WiFi.h>
#include <WiFiUdp.h>
#elif defined(ESP32)
#include <WiFi.h>
#else
#error "This is not a ESP8266 or ESP32!"
#endif

// Set to the number of LEDs in your LED strip
#define NUM_LEDS 140

//NeoPixelBus settings
const uint8_t PixelPin = 4;  // make sure to set this to the correct pin, ignored for Esp8266(set to 3 by default for DMA)

NeoPixelBus<NeoGrbFeature, Neo800KbpsMethod> ledstrip(NUM_LEDS, PixelPin);

void setup() {
    Serial.begin(115200);
    ledstrip.Begin();//Begin output
    ledstrip.Show();//Clear the strip for use
}

void loop() {
    ledstrip.ClearTo(RgbColor(255,255,255));
    ledstrip.Show();
}
