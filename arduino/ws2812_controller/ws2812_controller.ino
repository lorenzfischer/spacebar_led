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

// Wifi and socket settings
const char* ssid     = "dune_24";  // your (2.4GHz) WiFi SSID
const char* password = "indyshadow9";  // your WiFi password

// Set to the number of LEDs in your LED strip
#define NUM_LEDS 140
// Maximum number of packets to hold in the buffer. Don't change this.
#define BUFFER_LEN 1024
// Toggles FPS output (1 = print FPS over serial, 0 = disable output)
#define PRINT_FPS 1
#define NUM_ANIMATION_CHANNELS 3 // 1=battery, 2=network, 3=not defined yet
#define ANIMATION_CHANNEL_BATTERY 0
#define ANIMATION_CHANNEL_NETWORK 1
#define ANIMATION_SECONDS_BATTERY 2 // we want the battery animation to take 2 seconds
#define ANIMATION_SECONDS_NETWORK 10 // we want the network animation to run for at most 10 seconds
#define BATTERY_UPDATE_INTERVAL_SECONDS 10  // we only update the battery level every 10 seconds
#define SERVER_TIMEOUT_SECONDS 10  // wait for 10 seconds before trying to reconnect

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
NeoPixelAnimator animations(NUM_ANIMATION_CHANNELS); // NeoPixel animation management object, with 
long _lastPacketReceived;  // detect when we lose the connection to the server
float _batteryLevel;  // we store the current battery level in this
long _lastBatteryUpdateMillis = millis();  // store when we last updated the battery

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

  // turn off all the LEDs while we're doing this
  clearStrip();
  
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

  _lastPacketReceived = millis();  // only try to connect to the server once a second
}


#define READ_AT_42V 898.0  // this is what I read as the A0 value at 4.2V input (max voltage for 18650 cells)
#define READ_AT_3V 642.0  // this is what I read as the A0 value at 3.0V input (min voltage for 18650 cells)
/** This method probes the A0 (analog 0 pin) to find the current battery level.*/
float measureBatteryLevel() {
  int nVoltageRaw = analogRead(A0);
  float batteryLevelNorm = ((float) nVoltageRaw - READ_AT_3V) / (READ_AT_42V - READ_AT_3V);
  return max(min(batteryLevelNorm, 1.0f), 0.0f);
}


/** Updates the battery level at a fixed interval and returns the last measured battery level. */
float updateAndGetBatteryLevel() {
  if ((_batteryLevel == NULL) || 
      (_lastBatteryUpdateMillis + (BATTERY_UPDATE_INTERVAL_SECONDS * 1000) < millis())
  ){
    //Serial.println("updating battery");
    _batteryLevel = measureBatteryLevel();
    _lastBatteryUpdateMillis = millis();
  }
  return _batteryLevel;
}


void batteryStatusAnimationUpdate(const AnimationParam& param) {
  // we fake a progress that's twice the actual progress, so the battery level can be seen half of the time
  float fakeProgress = 2 * param.progress;  
  //  Serial.println(param.progress);
  
  if (param.state == AnimationState_Completed) {
      playNetworkStatusAnimation();
      clearStrip();
  } else {
    if (fakeProgress < 1.0) {
      /*
       * We want the green leds to move up and down 3 times and then at the third round, 
       * stop at the current battery level. We use a sign wave for this
       */
      int fullAnimation = 360 + (updateAndGetBatteryLevel() * 180);  // we do two full waves and then stop at the charge level
      float angle = ((fakeProgress * fullAnimation) - 90) * (M_PI / 180);  // -90 => start at the bottom of the sine wave
      float wave = sin(angle);  // this will be a value between -1 and 1, we need it between 0 and 1 ...
      float waveNorm = (wave + 1) / 2;  // .. so we normalise it here
      float pixelsOn = waveNorm * NUM_LEDS;  // ... and finally multiply with the number of LEDs we have
  
      for (int p=0; p<NUM_LEDS; p++) {  // the pixel index
        if (p < pixelsOn) {
          ledstrip.SetPixelColor(p, RgbColor(0, 255, 0));  // green
        } else {
          ledstrip.SetPixelColor(p, COLOR_OFF);  
        }
      }  
    }
  }
}


/** This measures the battery level and plays an animation on the strip to show the battery level. */
void playBatteryAnimation() {
  clearStrip(); // turn all LEDs off, so we get a clean battery reading  

  Serial.printf("Battery Level (BAT): %d%%\n", (int) round(updateAndGetBatteryLevel() * 100));
  animations.StartAnimation(
    ANIMATION_CHANNEL_BATTERY,
    ANIMATION_SECONDS_BATTERY * 1000,
    batteryStatusAnimationUpdate
  );
}


void connectToWifi() {
  Serial.println("");
  Serial.printf("Connecting to %s ...\n", ssid);
  WiFi.mode(WIFI_STA);
  //WiFi.config(ip, gateway, subnet);  // uncomment this to override DHCP
  WiFi.begin(ssid, password);  // we use DHCP for the ip configuration
}


/**Renders an animation while the network is still connecting. */
void networkStatusUpdate(const AnimationParam& param) {
    if (!animations.IsAnimationActive(ANIMATION_CHANNEL_NETWORK)) { // after we stopped the animation, stop coming in here
      return;
    }
    // Serial.println(param.progress);

    int fullAnimation = 10 * 360; // we want the white leds to move up and down 10 times, at most
    float angle = ((param.progress * fullAnimation) - 90) * (M_PI / 180);  // -90 => start at the bottom of the sine wave
    float wave = sin(angle);  // this will be a value between -1 and 1, we need it between 0 and 1 ...
    float waveNorm = (wave + 1) / 2;  // .. so we normalise it here
    float pixelsOn = waveNorm * NUM_LEDS;  // ... and finally multiply with the number of LEDs we have

    for (int p=0; p<NUM_LEDS; p++) {  // the pixel index
      if (p < pixelsOn) {
        ledstrip.SetPixelColor(p, RgbColor(255, 255, 255));  // white
      } else {
        ledstrip.SetPixelColor(p, COLOR_OFF);  
      }
    }  
    
    if (WiFi.status() == WL_CONNECTED) {
      Serial.println("");
      Serial.printf("Connected to %s\n", ssid);
      Serial.print("IP address: ");
      Serial.println(WiFi.localIP());
      animations.StopAnimation(ANIMATION_CHANNEL_NETWORK);
      animations.Pause(); // stop all animations
      clearStrip();
    }
}


/** This will play the network status animation */
void playNetworkStatusAnimation() {
  animations.Resume(); // in case the animation framework was paused
  if (!animations.IsAnimationActive(ANIMATION_CHANNEL_NETWORK)) {
    animations.StartAnimation(
      ANIMATION_CHANNEL_NETWORK,
      ANIMATION_SECONDS_NETWORK * 1000,
      networkStatusUpdate
    );
  }
}


/** checks whether we have a connection to an LED server. */
bool connectedToServer() {
  // if we haven't received anything from the server for more than 2 seconds ..
  return (millis() - _lastPacketReceived) < (SERVER_TIMEOUT_SECONDS * 1000); 
}


void setup() {
    Serial.begin(115200);

    ledstrip.Begin();//Begin output
    ledstrip.Show();//Clear the strip for use

    connectToWifi();
        
    // start listening for messages from the server
    port.begin(localPort);
    _lastPacketReceived = millis() - (SERVER_TIMEOUT_SECONDS * 1000); // start the count
    
    playBatteryAnimation();  // measure battery level and display on the stip
}


void loop() {
    updateAndGetBatteryLevel();
    
    if (!animations.IsPaused()) {
      
      animations.UpdateAnimations();
        
    } else {  // if the animator is paused, play the instructions received from the server

      // Read data over socket
      int packetSize = port.parsePacket();
      
      if (packetSize == 0) {

        /* 
         * TODO: 
         * 
         * Move all the connection code into one method which will be called in reach update. This method will 
         * then check if the wifi is up and if we are receiving server messages. if either is not the case
         * it will initiate the wifi connection, and listen for the server broadcast and if we are receiving
         * messages.
         * 
         * Only if we are, will we stop the connection animation. If we don't hear from the server withing 10 seconds
         * we start some random animation
         * 
         */
        
        if (!connectedToServer()) {
          registerWithServer();  
        } 
        
      } else {  // If packets have been received, interpret the command
  
          _lastPacketReceived = millis();
          int len = port.read(packetBuffer, BUFFER_LEN);
          packetBuffer[len] = 0;  // what is this for?
          for(int i = 0; i < len; i+=4) {
              N = packetBuffer[i];
              RgbColor pixel((uint8_t)packetBuffer[i+1], (uint8_t)packetBuffer[i+2], (uint8_t)packetBuffer[i+3]);//color
              ledstrip.SetPixelColor(N, pixel);//N is the pixel number
          } 
          #if PRINT_FPS
              fpsCounter++;
              Serial.print("/");//Monitors connection(shows jumps/jitters in packets)
          #endif
          
      }
      #if PRINT_FPS
          if (millis() - secondTimer >= 1000U) {
              secondTimer = millis();
              Serial.printf("FPS: %d BAT: %d%% \n", fpsCounter, (int) round(_batteryLevel * 100));
              fpsCounter = 0;
          }   
      #endif
      
    }
    ledstrip.Show();
}
