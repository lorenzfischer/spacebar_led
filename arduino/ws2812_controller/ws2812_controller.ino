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

// Wifi and socket settings
const char* ssid     = "SSID_24";  // put in your (2.4GHz) WiFi SSID
const char* password = "WIFI_PW";  // put the password for your WiFi

// Set to the number of LEDs in your LED strip
#define NUM_LEDS 140
// Maximum number of packets to hold in the buffer. Don't change this.
#define BUFFER_LEN 1024
// Toggles FPS output (1 = print FPS over serial, 0 = disable output)
#define PRINT_FPS 1

//NeoPixelBus settings
const uint8_t PixelPin = 4;  // make sure to set this to the correct pin, ignored for Esp8266, which will use the RX pin (set to 3 by default for DMA)
const RgbColor COLOR_OFF = RgbColor(0,0,0);

unsigned int localPort = 7777;
IPAddress multicastIp(224, 1, 1, 1);  // hard coded 
unsigned int multicastPort = 5555;
unsigned int serverPort = 1337;  // hard coded for now todo: make dynamic
char packetBuffer[BUFFER_LEN];

uint8_t N = 0;

//// Network information
//IPAddress ip(192, 168, 1, 200);  // IP must match the IP in config.py
//IPAddress gateway(192, 168, 1, 1);  // Set gateway to your router's gateway
//IPAddress subnet(255, 255, 255, 0);

WiFiUDP port;
NeoPixelBus<NeoGrbFeature, Neo800KbpsMethod> ledstrip(NUM_LEDS, PixelPin);
long lastPacketReceived;  // detect when we lose the connection to the server
uint16_t batteryLevel;  // we store the current battery level in this

#if PRINT_FPS
    uint16_t fpsCounter = 0;
    uint32_t secondTimer = 0;
#endif


/** turns all LEDs on the strip off. */
void clearStrip() {
  ledstrip.ClearTo(COLOR_OFF);
  ledstrip.Show();
}


/** 
 *  This method registers this led client with the server, so it will receive led show data packets going forward.
 *  
 *  It works in two steps:
 *  
 *   1. wait for a server broadcast announcing the server IP
 *   2. send this clients IP to the server, asking for show data packets to be sent to us
 *  
 */
void registerWithServer() {
  IPAddress serverIp = NULL;
  WiFiClient client;
  WiFiUDP mcast;
  char packet[BUFFER_LEN];
  int packetSize;
  
  Serial.print("Listening for server broadcast ");
  mcast.beginMulticast(WiFi.localIP(), multicastIp, multicastPort);
  for (int i=0; i<20 && !serverIp; i++) {
    packetSize = mcast.parsePacket();
    if (packetSize > 0) {
      int len = mcast.read(packet, BUFFER_LEN);
      packet[len] = '\0';  // 0-terminate the string, so we know where to stop reading
      serverIp = IPAddress((uint8_t)packet[0], (uint8_t)packet[1], (uint8_t)packet[2], (uint8_t)packet[3]);
    } else {
      delay(500);  
      Serial.print(".");
    }
  }
  mcast.stop();

  if (!serverIp) {
    Serial.print(" no server found!");
  } else {
    Serial.print(" server found at ");
    Serial.println(serverIp);
  
    Serial.print("Registering with server at ");
    Serial.print(serverIp);
    Serial.print(":");
    Serial.print(serverPort);
    Serial.print(" ... ");
    if (!client.connect(serverIp, serverPort)) {
      Serial.println(" connection failed");
      delay(500);
    } else {  // connection to server established
      client.write((uint8_t*) &localPort,sizeof(localPort)); // the server will see our ip, so we only have to tell it the port we're listing on
      client.stop();
      Serial.println(" client registered!");
    }
  }
}


#define READ_AT_42V 898.0  // this is what I read as the A0 value at 4.2V input (max voltage for 18650 cells)
#define READ_AT_3V 642.0  // this is what I read as the A0 value at 3.0V input (min voltage for 18650 cells)
//
/** This method probes the A0 (analog 0 pin) to find the current battery level.*/
int16_t measureBatteryLevel() {
  int nVoltageRaw = analogRead(A0);
  float batteryLevel = ((float) nVoltageRaw - READ_AT_3V) / (READ_AT_42V - READ_AT_3V) * 100;
  return (int16_t) batteryLevel;
}


/** This measures the battery level and plays an animation on the strip to show the battery level. */
void playBatteryAnimation() {
  float batteryLevel;
  
  clearStrip(); // turn all LEDs off, so we get a clean battery reading  
  batteryLevel = 1.0 * measureBatteryLevel() / 100;  // convert the 0-100 value into a float

  // todo: use some cool animation to do this.
  for (int p=0; p<NUM_LEDS; p++) {  // the pixel index
    float pixelPercentage = (float)p / NUM_LEDS;
    RgbColor color = COLOR_OFF;
    if (pixelPercentage < batteryLevel) {
      color = RgbColor(0, 255, 0); // green
    }
    ledstrip.SetPixelColor(p, color);
  }
  
  ledstrip.Show();
  delay(1*1000);

  // clear to empty
  clearStrip();
}



void setup() {
    Serial.begin(115200);

    ledstrip.Begin();//Begin output
    ledstrip.Show();//Clear the strip for use

    // measure battery level and display on the stip
    playBatteryAnimation();
    
    // connect to wifi
    WiFi.mode(WIFI_STA);
    //WiFi.config(ip, gateway, subnet);  // uncomment this to override DHCP
    WiFi.begin(ssid, password);  // we use DHCP for the ip configuration
    Serial.println("");
    // Connect to wifi and print the IP address over serial
    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print(".");
    }
    
    Serial.println("");
    Serial.print("Connected to ");
    Serial.println(ssid);
    Serial.print("IP address: ");
    Serial.println(WiFi.localIP());

    // start listening for messages from the server
    port.begin(localPort);
//    port.beginMulticast(ip, IPAddress(224, 1, 1, 1), 5555);

    lastPacketReceived = millis(); // start the count
}


void loop() {
    // Read data over socket
    int packetSize = port.parsePacket();
    
    if (packetSize == 0) {
    
      if ((millis() - lastPacketReceived) > 1000) {  // if we haven't received anything from the server for more than 1 second ..
        registerWithServer();  
        lastPacketReceived = millis();  // only try to connect to the server once a second
      } 
      
    } else {  // If packets have been received, interpret the command

        lastPacketReceived = millis();
        int len = port.read(packetBuffer, BUFFER_LEN);
        packetBuffer[len] = 0;  // what is this for?
        for(int i = 0; i < len; i+=4) {
            N = packetBuffer[i];
            RgbColor pixel((uint8_t)packetBuffer[i+1], (uint8_t)packetBuffer[i+2], (uint8_t)packetBuffer[i+3]);//color
            ledstrip.SetPixelColor(N, pixel);//N is the pixel number
        } 
        ledstrip.Show();
        #if PRINT_FPS
            fpsCounter++;
            Serial.print("/");//Monitors connection(shows jumps/jitters in packets)
        #endif
        
    }
    #if PRINT_FPS
        if (millis() - secondTimer >= 1000U) {
            secondTimer = millis();
            Serial.printf("FPS: %d BAT: %d % \n", fpsCounter, 100);
            fpsCounter = 0;
        }   
    #endif
}
