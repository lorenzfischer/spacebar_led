import config
import logging
import numpy
import platform
import socket
import time
import threading

_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
_sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, config.MULTICAST_TTL)

_is_python_2 = int(platform.python_version_tuple()[0]) == 2

def get_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.settimeout(0)
    try:
        s.connect(('10.255.255.255', 1))  # doesn't even have to be reachable
        IP = s.getsockname()[0]
    except Exception:
        IP = '127.0.0.1'
    finally:
        s.close()
    return IP


def start_server_broadcasting(mcast_group_ip, mcast_port):
    """Starts a thread that repeatedly broadcasts the server IP"""
    def broadcast_server_ip():
        while True:
            server_ip = get_ip()
            port = config.SERVER_PORT
            logging.debug("Broadcasting server address at {}:{}".format(server_ip, port))

            if _is_python_2:
                raise Exception("Python2 is not supported, please test the below if you want to use python2")

            ip_parts = [int(p) for p in server_ip.split(".")]  # todo: find a way to pack the port into a byte
            message = bytes(ip_parts)
            _sock.sendto(message, (mcast_group_ip, mcast_port))  # broadcast the 5 ints
            time.sleep(5)  # sleep 5 seconds

    broadcasting_thread = threading.Thread(target=broadcast_server_ip)
    broadcasting_thread.start()
    logging.info("Starting server broadcast")


def start_client_registration_server(client_list, server_port):
    def client_registration_server():
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.bind(('0.0.0.0', server_port))
            s.listen()
            while True:
                conn, (addr, _) = s.accept()
                with conn:
                    client_port = int.from_bytes(conn.recv(1024), byteorder='little')
                    client_list[addr] = client_port
                    logging.info(f"Registered client at {addr}:{client_port}")
    broadcasting_thread = threading.Thread(target=client_registration_server)
    broadcasting_thread.start()
    logging.info("Starting registration server")


def start_client_manager(client_list, server_port, mcast_group_ip, mcast_port):
    """This starts the server broadcast over the configured multicast as well as the server accepting client
    registrations"""

    start_server_broadcasting(mcast_group_ip, mcast_port)
    start_client_registration_server(client_list, server_port)