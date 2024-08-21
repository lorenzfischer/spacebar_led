from threading import Thread
import socket
import struct
import time

MCAST_GRP = '224.1.1.1'
MCAST_PORT = 5555
SERVER_PORT = 1337
# LOCAL_PORT = 8888


class LEDTubeClient:

  def __init__(self):
    self.server_address = None
    self.local_port = None
    self.last_message_timestamp = 0
    self.messages_received_per_second = 0

    self.receiving_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    self.receiving_socket.bind(('0.0.0.0', 0))
    # self.receiving_socket.bind(('', LOCAL_PORT))
    self.local_port = self.receiving_socket.getsockname()[1]
    print(f"Listening locally on port {self.local_port}")

  def listenForBroadcast(self):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(('', MCAST_PORT))
    mreq = struct.pack('4sl', socket.inet_aton(MCAST_GRP), socket.INADDR_ANY)
    sock.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, mreq)

    print("Waiting for Broadcast")
    while True:
      if self.server_address == None:
        bytes = sock.recv(10240)
        self.server_address = ".".join([str(i) for i in list(bytes)])
        print(f"Received server broadcast from {self.server_address}")
      time.sleep(1)

  def register_with_server(self):
    while True:
      if self.server_address != None:
          current_time = int(time.time())
          if current_time - self.last_message_timestamp > 10:
            try:
              # connect to server
              client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
              client_socket.connect((self.server_address, SERVER_PORT))
              # send port to server
              print(f"Registering local port {self.local_port} to server at {self.server_address}")
              client_socket.send(struct.pack('<H', self.local_port))
              time.sleep(1)  # give the server some time to respond
              self.last_message_timestamp = time.time() * 1000  # plus try to listen for some data now
            except Exception as e:
              print(f"problem while registering with server: {e}")
              self.server_address = None  # retrieve the server address again
      time.sleep(1)

  def receivePacket(self):
    while True:
      try:
        # self.receiving_socket.accept()
        data = self.receiving_socket.recv(1024)
        if not data:
          print("Something went wrong with receiving data -> reset the server configuration")
          self.server_address = None
        new_second = int(time.time())
        if new_second != self.last_message_timestamp:
          print(f"FPS: {self.messages_received_per_second} ({new_second})")
          self.last_message_timestamp = int(time.time())
          self.messages_received_per_second = 0
        else:
          self.messages_received_per_second += 1
      except Exception as e:
        print("Socket not yet ready")
        print(e)
        time.sleep(1)  # sleep for 1 second

  def run(self):
    Thread(target=self.listenForBroadcast).start()
    Thread(target=self.register_with_server).start()
    Thread(target=self.receivePacket).start()

def main():
  LEDTubeClient().run()


if __name__ == '__main__':
  main()