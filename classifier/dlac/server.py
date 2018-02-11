import socketserver
import struct

import numpy as np

max_history_length = 5
feature_count = 16


class ConnectionHandler(socketserver.BaseRequestHandler):
    def handle(self):
        print("{} connected".format(self.client_address[0]))
        while True:
            packet_type = self.read_int()
            if packet_type == 0:
                print("{} disconnected".format(self.client_address[0]))
                return
            elif packet_type == 1:
                total, updated, vanilla, hacking = self.update_dataset()

                print("Updated combat data. Vanilla: ", vanilla, ", Hacking:", hacking, ", Updated: ", updated,
                      ", Total: ", total)
                self.write_int(total)
                self.write_int(updated)
                self.write_int(vanilla)
                self.write_int(hacking)
                continue

    def update_dataset(self):
        try:
            x = list(np.load("x.npy").tolist())
            y = list(np.load("y.npy").tolist())
        except IOError:
            x = []
            y = []

        snippet_count = self.read_int()

        for i in range(snippet_count):
            mode = self.read_int()
            history_size = self.read_int()
            features = []

            for j in range(history_size):
                packet_type = self.read_int()
                time = self.read_int()

                if packet_type == 0:
                    ax = self.read_double()
                    ay = self.read_double()
                    az = self.read_double()
                    ayaw = self.read_double()
                    apitch = self.read_double()
                    features.append([ax, ay, az, ayaw, apitch, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, time])
                elif packet_type == 1:
                    px = self.read_double()
                    py = self.read_double()
                    pz = self.read_double()
                    features.append([0, 0, 0, 0, 0, px, py, pz, 0, 0, 0, 0, 0, 0, 0, time])
                elif packet_type == 2:
                    lyaw = self.read_double()
                    lpitch = self.read_double()
                    features.append([0, 0, 0, 0, 0, 0, 0, 0, lyaw, lpitch, 0, 0, 0, 0, 0, time])
                else:
                    plx = self.read_double()
                    ply = self.read_double()
                    plz = self.read_double()
                    plyaw = self.read_double()
                    plpitch = self.read_double()
                    features.append([0, 0, 0, 0, 0, 0, 0, 0, 0, 0, plx, ply, plz, plyaw, plpitch, time])

            x.append(features)
            y.append(mode)

        np.save("x.npy", x)
        np.save("y.npy", y)
        y = np.array(y)
        return len(x), snippet_count, int((y == 0).sum()), int((y == 1).sum())

    def read_double(self):
        data = self.read_all(8)
        value = struct.unpack('>d', data)[0]
        return value

    def read_int(self):
        data = self.read_all(4)
        return int.from_bytes(data, byteorder='big', signed=True)

    def read_all(self, size):
        data = bytes()
        while size > 0:
            b = self.request.recv(size)
            data = b''.join([data, b])
            size -= len(b)
        return data

    def write_int(self, i):
        b = i.to_bytes(4, byteorder='big')
        self.request.sendall(b)


def main():
    HOST, PORT = "localhost", 14454
    server = socketserver.TCPServer((HOST, PORT), ConnectionHandler)
    server.serve_forever()


if __name__ == "__main__":
    main()
