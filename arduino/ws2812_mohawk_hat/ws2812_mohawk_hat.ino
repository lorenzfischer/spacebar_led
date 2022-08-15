/*
* This example works for ESP8266 & ESP32 and uses the NeoPixelBus library instead of the one bundle
* Sketch written by Joey Babcock - https://joeybabcock.me/blog/, and Scott Lawson (Below) 
* Codebase created by ScottLawsonBC - https://github.com/scottlawsonbc
*/
#include <NeoPixelBus.h>
#include <NeoPixelAnimator.h>
#include <math.h>  // we need this to get M_PI

#if defined(ESP8266)
#include <ESP8266WiFi.h>
#include <WiFiUdp.h>
#elif defined(ESP32)
#include <WiFi.h>
#else
#error "This is not a ESP8266 or ESP32!"
#endif

// Set to the number of LEDs in your LED strip
#define NUM_LEDS 1

//NeoPixelBus settings
const uint8_t PixelPin = 4;  // make sure to set this to the correct pin, ignored for Esp8266, which will use the RX pin (set to 3 by default for DMA)
const RgbColor COLOR_OFF = RgbColor(0,0,0);
int counter = 0;
NeoPixelBus<NeoGrbFeature, Neo800KbpsMethod> ledstrip(NUM_LEDS, PixelPin);


/** turns all LEDs on the strip off. */
void clearStrip() {
  ledstrip.ClearTo(COLOR_OFF);
  ledstrip.Show();
}


void setup() {
    Serial.begin(115200);

    ledstrip.Begin();//Begin output
    ledstrip.Show();//Clear the strip for use
}


void loop() {
    RgbColor pixel;

    switch(counter) {
      case 0:
        pixel = RgbColor(0, 0, 255);  // blue
        break;
      case 1:
        pixel = RgbColor(255, 0, 0);  // red
        break;
      default:
        pixel = RgbColor(0, 255, 0);  // green
    }
    
    ledstrip.SetPixelColor(0, pixel);//N is the pixel number
    ledstrip.Show();
    counter++;
    if (counter > 2) {
      counter = 0;
    }
    delay(1000); 
}
